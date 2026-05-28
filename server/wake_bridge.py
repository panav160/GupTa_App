"""
Wake-word bridge — port 8766.

Protocol (per request):
  Phone → Server:
    [4B LE] token length
    [N B]   token (must match security.TOKEN)
    [4B LE] encrypted payload length
    [?B]    IV(12) + AES-256-GCM ciphertext + tag(16)
              └─ plaintext = [4B LE sample_count][float32 * sample_count]

  Server → Phone:
    [4B LE] encrypted response length
    [?B]    IV(12) + AES-256-GCM ciphertext + tag(16)
              └─ plaintext = [4B LE text_len][utf-8 text bytes]
"""
import asyncio, struct, io, httpx, wave, json, sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
# Force UTF-8 stdout/stderr so non-ASCII never crashes on Windows cp1252 console.
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass
import numpy as np
import security

WHISPER_HTTP = "http://127.0.0.1:8081/inference"
HOST, PORT   = "0.0.0.0", 8766


async def handle(reader: asyncio.StreamReader, writer: asyncio.StreamWriter):
    peer = writer.get_extra_info("peername")
    try:
        while True:
            # ── 1. Read & validate token ───────────────────────────────────────
            tok_len_b = await asyncio.wait_for(reader.readexactly(4), timeout=120)
            tok_len   = struct.unpack("<I", tok_len_b)[0]
            if tok_len > 256:          # sanity check
                break
            token = (await asyncio.wait_for(reader.readexactly(tok_len), timeout=10)).decode()

            if not security.validate_token(token):
                print(f"[wake] Rejected {peer}: wrong token")
                break

            # ── 2. Read & decrypt audio payload ───────────────────────────────
            enc_len_b = await asyncio.wait_for(reader.readexactly(4), timeout=120)
            enc_len   = struct.unpack("<I", enc_len_b)[0]
            enc_data  = await asyncio.wait_for(reader.readexactly(enc_len), timeout=30)

            plain        = security.decrypt(enc_data)
            sample_count = struct.unpack("<I", plain[:4])[0]
            audio_bytes  = plain[4: 4 + sample_count * 4]

            # ── 3. Convert float32 → WAV ───────────────────────────────────────
            buf = io.BytesIO()
            with wave.open(buf, "wb") as w:
                w.setnchannels(1); w.setsampwidth(2); w.setframerate(16000)
                samples = (np.frombuffer(audio_bytes, dtype=np.float32) * 32767).astype(np.int16)
                w.writeframes(samples.tobytes())

            # ── 4. Transcribe via local Whisper HTTP ───────────────────────────
            async with httpx.AsyncClient() as client:
                resp = await client.post(
                    WHISPER_HTTP,
                    files={"file": ("audio.wav", buf.getvalue(), "audio/wav")},
                    params={"response_format": "text"},
                    timeout=30,
                )
            raw = resp.text.strip()
            try:    text = json.loads(raw).get("text", raw).strip()
            except: text = raw

            # ── 5. Encrypt & send response ─────────────────────────────────────
            text_bytes = text.encode("utf-8")
            payload    = struct.pack("<I", len(text_bytes)) + text_bytes
            enc_resp   = security.encrypt(payload)
            writer.write(struct.pack("<I", len(enc_resp)) + enc_resp)
            await writer.drain()

    except Exception:
        pass
    finally:
        writer.close()


async def main():
    print(f"[wake] Secure wake-word bridge on {HOST}:{PORT}")
    server = await asyncio.start_server(handle, HOST, PORT)
    async with server:
        await server.serve_forever()


asyncio.run(main())
