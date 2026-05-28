import asyncio
import json
import time
import numpy as np
from faster_whisper import WhisperModel
from fastapi import FastAPI, UploadFile, File, Query
from fastapi.responses import JSONResponse
import uvicorn

MODEL_SIZE = "v3-turbo"
DEVICE = "cpu"
COMPUTE_TYPE = "int8"
PORT = 8081

print(f"Loading {MODEL_SIZE}...")
model = WhisperModel(MODEL_SIZE, device=DEVICE, compute_type=COMPUTE_TYPE)
print(f"Model loaded, server ready on port {PORT}")

app = FastAPI()

@app.post("/inference")
async def inference(
    file: UploadFile = File(...),
    response_format: str = Query("text")
):
    t0 = time.time()
    wav_bytes = await file.read()
    import io
    import wave
    with wave.open(io.BytesIO(wav_bytes), "rb") as w:
        frames = w.readframes(w.getnframes())
        sample_rate = w.getframerate()
        audio = np.frombuffer(frames, dtype=np.int16).astype(np.float32) / 32768.0

    segments, _ = model.transcribe(
        audio,
        beam_size=1,
        best_of=1,
        language="en",
        vad_filter=False,
    )
    text = "".join(seg.text for seg in segments).strip()
    t1 = time.time()
    print(f"  {t1-t0:.2f}s -> \"{text}\" ({len(wav_bytes)} bytes)")

    if response_format == "json":
        return JSONResponse({"text": text})
    return text

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=PORT)
