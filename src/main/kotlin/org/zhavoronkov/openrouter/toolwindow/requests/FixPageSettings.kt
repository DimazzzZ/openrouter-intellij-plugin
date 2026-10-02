package org.zhavoronkov.openrouter.toolwindow.requests

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import org.zhavoronkov.openrouter.models.FixPage
import org.zhavoronkov.openrouter.settings.FavoriteModelsConfigurable
import org.zhavoronkov.openrouter.settings.OpenRouterConfigurable
import org.zhavoronkov.openrouter.settings.OutputSchemasConfigurable
import org.zhavoronkov.openrouter.settings.PresetsConfigurable
import org.zhavoronkov.openrouter.utils.ExcludeFromCoverage

/** Where each [FixPage] is in the Settings dialog: one page per refusal reason, opened by its balloon. */
object FixPageSettings {

    /** The settings page [page] names; the Data Region lives on the main OpenRouter page. */
    fun configurable(page: FixPage): Class<out Configurable> = when (page) {
        FixPage.PRESETS -> PresetsConfigurable::class.java
        FixPage.FAVORITE_MODELS -> FavoriteModelsConfigurable::class.java
        FixPage.OUTPUT_SCHEMAS -> OutputSchemasConfigurable::class.java
        FixPage.DATA_REGION -> OpenRouterConfigurable::class.java
    }

    /** The action's text, e.g. "Open Presets". */
    fun actionText(page: FixPage): String = "Open ${page.title}"

    /** Opens the Settings dialog at [page]; with no [project] - the welcome screen - at the IDE level. */
    @ExcludeFromCoverage("opens the modal Settings dialog")
    fun open(project: Project?, page: FixPage) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, configurable(page))
    }
}
