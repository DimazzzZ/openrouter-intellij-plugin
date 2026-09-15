package org.zhavoronkov.openrouter.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Unit tests for the pure PKCE helpers extracted onto [PkceAuthHandler]. These run under the
 * fast `:test` task — no IntelliJ platform, sockets, or browser involved.
 */
@DisplayName("PkceAuthHandler PKCE Helpers")
class PkceAuthHandlerTest {

    private val base64UrlPattern = Regex("^[A-Za-z0-9_-]+$")

    @Nested
    @DisplayName("Code verifier")
    inner class CodeVerifier {

        @Test
        fun `verifier is base64url without padding`() {
            val verifier = PkceAuthHandler.generatePkceVerifier()
            assertTrue(base64UrlPattern.matches(verifier), "verifier must be base64url: $verifier")
            assertTrue(!verifier.contains("="), "verifier must not be padded")
        }

        @Test
        fun `verifier encodes 32 bytes to 43 chars`() {
            // 32 bytes base64url-unpadded = ceil(32*4/3) = 43 chars
            assertEquals(43, PkceAuthHandler.generatePkceVerifier().length)
        }

        @Test
        fun `verifier is different across calls`() {
            val a = PkceAuthHandler.generatePkceVerifier()
            val b = PkceAuthHandler.generatePkceVerifier()
            assertTrue(a != b, "two verifiers should not collide")
        }

        @Test
        fun `verifier is deterministic for a seeded random`() {
            val seeded = { SecureRandom.getInstance("SHA1PRNG").apply { setSeed(42L) } }
            val first = PkceAuthHandler.generatePkceVerifier(seeded())
            val second = PkceAuthHandler.generatePkceVerifier(seeded())
            assertEquals(first, second)
        }
    }

    @Nested
    @DisplayName("Code challenge")
    inner class CodeChallenge {

        @Test
        fun `challenge is base64url without padding`() {
            val challenge = PkceAuthHandler.generatePkceChallenge("some-verifier")
            assertTrue(base64UrlPattern.matches(challenge), "challenge must be base64url: $challenge")
            assertTrue(!challenge.contains("="), "challenge must not be padded")
        }

        @Test
        fun `challenge is SHA-256 S256 of the verifier`() {
            val verifier = "test-verifier-value"
            val expected = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
            )
            assertEquals(expected, PkceAuthHandler.generatePkceChallenge(verifier))
        }

        @Test
        fun `SHA-256 challenge is always 43 chars`() {
            // 32-byte digest base64url-unpadded = 43 chars
            assertEquals(43, PkceAuthHandler.generatePkceChallenge("anything").length)
        }
    }

    @Nested
    @DisplayName("Auth code extraction")
    inner class AuthCodeExtraction {

        @Test
        fun `extracts code from a callback request line`() {
            val line = "GET /callback?code=abc123 HTTP/1.1"
            assertEquals("abc123", PkceAuthHandler.extractAuthCode(line))
        }

        @Test
        fun `strips trailing query parameters`() {
            val line = "GET /callback?code=abc123&state=xyz HTTP/1.1"
            assertEquals("abc123", PkceAuthHandler.extractAuthCode(line))
        }

        @Test
        fun `strips trailing HTTP token when no extra params`() {
            val line = "GET /callback?code=onlycode HTTP/1.1"
            assertEquals("onlycode", PkceAuthHandler.extractAuthCode(line))
        }

        @Test
        fun `returns null for a non-callback request`() {
            assertNull(PkceAuthHandler.extractAuthCode("GET /favicon.ico HTTP/1.1"))
        }

        @Test
        fun `returns null when callback has no code parameter`() {
            assertNull(PkceAuthHandler.extractAuthCode("GET /callback?error=denied HTTP/1.1"))
        }

        @Test
        fun `returns null for empty code value`() {
            assertNull(PkceAuthHandler.extractAuthCode("GET /callback?code= HTTP/1.1"))
        }
    }
}
