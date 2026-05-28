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
import asyncio, struct, io, httpx, wave, json, time
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
            print(f"[bridge] Whisper not ready — closing {peer}")
            return

        # ── Validate token (sent once per connection) ──────────────────────────
        tok_len_b = await asyncio.wait_for(reader.readexactly(4), timeout=15)
        tok_len   = struct.unpack("<I", tok_len_b)[0]
        if tok_len > 256:
            print(f"[bridge] Rejected {peer}: token too long")
            return
        token = (await asyncio.wait_for(reader.readexactly(tok_len), timeout=10)).decode()

        if not security.validate_token(token):
            print(f"[bridge] Rejected {peer}: wrong token")
            return

        # Token OK — confirm to phone
        writer.write(bytes([0x01]))
        await writer.drain()
        print(f"[bridge] Authenticated {peer}")

        # ── Request loop ───────────────────────────────────────────────────────
        while True:
            enc_len_b = await asyncio.wait_for(reader.readexactly(4), timeout=120)
            enc_len   = struct.unpack("<I", enc_len_b)[0]
            enc_data  = await asyncio.wait_for(reader.readexactly(enc_len), timeout=30)

            plain        = security.decrypt(enc_data)
            sample_count = struct.unpack("<I", plain[:4])[0]
            audio_bytes  = plain[4: 4 + sample_count * 4]

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
            print(f"[bridge]  {t1-t0:.2f}s → \"{text}\"")

            text_bytes = text.encode("utf-8")
            payload    = struct.pack("<I", len(text_bytes)) + text_bytes
            enc_resp   = security.encrypt(payload)
            writer.write(struct.pack("<I", len(enc_resp)) + enc_resp)
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
