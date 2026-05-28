package com.voicecommand.app.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlin.math.abs
import kotlin.math.max

class AudioRecorder {

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_SIZE = 4096
        private const val MAX_RECORDING_MS = 30_000L
        private const val SILENCE_THRESHOLD = 0.005f
        private const val VAD_SILENCE_MS = 400L
    }

    private var audioRecord: AudioRecord? = null
    @Volatile
    private var isRecording = false
    private var recordingDeferred = CompletableDeferred<FloatArray>()
    private val maxSamples = (SAMPLE_RATE * (MAX_RECORDING_MS / 1000)).toInt()
    private val samplesBuffer = ShortArray(maxSamples)
    private var samplesCount = 0
    private var recordingStartMs = 0L

    private val minBufferSize: Int by lazy {
        max(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT),
            CHUNK_SIZE
        )
    }

    fun start(): Boolean {
        if (isRecording) return false

        try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                minBufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return false
            }

            samplesCount = 0
            recordingDeferred = CompletableDeferred()
            isRecording = true
            recordingStartMs = System.currentTimeMillis()
            audioRecord = record
            record.startRecording()

            val buffer = ShortArray(CHUNK_SIZE)
            while (isRecording) {
                val elapsed = System.currentTimeMillis() - recordingStartMs
                if (elapsed > MAX_RECORDING_MS) break

                val read = record.read(buffer, 0, CHUNK_SIZE)
                if (read > 0) {
                    val remaining = maxSamples - samplesCount
                    val toCopy = minOf(read, remaining)
                    System.arraycopy(buffer, 0, samplesBuffer, samplesCount, toCopy)
                    samplesCount += toCopy
                    if (samplesCount >= maxSamples) break
                }
            }

            isRecording = false

            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            } catch (_: IllegalStateException) { }
            record.release()
            audioRecord = null

            val resultData = if (samplesCount > 0) {
                Log.d("AudioRecorder", "Recorded $samplesCount samples")
                val floatArray = FloatArray(samplesCount) { i ->
                    samplesBuffer[i].toFloat() / 32768.0f
                }
                val trimmed = trimSilence(floatArray)
                Log.d("AudioRecorder", "Trimmed from ${floatArray.size} to ${trimmed.size} samples")
                trimmed
            } else {
                Log.w("AudioRecorder", "No samples recorded")
                FloatArray(0)
            }
            recordingDeferred.complete(resultData)

            return true
        } catch (e: SecurityException) {
            isRecording = false
            recordingDeferred.complete(FloatArray(0))
            return false
        }
    }

    fun startWithVad(silenceMs: Long = VAD_SILENCE_MS): Boolean {
        if (isRecording) return false

        try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                minBufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return false
            }

            samplesCount = 0
            recordingDeferred = CompletableDeferred()
            isRecording = true
            recordingStartMs = System.currentTimeMillis()
            audioRecord = record
            record.startRecording()

            val buffer = ShortArray(CHUNK_SIZE)
            var lastSpeechMs = System.currentTimeMillis()
            var hasSpeech = false

            while (isRecording) {
                val elapsed = System.currentTimeMillis() - recordingStartMs
                if (elapsed > MAX_RECORDING_MS) break

                val read = record.read(buffer, 0, CHUNK_SIZE)
                if (read > 0) {
                    val remaining = maxSamples - samplesCount
                    val toCopy = minOf(read, remaining)
                    System.arraycopy(buffer, 0, samplesBuffer, samplesCount, toCopy)
                    samplesCount += toCopy
                    if (samplesCount >= maxSamples) break

                    var energy = 0f
                    for (i in 0 until read) {
                        energy += kotlin.math.abs(buffer[i].toFloat())
                    }
                    energy /= (read * 32768f)

                    if (energy > SILENCE_THRESHOLD) {
                        lastSpeechMs = System.currentTimeMillis()
                        hasSpeech = true
                    }

                    if (hasSpeech && (System.currentTimeMillis() - lastSpeechMs) > silenceMs) {
                        Log.d("AudioRecorder", "VAD: silence for ${silenceMs}ms, stopping")
                        break
                    }
                }
            }

            isRecording = false

            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            } catch (_: IllegalStateException) { }
            record.release()
            audioRecord = null

            val resultData = if (samplesCount > 0) {
                Log.d("AudioRecorder", "Recorded $samplesCount samples (VAD)")
                val floatArray = FloatArray(samplesCount) { i ->
                    samplesBuffer[i].toFloat() / 32768.0f
                }
                val trimmed = trimSilence(floatArray)
                Log.d("AudioRecorder", "Trimmed from ${floatArray.size} to ${trimmed.size} samples (VAD)")
                trimmed
            } else {
                Log.w("AudioRecorder", "No samples recorded (VAD)")
                FloatArray(0)
            }
            recordingDeferred.complete(resultData)

            return true
        } catch (e: SecurityException) {
            isRecording = false
            recordingDeferred.complete(FloatArray(0))
            return false
        }
    }

    fun getCurrentSnapshot(): FloatArray {
        val count = samplesCount
        if (count == 0) return FloatArray(0)
        return FloatArray(count) { i ->
            samplesBuffer[i].toFloat() / 32768.0f
        }
    }

    fun getNewSamplesSince(lastIndex: Int): FloatArray {
        val count = samplesCount
        if (count <= lastIndex) return FloatArray(0)
        return FloatArray(count - lastIndex) { i ->
            samplesBuffer[lastIndex + i].toFloat() / 32768.0f
        }
    }

    suspend fun stop(): FloatArray {
        isRecording = false
        return recordingDeferred.await()
    }

    fun isRecording(): Boolean = isRecording

    private fun trimSilence(samples: FloatArray): FloatArray {
        val energyThreshold = SILENCE_THRESHOLD
        var start = 0
        while (start < samples.size - 1) {
            var sum = 0f
            val end = minOf(start + SAMPLE_RATE / 100, samples.size)
            for (i in start until end) {
                sum += abs(samples[i])
            }
            val avg = sum / (end - start)
            if (avg > energyThreshold) break
            start = end
        }

        if (start >= samples.size - 1) {
            Log.w("AudioRecorder", "Entire buffer is silence (start=$start)")
            return samples
        }

        var end = samples.size - 1
        while (end > start) {
            var sum = 0f
            val chunkStart = maxOf(0, end - SAMPLE_RATE / 100)
            for (i in chunkStart until end) {
                sum += abs(samples[i])
            }
            val avg = sum / (end - chunkStart)
            if (avg > energyThreshold) {
                end = end.coerceAtMost(samples.size - 1)
                break
            }
            end = chunkStart
        }

        Log.d("AudioRecorder", "Trim points: start=$start, end=$end, original_size=${samples.size}")
        return samples.copyOfRange(start, end + 1)
    }
}
