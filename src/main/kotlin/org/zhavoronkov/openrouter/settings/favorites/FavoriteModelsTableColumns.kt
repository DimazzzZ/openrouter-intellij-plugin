package org.zhavoronkov.openrouter.settings.favorites

import com.intellij.ui.BooleanTableCellEditor
import com.intellij.ui.BooleanTableCellRenderer
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.ui.VariantChipTableCellRenderer
import org.zhavoronkov.openrouter.utils.ModelPricingFormatter
import org.zhavoronkov.openrouter.utils.ModelProviderUtils
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

/**
 * Column definitions for the single favorites table.
 *
 * Values and comparators only read [state] and plain model data, so they are
 * unit-testable headlessly. Renderers and editors are created lazily and only
 * touched by a live [JTable].
 */
class FavoriteModelsTableColumns(private val state: FavoriteModelsPageState) {

    private val booleanRenderer by lazy { BooleanTableCellRenderer(SwingConstants.CENTER) }
    private val booleanEditor by lazy { BooleanTableCellEditor() }
    private val chipRenderer by lazy { VariantChipTableCellRenderer(isAvailable = state::isAvailable) }
    private val contextRenderer by lazy {
        object : DefaultTableCellRenderer() {
            init {
                horizontalAlignment = SwingConstants.RIGHT
            }

            override fun setValue(value: Any?) {
                text = ModelProviderUtils.formatContextLength(value as? Int)
            }
        }
    }

    val favorite: ColumnInfo<OpenRouterModelInfo, Boolean> =
        object : ColumnInfo<OpenRouterModelInfo, Boolean>("★") {
            override fun valueOf(item: OpenRouterModelInfo): Boolean = state.isFavorite(item.id)
            override fun getColumnClass(): Class<*> = java.lang.Boolean::class.java
            override fun isCellEditable(item: OpenRouterModelInfo): Boolean = true
            override fun setValue(item: OpenRouterModelInfo, value: Boolean) {
                state.setFavorite(item.id, value)
            }
            override fun getRenderer(item: OpenRouterModelInfo?): TableCellRenderer = booleanRenderer
            override fun getEditor(item: OpenRouterModelInfo?): TableCellEditor = booleanEditor
            override fun getWidth(table: JTable?): Int = JBUI.scale(STAR_WIDTH)
        }

    val model: ColumnInfo<OpenRouterModelInfo, String> =
        object : ColumnInfo<OpenRouterModelInfo, String>("Model") {
            override fun valueOf(item: OpenRouterModelInfo): String = item.id
            override fun getComparator(): Comparator<OpenRouterModelInfo> = compareBy { it.id }
            override fun getRenderer(item: OpenRouterModelInfo?): TableCellRenderer = chipRenderer
            override fun getPreferredStringValue(): String = "anthropic/claude-3.5-sonnet-20241022"
        }

    val context: ColumnInfo<OpenRouterModelInfo, Int> =
        object : ColumnInfo<OpenRouterModelInfo, Int>("Context") {
            override fun valueOf(item: OpenRouterModelInfo): Int? = item.contextLength
            override fun getComparator(): Comparator<OpenRouterModelInfo> =
                compareBy(nullsLast()) { it.contextLength }
            override fun getRenderer(item: OpenRouterModelInfo?): TableCellRenderer = contextRenderer
            override fun getWidth(table: JTable?): Int = JBUI.scale(CONTEXT_WIDTH)
        }

    val input: ColumnInfo<OpenRouterModelInfo, String> =
        priceColumn("Input", { ModelPricingFormatter.formatInputPrice(it.pricing) }, { it.pricing?.prompt })

    val output: ColumnInfo<OpenRouterModelInfo, String> =
        priceColumn("Output", { ModelPricingFormatter.formatOutputPrice(it.pricing) }, { it.pricing?.completion })

    fun asArray(): Array<ColumnInfo<OpenRouterModelInfo, *>> = arrayOf(favorite, model, context, input, output)

    private fun priceColumn(
        name: String,
        text: (OpenRouterModelInfo) -> String,
        raw: (OpenRouterModelInfo) -> String?,
    ): ColumnInfo<OpenRouterModelInfo, String> =
        object : ColumnInfo<OpenRouterModelInfo, String>(name) {
            override fun valueOf(item: OpenRouterModelInfo): String = text(item)
            override fun getComparator(): Comparator<OpenRouterModelInfo> =
                compareBy(nullsLast()) { raw(it)?.toDoubleOrNull() }
            override fun getWidth(table: JTable?): Int = JBUI.scale(PRICE_WIDTH)
            override fun getPreferredStringValue(): String = "$0.0000"
        }

    private companion object {
        const val STAR_WIDTH = 28
        const val CONTEXT_WIDTH = 64
        const val PRICE_WIDTH = 72
    }
}
