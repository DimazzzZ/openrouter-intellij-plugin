package org.zhavoronkov.openrouter.icons

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.LayeredIcon
import javax.swing.Icon

/**
 * Icon definitions for the OpenRouter plugin
 *
 * All icons are loaded using IntelliJ's IconLoader to ensure proper theme support
 * and high-DPI scaling.
 */
object OpenRouterIcons {

    /** X/Y pixel offset that pins the 8×8 badge to the bottom-right of the 16×16 base icon. */
    private const val BADGE_OFFSET_X = 8
    private const val BADGE_OFFSET_Y = 8

    /**
     * Icon for the tool window button.
     * Base size: 16x16 (light=#6C707E / dark=#CED0D6 per JetBrains New UI guidelines).
     * IntelliJ automatically selects _dark, @20x20, and @20x20_dark variants as needed.
     * The platform remaps the prescribed colors to white when the button is active/selected.
     */
    @JvmField
    val TOOL_WINDOW: Icon = IconLoader.getIcon("/icons/openrouter-toolwindow.svg", OpenRouterIcons::class.java)

    /**
     * Base icon for the status bar widget (vector, 16x16).
     * IntelliJ automatically selects the _dark variant for dark themes.
     * Also used in the Stats Popup dialog header.
     */
    @JvmField
    val STATUS_BAR: Icon = IconLoader.getIcon("/icons/openrouter-statusbar.svg", OpenRouterIcons::class.java)

    // ── Badge icons ──────────────────────────────────────────────────────────

    /** Green circle + white checkmark badge (8x8). */
    @JvmField
    val BADGE_OK: Icon = IconLoader.getIcon("/icons/openrouter-badge-ok.svg", OpenRouterIcons::class.java)

    /** Red circle + white × badge (8x8). */
    @JvmField
    val BADGE_ERROR: Icon = IconLoader.getIcon("/icons/openrouter-badge-error.svg", OpenRouterIcons::class.java)

    // ── Composed status icons (base 16x16 + badge 8x8 at bottom-right) ──────

    /**
     * Icon indicating a successful / ready connection.
     * Renders the OpenRouter logo with a green ✓ badge in the bottom-right corner.
     */
    @JvmField
    val SUCCESS: Icon = LayeredIcon(2).also { layered ->
        layered.setIcon(STATUS_BAR, 0)
        layered.setIcon(BADGE_OK, 1, BADGE_OFFSET_X, BADGE_OFFSET_Y)
    }

    /**
     * Icon indicating an error, misconfiguration, or offline state.
     * Renders the OpenRouter logo with a red × badge in the bottom-right corner.
     */
    @JvmField
    val ERROR: Icon = LayeredIcon(2).also { layered ->
        layered.setIcon(STATUS_BAR, 0)
        layered.setIcon(BADGE_ERROR, 1, BADGE_OFFSET_X, BADGE_OFFSET_Y)
    }

    /** Icon for refresh actions (SVG, scales to 16x16) */
    @JvmField
    val REFRESH: Icon = IconLoader.getIcon("/icons/openrouter-logo.svg", OpenRouterIcons::class.java)

    /** Icon for settings actions (SVG, scales to 16x16) */
    @JvmField
    val SETTINGS: Icon = IconLoader.getIcon("/icons/openrouter-logo.svg", OpenRouterIcons::class.java)
}
