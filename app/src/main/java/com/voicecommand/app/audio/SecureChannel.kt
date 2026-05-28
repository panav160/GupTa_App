package com.voicecommand.app.audio

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

/**
 * AES-256-GCM encryption helper for the laptop server connection.
 *
 * Packet layout sent to server:
 *   [4 bytes LE: token length]
 *   [token bytes]
 *   [4 bytes LE: encrypted payload length]
 *   [12 bytes IV][ciphertext][16 bytes GCM auth tag]   ← all inside encrypted block
 *
 * Packet layout received from server:
 *   [4 bytes LE: encrypted payload length]
 *   [12 bytes IV][ciphertext][16 bytes GCM auth tag]
 */
object SecureChannel {

    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12
    private val random = SecureRandom()

    /** Decode a lowercase hex string to bytes. */
    fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(hex.length / 2) { i ->
            ((hex[i * 2].digitToInt(16) shl 4) + hex[i * 2 + 1].digitToInt(16)).toByte()
        }
    }

    /**
     * Encrypt [plaintext] with AES-256-GCM using [keyHex] (64 hex chars = 32 bytes).
     * Returns: [12 bytes IV] + [ciphertext + 16 byte tag].
     */
    fun encrypt(plaintext: ByteArray, keyHex: String): ByteArray {
        val key = SecretKeySpec(hexToBytes(keyHex), "AES")
        val iv = ByteArray(GCM_IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext          // IV prepended, tag is appended by JCE automatically
    }

    /**
     * Decrypt data produced by [encrypt].
     * Input: [12 bytes IV] + [ciphertext + 16 byte tag]
     * Throws if the auth tag doesn't match (tampered or wrong key).
     */
    fun decrypt(encrypted: ByteArray, keyHex: String): ByteArray {
        val iv = encrypted.copyOfRange(0, GCM_IV_BYTES)
        val ciphertext = encrypted.copyOfRange(GCM_IV_BYTES, encrypted.size)
        val key = SecretKeySpec(hexToBytes(keyHex), "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    /** Returns true if both token and key look like valid hex strings of the right length. */
    fun isConfigured(token: String, aesKeyHex: String): Boolean =
        token.length == 32 && aesKeyHex.length == 64 &&
        token.all { it.isLetterOrDigit() } && aesKeyHex.all { it.isLetterOrDigit() }
}
