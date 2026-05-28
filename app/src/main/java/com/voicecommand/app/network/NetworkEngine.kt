package com.voicecommand.app.network

import android.util.Log
import com.voicecommand.app.audio.SecureChannel
import com.voicecommand.app.audio.WakeWordService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NetworkEngine(
    private val host: String,
    private val port: Int,
    private val timeoutMs: Int = 15000
) : AutoCloseable {

    private var socket: Socket? = null
    private var dos: DataOutputStream? = null
    private var dis: DataInputStream? = null

    // ── Connection management ──────────────────────────────────────────────────

    private fun isConnected(): Boolean =
        socket?.let { it.isConnected && !it.isClosed } ?: false

    private fun openConnection() {
        closeQuietly()
        val sock = Socket()
        sock.connect(InetSocketAddress(host, port), 5000)
        sock.soTimeout = timeoutMs
        sock.setKeepAlive(true)
        socket = sock
        dos = DataOutputStream(sock.getOutputStream())
        dis = DataInputStream(sock.getInputStream())

        // Handshake: bridge sends 0x01 if Whisper is up, 0x00 if not
        val status = dis!!.read()
        if (status != 1) {
            closeQuietly()
            throw IOException("Laptop bridge is up but Whisper model is not running")
        }

        // Send token for authentication (once per connection)
        val token  = WakeWordService.serverToken
        val aesKey = WakeWordService.serverAesKey
        if (SecureChannel.isConfigured(token, aesKey)) {
            val tokenBytes = token.toByteArray(Charsets.UTF_8)
            val buf = ByteBuffer.allocate(4 + tokenBytes.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(tokenBytes.size)
            buf.put(tokenBytes)
            dos!!.write(buf.array())
            dos!!.flush()

            // Server replies 0x01 if token accepted, otherwise closes
            val ack = dis!!.read()
            if (ack != 1) {
                closeQuietly()
                throw IOException("Server rejected auth token — re-scan the QR code")
            }
        }

        Log.i("NetworkEngine", "Connected to $host:$port — Whisper healthy")
    }

    private fun closeQuietly() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        dos    = null
        dis    = null
    }

    suspend fun checkConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isConnected()) openConnection()
            true
        } catch (e: Exception) {
            Log.w("NetworkEngine", "checkConnection failed: ${e.message}")
            false
        }
    }

    // ── Transcription ──────────────────────────────────────────────────────────

    suspend fun transcribe(audioData: FloatArray): String = withContext(Dispatchers.IO) {
        repeat(2) { attempt ->
            try {
                if (!isConnected()) openConnection()

                val out    = dos!!
                val inp    = dis!!
                val token  = WakeWordService.serverToken
                val aesKey = WakeWordService.serverAesKey

                // Build raw audio payload: [4B sample count][float32 array]
                val plainBuf = ByteBuffer.allocate(4 + audioData.size * 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(audioData.size)
                for (s in audioData) plainBuf.putFloat(s)
                val plainBytes = plainBuf.array()

                if (SecureChannel.isConfigured(token, aesKey)) {
                    // Secure: [4B enc len][IV + ciphertext + tag]
                    val encrypted = SecureChannel.encrypt(plainBytes, aesKey)
                    val sendBuf = ByteBuffer.allocate(4 + encrypted.size)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(encrypted.size)
                    sendBuf.put(encrypted)
                    out.write(sendBuf.array())
                    out.flush()

                    // Receive encrypted response: [4B enc len][IV + ciphertext + tag]
                    val encLenBytes = ByteArray(4)
                    inp.readFully(encLenBytes)
                    val encLen = ByteBuffer.wrap(encLenBytes).order(ByteOrder.LITTLE_ENDIAN).int
                    val encResp = ByteArray(encLen)
                    inp.readFully(encResp)

                    val decrypted = SecureChannel.decrypt(encResp, aesKey)
                    val textLen = ByteBuffer.wrap(decrypted, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    return@withContext String(decrypted, 4, textLen, Charsets.UTF_8)

                } else {
                    // Legacy plain mode (no token/key configured)
                    out.write(plainBytes)
                    out.flush()

                    val lenBytes = ByteArray(4)
                    inp.readFully(lenBytes)
                    val textLen = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).int
                    val textBytes = ByteArray(textLen)
                    inp.readFully(textBytes)
                    return@withContext String(textBytes, Charsets.UTF_8)
                }

            } catch (e: Exception) {
                Log.w("NetworkEngine", "transcribe attempt ${attempt + 1} failed: ${e.message}")
                closeQuietly()
                if (attempt == 1) throw e
            }
        }
        throw IllegalStateException("unreachable")
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun close() = closeQuietly()
}
