package org.zhavoronkov.openrouter.services.settings

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.AuthScope
import org.zhavoronkov.openrouter.models.OpenRouterSettings
import org.zhavoronkov.openrouter.utils.EncryptionUtil
import org.zhavoronkov.openrouter.utils.PasswordSafeKeyStorage

/**
 * Branch-focused tests for [ApiKeySettingsManager].
 *
 * Complements [ApiKeySettingsManagerTest] by exercising the legacy-key migration
 * paths (PasswordSafe empty + legacy XML value present, both encrypted and plaintext),
 * the auth-scope-dependent configuration/validation arms, and logout key clearing.
 *
 * [PasswordSafeKeyStorage] is an app-level object that falls back to in-memory storage
 * outside an IntelliJ runtime, so each test resets it via resetCacheForTesting() to
 * guarantee isolation.
 */
@DisplayName("ApiKeySettingsManager Branch Tests")
class ApiKeySettingsManagerBranchTest {

    @BeforeEach
    fun setUp() {
        PasswordSafeKeyStorage.resetCacheForTesting()
    }

    @AfterEach
    fun tearDown() {
        PasswordSafeKeyStorage.resetCacheForTesting()
    }

    private fun manager(
        settings: OpenRouterSettings,
        onChange: () -> Unit = {}
    ) = ApiKeySettingsManager(settings, onChange)

    @Nested
    @DisplayName("getApiKey legacy migration")
    inner class GetApiKeyMigration {

        @Test
        @DisplayName("prefers PasswordSafe value over legacy settings")
        fun `returns PasswordSafe value when present`() {
            PasswordSafeKeyStorage.setApiKey("sk-or-v1-fromsafe")
            val settings = OpenRouterSettings(apiKey = "sk-or-v1-legacyxml")
            var changes = 0

            val result = manager(settings) { changes++ }.getApiKey()

            assertThat(result).isEqualTo("sk-or-v1-fromsafe")
            assertThat(settings.apiKey).isEqualTo("sk-or-v1-legacyxml")
            assertThat(changes).isZero()
        }

        @Test
        @DisplayName("migrates plaintext legacy key to PasswordSafe and clears settings")
        fun `migrates plaintext legacy key`() {
            val legacyPlain = "sk-or-v1-legacyplainkey"
            val settings = OpenRouterSettings(apiKey = legacyPlain)
            var changes = 0

            val result = manager(settings) { changes++ }.getApiKey()

            assertThat(result).isEqualTo(legacyPlain)
            assertThat(settings.apiKey).isEmpty()
            assertThat(PasswordSafeKeyStorage.getApiKey()).isEqualTo(legacyPlain)
            assertThat(changes).isEqualTo(1)
        }

        @Test
        @DisplayName("decrypts and migrates encrypted legacy key")
        fun `migrates encrypted legacy key`() {
            val secret = "sk-or-v1-secretvalue123"
            val encrypted = EncryptionUtil.encrypt(secret)
            assertThat(EncryptionUtil.isEncrypted(encrypted)).isTrue()
            val settings = OpenRouterSettings(apiKey = encrypted)
            var changes = 0

            val result = manager(settings) { changes++ }.getApiKey()

            assertThat(result).isEqualTo(secret)
            assertThat(settings.apiKey).isEmpty()
            assertThat(PasswordSafeKeyStorage.getApiKey()).isEqualTo(secret)
            assertThat(changes).isEqualTo(1)
        }

        @Test
        @DisplayName("returns empty and does not migrate when nothing is stored")
        fun `returns empty when no key anywhere`() {
            val settings = OpenRouterSettings(apiKey = "")
            var changes = 0

            val result = manager(settings) { changes++ }.getApiKey()

            assertThat(result).isEmpty()
            assertThat(changes).isZero()
        }
    }

    @Nested
    @DisplayName("getProvisioningKey legacy migration")
    inner class GetProvisioningKeyMigration {

        @Test
        @DisplayName("prefers PasswordSafe value over legacy settings")
        fun `returns PasswordSafe provisioning value when present`() {
            PasswordSafeKeyStorage.setProvisioningKey("sk-or-v1-provsafe")
            val settings = OpenRouterSettings(provisioningKey = "sk-or-v1-provlegacy")

            val result = manager(settings).getProvisioningKey()

            assertThat(result).isEqualTo("sk-or-v1-provsafe")
            assertThat(settings.provisioningKey).isEqualTo("sk-or-v1-provlegacy")
        }

        @Test
        @DisplayName("migrates plaintext legacy provisioning key")
        fun `migrates plaintext legacy provisioning key`() {
            val legacyPlain = "sk-or-v1-provplainkey"
            val settings = OpenRouterSettings(provisioningKey = legacyPlain)
            var changes = 0

            val result = manager(settings) { changes++ }.getProvisioningKey()

            assertThat(result).isEqualTo(legacyPlain)
            assertThat(settings.provisioningKey).isEmpty()
            assertThat(PasswordSafeKeyStorage.getProvisioningKey()).isEqualTo(legacyPlain)
            assertThat(changes).isEqualTo(1)
        }

        @Test
        @DisplayName("decrypts and migrates encrypted legacy provisioning key")
        fun `migrates encrypted legacy provisioning key`() {
            val secret = "sk-or-v1-provsecret456"
            val encrypted = EncryptionUtil.encrypt(secret)
            assertThat(EncryptionUtil.isEncrypted(encrypted)).isTrue()
            val settings = OpenRouterSettings(provisioningKey = encrypted)

            val result = manager(settings).getProvisioningKey()

            assertThat(result).isEqualTo(secret)
            assertThat(settings.provisioningKey).isEmpty()
            assertThat(PasswordSafeKeyStorage.getProvisioningKey()).isEqualTo(secret)
        }

        @Test
        @DisplayName("returns empty when no provisioning key anywhere")
        fun `returns empty when no provisioning key`() {
            val result = manager(OpenRouterSettings()).getProvisioningKey()

            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("set methods clear legacy storage")
    inner class SetClearsLegacy {

        @Test
        @DisplayName("setApiKey clears a populated legacy setting")
        fun `setApiKey clears legacy`() {
            val settings = OpenRouterSettings(apiKey = "old-legacy-xml-value")
            var changes = 0
            val manager = manager(settings) { changes++ }

            manager.setApiKey("sk-or-v1-brandnew")

            assertThat(settings.apiKey).isEmpty()
            assertThat(manager.getApiKey()).isEqualTo("sk-or-v1-brandnew")
            assertThat(changes).isGreaterThanOrEqualTo(1)
        }

        @Test
        @DisplayName("setProvisioningKey clears a populated legacy setting")
        fun `setProvisioningKey clears legacy`() {
            val settings = OpenRouterSettings(provisioningKey = "old-prov-xml-value")
            var changes = 0
            val manager = manager(settings) { changes++ }

            manager.setProvisioningKey("sk-or-v1-newprov")

            assertThat(settings.provisioningKey).isEmpty()
            assertThat(manager.getProvisioningKey()).isEqualTo("sk-or-v1-newprov")
            assertThat(changes).isGreaterThanOrEqualTo(1)
        }

        @Test
        @DisplayName("setApiKey leaves an already-empty legacy setting untouched")
        fun `setApiKey with empty legacy`() {
            val settings = OpenRouterSettings(apiKey = "")
            val manager = manager(settings)

            manager.setApiKey("sk-or-v1-value")

            assertThat(settings.apiKey).isEmpty()
            assertThat(manager.getApiKey()).isEqualTo("sk-or-v1-value")
        }
    }

    @Nested
    @DisplayName("getStoredApiKey")
    inner class GetStoredApiKey {

        @Test
        @DisplayName("returns the key when configured")
        fun `returns key when present`() {
            PasswordSafeKeyStorage.setApiKey("sk-or-v1-stored")

            val result = manager(OpenRouterSettings()).getStoredApiKey()

            assertThat(result).isEqualTo("sk-or-v1-stored")
        }

        @Test
        @DisplayName("returns null when no key is configured")
        fun `returns null when absent`() {
            val result = manager(OpenRouterSettings()).getStoredApiKey()

            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("isConfigured by auth scope")
    inner class IsConfigured {

        @Test
        @DisplayName("REGULAR: true when api key present")
        fun `regular configured`() {
            PasswordSafeKeyStorage.setApiKey("sk-or-v1-regular")
            val settings = OpenRouterSettings(authScope = AuthScope.REGULAR)

            assertThat(manager(settings).isConfigured()).isTrue()
        }

        @Test
        @DisplayName("REGULAR: false when api key absent")
        fun `regular not configured`() {
            val settings = OpenRouterSettings(authScope = AuthScope.REGULAR)

            assertThat(manager(settings).isConfigured()).isFalse()
        }

        @Test
        @DisplayName("EXTENDED: true when provisioning key present")
        fun `extended configured`() {
            PasswordSafeKeyStorage.setProvisioningKey("sk-or-v1-extended")
            val settings = OpenRouterSettings(authScope = AuthScope.EXTENDED)

            assertThat(manager(settings).isConfigured()).isTrue()
        }

        @Test
        @DisplayName("EXTENDED: false when provisioning key absent")
        fun `extended not configured`() {
            val settings = OpenRouterSettings(authScope = AuthScope.EXTENDED)

            assertThat(manager(settings).isConfigured()).isFalse()
        }
    }

    @Nested
    @DisplayName("validateKeyForCurrentScope")
    inner class ValidateKeyForScope {

        @Test
        @DisplayName("REGULAR: null when a well-formed api key is present")
        fun `regular valid key`() {
            PasswordSafeKeyStorage.setApiKey("sk-or-v1-validregularkey")
            val settings = OpenRouterSettings(authScope = AuthScope.REGULAR)

            assertThat(manager(settings).validateKeyForCurrentScope()).isNull()
        }

        @Test
        @DisplayName("REGULAR: message when api key is blank")
        fun `regular blank key`() {
            val settings = OpenRouterSettings(authScope = AuthScope.REGULAR)

            assertThat(manager(settings).validateKeyForCurrentScope())
                .isEqualTo("API key is not configured")
        }

        @Test
        @DisplayName("REGULAR: message when api key format is invalid")
        fun `regular invalid key`() {
            PasswordSafeKeyStorage.setApiKey("short")
            val settings = OpenRouterSettings(authScope = AuthScope.REGULAR)

            assertThat(manager(settings).validateKeyForCurrentScope()).isNotNull()
        }

        @Test
        @DisplayName("EXTENDED: null when a well-formed provisioning key is present")
        fun `extended valid key`() {
            PasswordSafeKeyStorage.setProvisioningKey("sk-or-v1-validprovkey")
            val settings = OpenRouterSettings(authScope = AuthScope.EXTENDED)

            assertThat(manager(settings).validateKeyForCurrentScope()).isNull()
        }

        @Test
        @DisplayName("EXTENDED: message when provisioning key is blank")
        fun `extended blank key`() {
            val settings = OpenRouterSettings(authScope = AuthScope.EXTENDED)

            assertThat(manager(settings).validateKeyForCurrentScope())
                .isEqualTo("Provisioning key is not configured")
        }

        @Test
        @DisplayName("EXTENDED: message when provisioning key format is invalid")
        fun `extended invalid key`() {
            PasswordSafeKeyStorage.setProvisioningKey("bad")
            val settings = OpenRouterSettings(authScope = AuthScope.EXTENDED)

            assertThat(manager(settings).validateKeyForCurrentScope()).isNotNull()
        }
    }

    @Nested
    @DisplayName("authScope + clearAllKeys")
    inner class ScopeAndClear {

        @Test
        @DisplayName("authScope setter propagates to settings and fires onStateChanged")
        fun `authScope setter`() {
            val settings = OpenRouterSettings(authScope = AuthScope.REGULAR)
            var changes = 0
            val manager = manager(settings) { changes++ }

            manager.authScope = AuthScope.EXTENDED

            assertThat(settings.authScope).isEqualTo(AuthScope.EXTENDED)
            assertThat(manager.authScope).isEqualTo(AuthScope.EXTENDED)
            assertThat(changes).isEqualTo(1)
        }

        @Test
        @DisplayName("clearAllKeys wipes both stores and fires onStateChanged")
        fun `clearAllKeys clears everything`() {
            PasswordSafeKeyStorage.setApiKey("sk-or-v1-api")
            PasswordSafeKeyStorage.setProvisioningKey("sk-or-v1-prov")
            val settings = OpenRouterSettings(apiKey = "legacy-a", provisioningKey = "legacy-p")
            var changes = 0
            val manager = manager(settings) { changes++ }

            manager.clearAllKeys()

            assertThat(manager.getApiKey()).isEmpty()
            assertThat(manager.getProvisioningKey()).isEmpty()
            assertThat(settings.apiKey).isEmpty()
            assertThat(settings.provisioningKey).isEmpty()
            assertThat(changes).isGreaterThanOrEqualTo(1)
        }
    }
}
