package org.zhavoronkov.openrouter.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState

/** Where the plugin's own UI is updated from another thread. */
object Edt {

    /**
     * Runs [action] on the EDT, later, in any modality: it updates the plugin's own UI, which a
     * modal dialog open in front of it - Settings, say - must not hold back.
     */
    fun later(action: Runnable) {
        ApplicationManager.getApplication().invokeLater(action, ModalityState.any())
    }
}
