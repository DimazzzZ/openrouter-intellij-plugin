package org.zhavoronkov.openrouter.presets

import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.proxy.pairs.PairAvailability
import org.zhavoronkov.openrouter.services.FavoriteModelsService
import org.zhavoronkov.openrouter.services.OpenRouterService
import java.nio.file.Path

/**
 * The application's [PresetCopy], read through the configured API key and kept beside the
 * Requests log. It is read once in the background when first used; the Presets and Favorite
 * Models pages ask for a read when they open, and saving a preset asks for one after.
 */
@Service(Service.Level.APP)
class PresetCopyService {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val copy: PresetCopy = PresetCopy(
        file = Path.of(PathManager.getConfigPath(), "openrouter", FILE_NAME),
        list = ::listPresets,
        read = ::readVersion,
        scope = scope
    ).also { it.refreshLater() }

    private suspend fun listPresets(): List<PresetListing>? =
        when (val result = OpenRouterService.getInstance().getPresets()) {
            is ApiResult.Success -> result.data.data.map { PresetListing(it.slug, it.name) }
            is ApiResult.Error -> null
        }

    private suspend fun readVersion(slug: String): PresetVersion? {
        val version = OpenRouterService.getInstance().getPresetVersionJson(slug) ?: return null
        val config = version.get("config")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
        val prompt = version.get("system_prompt")?.takeIf { it.isJsonPrimitive }?.asString
        return PresetVersion(prompt, config)
    }

    companion object {
        private const val FILE_NAME = "presets.json"

        fun getInstance(): PresetCopyService =
            ApplicationManager.getApplication().getService(PresetCopyService::class.java)

        /** Whether pairs can be sent, asked of this copy of the presets and the loaded catalogue. */
        fun pairs(): PairAvailability {
            val copy = getInstance().copy
            return PairAvailability(
                presets = copy::snapshot,
                lookup = copy::find,
                catalogue = { FavoriteModelsService.getInstance().getCachedModels() }
            )
        }
    }
}
