"""
Command transcription bridge — port 8765.

Connection handshake:
  Server → Phone: [1B] 0x01 (Whisper healthy) or 0x00 (not ready)
  Phone  → Server: [4B LE token_len][token bytes]   ← must match security.TOKEN
  Server → Phone: [1B] 0x01 (accepted) or closes connection

Per-request (persistent connection):
  Phone  → Server:
    [4B LE] encrypted payload length
    [?B]    IV(12) + AES-256-GCM ciphertext + tag(16)
              └─ plaintext = [4B LE sample_count][float32 * sample_count]

  Server → Phone:
    [4B LE] encrypted response length
    [?B]    IV(12) + AES-256-GCM ciphertext + tag(16)
              └─ plaintext = [4B LE text_len][utf-8 text bytes]
"""
import asyncio, struct, io, httpx, wave, json, time, sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
# Force UTF-8 stdout/stderr so non-ASCII (arrows, accented chars, non-English
# transcriptions) never crash on Windows' default cp1252 console encoding.
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass
import numpy as np
import security

WHISPER_HTTP = "http://127.0.0.1:8081/inference"
HOST, PORT   = "0.0.0.0", 8765


async def _whisper_ready() -> bool:
    try:
        async with httpx.AsyncClient() as c:
            r = await c.get("http://127.0.0.1:8081/docs", timeout=2)
            return r.status_code < 500
    except Exception:
        return False


async def handle(reader: asyncio.StreamReader, writer: asyncio.StreamWriter):
    peer = writer.get_extra_info("peername")
    print(f"[bridge] Phone connected: {peer}")
    try:
        # ── Handshake: tell phone whether Whisper is ready ─────────────────────
        ready = await _whisper_ready()
        writer.write(bytes([0x01 if ready else 0x00]))
        await writer.drain()
        if not ready:
            print(f"[bridge] Whisper not ready - closing {peer}")
            return

        # ── Unified request loop ───────────────────────────────────────────────
        # Each 4-byte header is EITHER a token length (<=256) or an encrypted
        # audio-payload length (always many KB). We accept audio with or without
        # a preceding token on a given connection: AES-256-GCM already
        # authenticates every payload, so a valid ciphertext proves the sender
        # holds the shared key. This tolerates the phone reusing a socket the
        # server already closed (Android keeps reporting it "connected"), which
        # otherwise produced spurious "token too long" rejections.
        while True:
            hdr = await asyncio.wait_for(reader.readexactly(4), timeout=300)
            n   = struct.unpack("<I", hdr)[0]

            # Small value => token handshake (validate + ack, then continue)
            if n <= 256:
                token = (await asyncio.wait_for(
                    reader.readexactly(n), timeout=10)).decode("utf-8", "replace")
                if not security.validate_token(token):
                    print(f"[bridge] Rejected {peer}: wrong token")
                    return
                writer.write(bytes([0x01]))
                await writer.drain()
                print(f"[bridge] Authenticated {peer}")
                continue

            # Otherwise n is an audio request. It's EITHER:
            #   secure:  n = encrypted-blob length -> n bytes that AES-decrypt to
            #            [4B sample_count][float32...]
            #   plain :  n = sample_count          -> n*4 bytes of raw float32
            # The phone uses plain mode when it has no token/key configured. We
            # detect which by trying to decrypt the first n bytes; if that fails,
            # we fall back to reading the remaining float bytes as plain audio.
            first = await asyncio.wait_for(reader.readexactly(n), timeout=30)
            secure = True
            try:
                plain        = security.decrypt(first)
                sample_count = struct.unpack("<I", plain[:4])[0]
                audio_bytes  = plain[4: 4 + sample_count * 4]
            except Exception:
                # Plain mode: n was the sample count; we've read n of the n*4
                # float bytes, so pull the remaining n*3 and treat as raw audio.
                secure = False
                rest = await asyncio.wait_for(reader.readexactly(n * 3), timeout=30)
                audio_bytes = first + rest

            # Float32 → WAV
            buf = io.BytesIO()
            with wave.open(buf, "wb") as w:
                w.setnchannels(1); w.setsampwidth(2); w.setframerate(16000)
                samples = (np.frombuffer(audio_bytes, dtype=np.float32) * 32767).astype(np.int16)
                w.writeframes(samples.tobytes())

            t0 = time.time()
            async with httpx.AsyncClient() as client:
                resp = await client.post(
                    WHISPER_HTTP,
                    files={"file": ("audio.wav", buf.getvalue(), "audio/wav")},
                    params={"response_format": "text"},
                    timeout=30,
                )
            t1 = time.time()
            raw = resp.text.strip()
            try:    text = json.loads(raw).get("text", raw).strip()
            except: text = raw
            print(f"[bridge]  {t1-t0:.2f}s -> \"{text}\"")

            text_bytes = text.encode("utf-8")
            if secure:
                payload  = struct.pack("<I", len(text_bytes)) + text_bytes
                enc_resp = security.encrypt(payload)
                writer.write(struct.pack("<I", len(enc_resp)) + enc_resp)
            else:
                # Plain mode response: [4B text_len][utf-8 text]
                writer.write(struct.pack("<I", len(text_bytes)) + text_bytes)
            await writer.drain()

    except (asyncio.IncompleteReadError, ConnectionResetError, asyncio.TimeoutError):
        pass
    except Exception as e:
        print(f"[bridge] Error from {peer}: {e}")
    finally:
        print(f"[bridge] Phone disconnected: {peer}")
        writer.close()


async def main():
    print(f"[bridge] Secure command bridge on {HOST}:{PORT}")
    server = await asyncio.start_server(handle, HOST, PORT)
    async with server:
        await server.serve_forever()


asyncio.run(main())
