package org.zhavoronkov.openrouter.toolwindow.requests

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import org.zhavoronkov.openrouter.requests.RequestBodies
import org.zhavoronkov.openrouter.utils.ExcludeFromCoverage
import org.zhavoronkov.openrouter.utils.MODAL_DIALOG
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants

/**
 * One request's kept bodies, a tab each: what its sender sent, what went to OpenRouter, the reply
 * and the failure - only those it has. JSON is shown indented, a streamed reply a chunk per entry;
 * the text can be selected and copied, not edited. Long lines - a prompt is one JSON string - wrap
 * at the dialog's width, so nothing is reached by scrolling sideways.
 */
class RequestBodiesDialog(parent: Component, private val bodies: RequestBodies) : DialogWrapper(parent, true) {

    init {
        title = "Request Prompt and Reply"
        init()
    }

    override fun createCenterPanel(): JComponent = JBTabbedPane().apply {
        tabs(bodies).forEach { (title, text) -> addTab(title, bodyView(text)) }
        preferredSize = Dimension(JBUI.scale(WIDTH), JBUI.scale(HEIGHT))
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)

    companion object {
        private const val WIDTH = 760
        private const val HEIGHT = 520

        /** [text], read-only and wrapped at word boundaries, scrolling only up and down. */
        fun bodyView(text: String): JBScrollPane {
            val area = JBTextArea(text).apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                font = JBUI.Fonts.create(Font.MONOSPACED, font.size)
                caretPosition = 0
            }
            return JBScrollPane(area).apply {
                horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            }
        }

        /** Opens the dialog on [bodies] over [parent]. */
        @ExcludeFromCoverage(MODAL_DIALOG)
        fun show(parent: Component, bodies: RequestBodies) {
            RequestBodiesDialog(parent, bodies).show()
        }

        /** The dialog's tabs, title and text, for the bodies [bodies] has, in the order they happened. */
        fun tabs(bodies: RequestBodies): List<Pair<String, String>> = listOfNotNull(
            bodies.received?.let { "Received" to indented(it) },
            bodies.sent?.let { "Sent to OpenRouter" to indented(it) },
            bodies.reply?.let { "Reply" to it.lines().joinToString("\n\n") { line -> indented(line) } },
            bodies.failure?.let { "Failure" to it }
        )

        private val pretty = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

        /** [text] indented when it is one JSON value, as it is otherwise - a cut body, plain text. */
        fun indented(text: String): String = try {
            // Unreachable branch: Gson.toJson never returns null
            JsonParser.parseString(text).takeIf { it.isJsonObject || it.isJsonArray }?.let(pretty::toJson) ?: text
        } catch (e: JsonSyntaxException) {
            text
        }
    }
}
