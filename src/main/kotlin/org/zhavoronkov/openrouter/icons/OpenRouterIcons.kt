package org.zhavoronkov.openrouter.icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Icon definitions for the OpenRouter plugin
 *
 * All icons are loaded using IntelliJ's IconLoader to ensure proper theme support
 * and high-DPI scaling.
 */
object OpenRouterIcons {

    /** Icon for the tool window (16x16) */
    @JvmField
    val TOOL_WINDOW: Icon = IconLoader.getIcon("/icons/openrouter-16.png", OpenRouterIcons::class.java)

    /** Icon for the status bar widget (13x13) */
    @JvmField
    val STATUS_BAR: Icon = IconLoader.getIcon("/icons/openrouter-13.png", OpenRouterIcons::class.java)

    /** Icon for refresh actions (16x16) */
    @JvmField
    val REFRESH: Icon = IconLoader.getIcon("/icons/openrouter-16.png", OpenRouterIcons::class.java)

    /** Icon for settings actions (16x16) */
    @JvmField
    val SETTINGS: Icon = IconLoader.getIcon("/icons/openrouter-16.png", OpenRouterIcons::class.java)

    /** Icon indicating successful connection/operation (16x16) */
    @JvmField
    val SUCCESS: Icon = IconLoader.getIcon("/icons/openrouter-plugin-success-16.png", OpenRouterIcons::class.java)

    /** Icon indicating error/failed connection (16x16) */
    @JvmField
    val ERROR: Icon = IconLoader.getIcon("/icons/openrouter-plugin-error-16.png", OpenRouterIcons::class.java)
}
