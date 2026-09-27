package org.zhavoronkov.openrouter.settings

import com.intellij.openapi.ui.ComboBox
import org.zhavoronkov.openrouter.models.DataRegion
import org.zhavoronkov.openrouter.models.DataRegions
import javax.swing.DefaultComboBoxModel
import javax.swing.JLabel

/**
 * The "Data region" control and the sentence underneath it.
 *
 * Its own class rather than more fields on [OpenRouterSettingsPanel]: the panel is already large,
 * and this control has real behaviour of its own - it narrows to what the account allows, explains
 * why it is disabled when it is, and refuses to drop a selection it no longer recognises.
 *
 * It owns no service and reads no settings; the caller supplies the region and the availability.
 * That keeps it constructible in a plain test.
 */
class DataRegionSection {

    val comboBox = ComboBox<DataRegion>(DefaultComboBoxModel(arrayOf(DataRegion.GLOBAL)))
    val comment = JLabel(CHECKING_TEXT)

    /** Set by the caller; invoked when the user picks a different region. */
    var onRegionChosen: (DataRegion) -> Unit = {}

    private var baseComment: String = CHECKING_TEXT

    init {
        comboBox.addActionListener {
            onRegionChosen(getRegion())
        }
    }

    /** The region currently shown. */
    fun getRegion(): DataRegion = comboBox.selectedItem as? DataRegion ?: DataRegion.GLOBAL

    fun setRegion(region: DataRegion) {
        if (items().none { it == region }) {
            comboBox.addItem(region)
        }
        comboBox.selectedItem = region
    }

    /**
     * Narrows the control to the regions the account may actually use, and explains the result.
     *
     * The current selection is kept in the list even when it is no longer among the available
     * ones. A region can be withdrawn without the user touching anything - a plan downgrade, a
     * new guardrail policy, a replaced key - and quietly resetting the control to Global would
     * move their traffic out of the region they chose, which for anyone who chose it on
     * data-residency grounds is precisely the outcome they were avoiding. It stays selected and
     * visible; the failure surfaces as failing requests, which is loud and honest.
     */
    fun setAvailableRegions(regions: List<DataRegion>) {
        val selected = getRegion()
        val items = (regions + selected).distinct()

        comboBox.model = DefaultComboBoxModel(items.toTypedArray())
        comboBox.selectedItem = selected

        // One entry means Global alone: there is nothing to choose, so the control is shown
        // disabled with the reason rather than hidden. The control stays enabled when a selection
        // has been withdrawn, so the user can act on the warning.
        val stillAvailable = DataRegions.isStillAvailable(selected, regions)
        val canChoose = regions.size > 1
        comboBox.isEnabled = canChoose || !stillAvailable
        baseComment = when {
            !stillAvailable -> withdrawnText(selected)
            canChoose -> AVAILABLE_TEXT
            else -> UNAVAILABLE_TEXT
        }
        comment.text = baseComment
    }

    /**
     * Appends what the selected region would cost in favourites, or clears it when there is
     * nothing to say.
     *
     * Kept beside the control rather than raised as a dialog: it is information for a decision
     * the user is in the middle of making, not an error, and a region that serves every favourite
     * should say nothing at all.
     */
    fun setFavoritesImpact(summary: String?) {
        comment.text = if (summary == null) baseComment else "$baseComment $summary"
    }

    private fun withdrawnText(region: DataRegion) =
        "${region.displayName} is no longer available to your keys, so requests will fail until you " +
            "choose another region. It has been left selected rather than changed for you."

    private fun items(): List<DataRegion?> = (0 until comboBox.itemCount).map { comboBox.getItemAt(it) }

    companion object {
        /** Shown until the account's allowed regions are known. */
        const val CHECKING_TEXT = "Checking which data regions this account may use..."

        /** Shown when the account has no in-region routing entitlement, which is the common case. */
        const val UNAVAILABLE_TEXT =
            "In-region routing is not enabled for this account, so requests use OpenRouter's global endpoint."

        /** Shown when a region can actually be chosen. */
        const val AVAILABLE_TEXT =
            "Pins every OpenRouter request to this region. Fewer models are available in a region than globally."
    }
}
