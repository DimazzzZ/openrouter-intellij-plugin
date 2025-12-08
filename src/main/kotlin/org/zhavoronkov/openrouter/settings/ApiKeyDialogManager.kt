package org.zhavoronkov.openrouter.settings

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.dsl.builder.panel
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.Action
import javax.swing.JComponent

/**
 * Manages API key display dialogs
 */
class ApiKeyDialogManager {

    companion object {
        private const val DIALOG_WIDTH = 500
        private const val DIALOG_HEIGHT = 200
        private const val API_KEY_PREVIEW_LENGTH = 10
        private const val API_KEY_TRUNCATE_LENGTH = 20
    }

    fun showApiKeyDialog(apiKey: String, label: String) {
        val dialog = ApiKeyDialog(apiKey, label)
        dialog.show()
    }

    private class ApiKeyDialog(
        private val apiKey: String,
        private val label: String
    ) : DialogWrapper(null) {

        init {
            title = "API Key Created"
            init()
        }

        override fun createCenterPanel(): JComponent {
            val mainPanel = JBPanel<JBPanel<*>>(BorderLayout())
            mainPanel.preferredSize = Dimension(DIALOG_WIDTH, DIALOG_HEIGHT)

            val contentPanel = panel {
                row {
                    label("API key created successfully!")
                }
                row {
                    label("Label: $label")
                }
                row {
                    label("API Key:")
                }
                row {
                    val keyPreview = if (apiKey.length > API_KEY_TRUNCATE_LENGTH) {
                        apiKey.substring(0, API_KEY_PREVIEW_LENGTH) + "..." +
                            apiKey.substring(apiKey.length - API_KEY_PREVIEW_LENGTH)
                    } else {
                        apiKey
                    }
                    label(keyPreview).bold()
                }
                row {
                    label("")
                }
                row {
                    val warningLabel = JBLabel(
                        "<html><b>Important:</b> This is the only time you'll see this key. " +
                            "Copy it now!</html>"
                    )
                    cell(warningLabel)
                }
                row {
                    button("Copy to Clipboard") {
                        copyToClipboard(apiKey)
                    }
                }
            }

            mainPanel.add(contentPanel, BorderLayout.CENTER)
            return mainPanel
        }

        override fun createActions(): Array<Action> {
            return arrayOf(
                object : DialogWrapperAction("Copy & Close") {
                    override fun doAction(e: java.awt.event.ActionEvent?) {
                        copyToClipboard(apiKey)
                        close(OK_EXIT_CODE)
                    }
                },
                cancelAction
            )
        }

        private fun copyToClipboard(text: String) {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(text), null)
        }
    }
}
