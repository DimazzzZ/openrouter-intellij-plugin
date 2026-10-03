package org.zhavoronkov.openrouter.utils

import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Simple encryption utility for storing sensitive data like API keys
 */
object EncryptionUtil {

    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES"
    private const val AES_KEY_SIZE_BYTES = 16
    private const val MAX_API_KEY_LENGTH = 100

    // Generate a key based on system properties for basic obfuscation
    private fun getKey(): SecretKeySpec {
        val keyString = "${System.getProperty("user.name", "default")}_openrouter_key"
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(keyString.toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(keyBytes.sliceArray(0 until AES_KEY_SIZE_BYTES), ALGORITHM)
    }

    /**
     * Encrypt a string value
     */
    fun encrypt(plainText: String): String {
        if (plainText.isBlank()) return plainText

        return plainTextIfEncryptionFails(plainText) {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getKey())
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            Base64.getEncoder().encodeToString(encryptedBytes)
        }
    }

    /** [encrypt]ed, or [plainText] itself when the JVM's cipher refuses. */
    @ExcludeFromCoverage("JCE refusing AES with a 128-bit key it was handed, which no supported JVM does")
    private fun plainTextIfEncryptionFails(plainText: String, encrypt: () -> String): String = try {
        encrypt()
    } catch (e: GeneralSecurityException) {
        PluginLogger.Service.error("Encryption failed, returning plain text", e)
        plainText
    }

    /**
     * Decrypt a string value
     */
    fun decrypt(encryptedText: String): String {
        if (encryptedText.isBlank()) return encryptedText

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getKey())
            val encryptedBytes = Base64.getDecoder().decode(encryptedText)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            // Bad padding or block size: text this key did not encrypt, which is a key stored in clear
            PluginLogger.Service.debug("Not decryptable with this key, assuming plain text", e)
            encryptedText
        } catch (e: IllegalArgumentException) {
            PluginLogger.Service.debug("Invalid Base64 format, assuming plain text", e)
            encryptedText
        }
    }

    /**
     * Check if a string appears to be encrypted (Base64 encoded)
     */
    fun isEncrypted(text: String): Boolean {
        if (text.isBlank()) return false

        return try {
            Base64.getDecoder().decode(text)
            // If it's valid Base64 and doesn't look like a typical API key format, assume it's encrypted
            !text.matches(Regex("^[a-zA-Z0-9_-]+$")) || text.length > MAX_API_KEY_LENGTH
        } catch (_: IllegalArgumentException) {
            PluginLogger.Service.debug("Invalid Base64 format in isEncrypted check")
            false
        }
    }
}
