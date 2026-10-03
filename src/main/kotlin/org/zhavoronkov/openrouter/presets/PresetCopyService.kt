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
import org.zhavoronkov.openrouter.utils.ExcludeFromCoverage
import org.zhavoronkov.openrouter.utils.asObjectOrNull
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

    @ExcludeFromCoverage("asks OpenRouter over the network; PresetCopy is tested with a list of its own")
    private suspend fun listPresets(): List<PresetListing>? =
        when (val result = OpenRouterService.getInstance().getPresets()) {
            is ApiResult.Success -> result.data.data.map { PresetListing(it.slug, it.name) }
            is ApiResult.Error -> null
        }

    @ExcludeFromCoverage("asks OpenRouter over the network; what it makes of the answer is versionOf")
    private suspend fun readVersion(slug: String): PresetVersion? =
        OpenRouterService.getInstance().getPresetVersionJson(slug)?.let(::versionOf)

    companion object {
        private const val FILE_NAME = "presets.json"

        /** A preset's designated version as OpenRouter sent it: its prompt, and its config as sent. */
        internal fun versionOf(version: JsonObject): PresetVersion {
            val config = version.get("config")?.asObjectOrNull() ?: JsonObject()
            val prompt = version.get("system_prompt")?.takeIf { it.isJsonPrimitive }?.asString
            return PresetVersion(prompt, config)
        }

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
