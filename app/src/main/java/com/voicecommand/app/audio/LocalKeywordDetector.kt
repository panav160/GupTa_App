package com.voicecommand.app.audio

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class LocalKeywordDetector(private val context: Context) {

    companion object {
        private const val TAG = "LocalKeywordDetector"
        private const val MODEL_DIR = "whisper_tiny"
        private const val SAMPLE_RATE = 16000

        private val WAKE_WORD_VARIANTS = listOf(
            "hi gupta", "hi guptah", "hi guptaa",
            "high gupta", "high guptah",
            "hai gupta", "hai guptah",
            "hie gupta",
            "he gupta",
            "aye gupta",
            "i gupta",
            "ai gupta",
            "gupta", "guptah", "guptaa",
            "gup ta", "gup tah",
            "gufta"
        )
    }

    private var helper: SherpaHelper? = null
    private var isLoaded = false

    fun load(): Boolean {
        try {
            val modelDir = File(context.filesDir, MODEL_DIR)
            if (!modelDir.exists()) {
                modelDir.mkdirs()
                extractModel(modelDir)
            }

            val encoder = File(modelDir, "encoder_model.onnx")
            val decoder = File(modelDir, "decoder_model.onnx")
            val tokens = File(modelDir, "tokens.txt")

            if (!encoder.exists() || !decoder.exists()) {
                Log.e(TAG, "Model files missing, re-extracting")
                extractModel(modelDir)
            }

            helper = SherpaHelper(
                encoder.absolutePath,
                decoder.absolutePath,
                tokens.absolutePath,
                2
            )

            isLoaded = true
            Log.d(TAG, "Whisper tiny model loaded successfully")

            testModel()

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Moonshine model: ${e.message}", e)
            isLoaded = false
            return false
        }
    }

    fun checkWakeWord(audioSamples: FloatArray): String? {
        if (!isLoaded || helper == null) return null

        try {
            val text = helper!!.checkWakeWord(audioSamples)
                .trim().lowercase()

            Log.d(TAG, "Local transcription: \"$text\"")

            for (variant in WAKE_WORD_VARIANTS) {
                if (text.contains(variant)) {
                    Log.d(TAG, "Wake word detected locally: \"$text\"")
                    return text
                }
            }
            return null
        } catch (e: Exception) {
            Log.w(TAG, "Local inference failed: ${e.message}")
            return null
        }
    }

    fun transcribe(audioSamples: FloatArray): String? {
        if (!isLoaded || helper == null) return null
        try {
            return helper!!.checkWakeWord(audioSamples).trim().lowercase()
        } catch (e: Exception) {
            Log.w(TAG, "Local transcription failed: ${e.message}")
            return null
        }
    }

    fun release() {
        try {
            helper?.release()
        } catch (_: Exception) {}
        helper = null
        isLoaded = false
    }

    private var testWavFloats: FloatArray? = null

    private fun testModel() {
        try {
            val stream = context.assets.open("$MODEL_DIR/test_wavs/0.wav")
            val data = stream.use { it.readBytes() }
            if (data.size < 44) return
            val numSamples = (data.size - 44) / 2
            val floats = FloatArray(numSamples)
            for (i in 0 until numSamples) {
                val lo = data[44 + i * 2].toInt() and 0xFF
                val hi = data[44 + i * 2 + 1].toInt() and 0xFF
                val s = (hi shl 8) or lo
                floats[i] = s.toShort().toFloat() / 32768.0f
            }
            testWavFloats = floats
            val text = helper?.checkWakeWord(floats) ?: ""
            Log.d(TAG, "TEST WAV: \"$text\"")
            Log.d(TAG, "Test WAV stats: samples=${floats.size}, max=${floats.maxOrNull()}, min=${floats.minOrNull()}")
        } catch (e: Exception) {
            Log.w(TAG, "TEST WAV failed: ${e.message}")
        }
    }

    fun checkTestWav(): String? {
        val floats = testWavFloats ?: return "no test wav"
        return try {
            val text = helper?.checkWakeWord(floats) ?: "null"
            text.trim().lowercase()
        } catch (e: Exception) {
            "error: ${e.message}"
        }
    }

    fun checkTestWavTruncated(samples: Int): String? {
        val floats = testWavFloats ?: return "no test wav"
        val truncated = if (samples >= floats.size) floats else floats.copyOf(samples)
        return try {
            val text = helper?.checkWakeWord(truncated) ?: "null"
            text.trim().lowercase()
        } catch (e: Exception) {
            "error: ${e.message}"
        }
    }

    private fun extractModel(modelDir: File) {
        val assets = context.assets.list(MODEL_DIR) ?: return
        for (name in assets) {
            if (name == "test_wavs" || name == "LICENSE" || name == "README.md") continue
            val destFile = File(modelDir, name)
            try {
                context.assets.open("$MODEL_DIR/$name").use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to extract $name: ${e.message}")
            }
        }
        Log.d(TAG, "Model files extracted to ${modelDir.absolutePath}")
    }
}