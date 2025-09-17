package org.zhavoronkov.openrouter.utils

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Simple encryption utility for storing sensitive data like API keys
 */
object EncryptionUtil {
    
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES"
    
    // Generate a key based on system properties for basic obfuscation
    private fun getKey(): SecretKeySpec {
        val keyString = "${System.getProperty("user.name", "default")}_openrouter_key"
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(keyString.toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(keyBytes.sliceArray(0..15), ALGORITHM)
    }
    
    /**
     * Encrypt a string value
     */
    fun encrypt(plainText: String): String {
        if (plainText.isBlank()) return plainText
        
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getKey())
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            Base64.getEncoder().encodeToString(encryptedBytes)
        } catch (e: Exception) {
            // If encryption fails, return the original value (fallback)
            plainText
        }
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
        } catch (e: Exception) {
            // If decryption fails, assume it's already plain text (backward compatibility)
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
            !text.matches(Regex("^[a-zA-Z0-9_-]+$")) || text.length > 100
        } catch (e: Exception) {
            false
        }
    }
}
