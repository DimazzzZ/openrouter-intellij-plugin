package org.zhavoronkov.openrouter.settings

import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import javax.swing.table.TableCellRenderer

/**
 * Display model for available models table
 */
data class AvailableModelDisplay(
    val model: OpenRouterModelInfo,
    val provider: String,
    val contextWindow: String,
    val capabilities: String
) {
    companion object {
        private const val UNKNOWN_VALUE = "—"
        private const val CONTEXT_SUFFIX = "K"
        private const val CAPABILITIES_SEPARATOR = ", "

        fun from(model: OpenRouterModelInfo): AvailableModelDisplay {
            val provider = extractProvider(model.id)
            val contextWindow = formatContextWindow(model.contextLength)
            val capabilities = formatCapabilities(model)

            return AvailableModelDisplay(
                model = model,
                provider = provider,
                contextWindow = contextWindow,
                capabilities = capabilities
            )
        }

        private fun extractProvider(modelId: String): String {
            val parts = modelId.split("/")
            return if (parts.size >= 2) parts[0] else UNKNOWN_VALUE
        }

        private fun formatContextWindow(contextLength: Int?): String {
            return if (contextLength != null && contextLength > 0) {
                "${contextLength / 1000}$CONTEXT_SUFFIX"
            } else {
                UNKNOWN_VALUE
            }
        }

        private fun formatCapabilities(model: OpenRouterModelInfo): String {
            val caps = mutableListOf<String>()

            model.architecture?.inputModalities?.let { modalities ->
                if (modalities.contains("image")) caps.add("Vision")
                if (modalities.contains("audio")) caps.add("Audio")
            }

            model.architecture?.outputModalities?.let { modalities ->
                if (modalities.contains("image")) caps.add("Image Gen")
            }

            model.supportedParameters?.let { params ->
                if (params.contains("tools") || params.contains("functions")) {
                    caps.add("Tools")
                }
            }

            return if (caps.isNotEmpty()) {
                caps.joinToString(CAPABILITIES_SEPARATOR)
            } else {
                UNKNOWN_VALUE
            }
        }
    }
}

/**
 * Display model for favorite models table
 */
data class FavoriteModelDisplay(
    val model: OpenRouterModelInfo,
    val isAvailable: Boolean = true
) {
    companion object {
        fun from(model: OpenRouterModelInfo, availableModels: List<OpenRouterModelInfo>): FavoriteModelDisplay {
            val isAvailable = availableModels.any { it.id == model.id }
            return FavoriteModelDisplay(
                model = model,
                isAvailable = isAvailable
            )
        }
    }
}

/**
 * Column definitions for available models table
 */
object AvailableModelsColumns {
    val MODEL_ID = object : ColumnInfo<AvailableModelDisplay, String>("Model ID") {
        override fun valueOf(item: AvailableModelDisplay): String = item.model.id
        override fun getPreferredStringValue(): String = "anthropic/claude-3.5-sonnet-20241022"
    }

    val PROVIDER = object : ColumnInfo<AvailableModelDisplay, String>("Provider") {
        override fun valueOf(item: AvailableModelDisplay): String = item.provider
        override fun getPreferredStringValue(): String = "anthropic"
    }

    val CONTEXT = object : ColumnInfo<AvailableModelDisplay, String>("Context") {
        override fun valueOf(item: AvailableModelDisplay): String = item.contextWindow
        override fun getPreferredStringValue(): String = "200K"
    }

    val CAPABILITIES = object : ColumnInfo<AvailableModelDisplay, String>("Capabilities") {
        override fun valueOf(item: AvailableModelDisplay): String = item.capabilities
        override fun getPreferredStringValue(): String = "Vision, Tools"
    }

    fun createTableModel(): ListTableModel<AvailableModelDisplay> {
        return ListTableModel(
            arrayOf(MODEL_ID, PROVIDER, CONTEXT, CAPABILITIES),
            mutableListOf()
        )
    }
}

/**
 * Column definitions for favorite models table
 */
object FavoriteModelsColumns {
    val MODEL_ID = object : ColumnInfo<FavoriteModelDisplay, String>("Model ID") {
        override fun valueOf(item: FavoriteModelDisplay): String = item.model.id
        override fun getPreferredStringValue(): String = "anthropic/claude-3.5-sonnet-20241022"

        override fun getRenderer(item: FavoriteModelDisplay?): TableCellRenderer? {
            // Could add custom renderer here to show unavailable models in gray
            return null
        }
    }

    val STATUS = object : ColumnInfo<FavoriteModelDisplay, String>("Status") {
        override fun valueOf(item: FavoriteModelDisplay): String {
            return if (item.isAvailable) "Available" else "Unavailable"
        }
        override fun getPreferredStringValue(): String = "Available"
    }

    fun createTableModel(): ListTableModel<FavoriteModelDisplay> {
        return ListTableModel(
            arrayOf(MODEL_ID, STATUS),
            mutableListOf()
        )
    }
}

/**
 * Helper class for managing table model operations
 */
class FavoriteModelsTableHelper {
    companion object {
        /**
         * Convert models to available display items
         */
        fun toAvailableDisplayItems(models: List<OpenRouterModelInfo>): List<AvailableModelDisplay> {
            return models.map { AvailableModelDisplay.from(it) }
        }

        /**
         * Convert models to favorite display items
         */
        fun toFavoriteDisplayItems(
            favoriteModels: List<OpenRouterModelInfo>,
            availableModels: List<OpenRouterModelInfo>
        ): List<FavoriteModelDisplay> {
            return favoriteModels.map { FavoriteModelDisplay.from(it, availableModels) }
        }

        /**
         * Filter available models by search text
         */
        fun filterModels(
            models: List<AvailableModelDisplay>,
            searchText: String
        ): List<AvailableModelDisplay> {
            if (searchText.isBlank()) {
                return models
            }

            val lowerSearch = searchText.lowercase()
            return models.filter { display ->
                display.model.id.lowercase().contains(lowerSearch) ||
                display.provider.lowercase().contains(lowerSearch) ||
                display.model.name.lowercase().contains(lowerSearch) ||
                display.capabilities.lowercase().contains(lowerSearch)
            }
        }

        /**
         * Remove favorites from available list to avoid duplicates
         */
        fun excludeFavorites(
            available: List<AvailableModelDisplay>,
            favorites: List<FavoriteModelDisplay>
        ): List<AvailableModelDisplay> {
            val favoriteIds = favorites.map { it.model.id }.toSet()
            return available.filter { !favoriteIds.contains(it.model.id) }
        }
    }
}

