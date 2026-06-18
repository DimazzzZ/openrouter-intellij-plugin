package org.zhavoronkov.openrouter.toolwindow.agent

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.IconUtil
import java.awt.Component
import javax.swing.Icon

private const val MAX_POPUP_ITEMS = 200
private const val MAX_QUERY_LENGTH = 100
private const val MAX_FILE_SCAN_MULTIPLIER = 5
private const val MAX_SORTED_ITEMS_MULTIPLIER = 3

data class FileEntry(
    val relativePath: String,
    val virtualFile: VirtualFile
) {
    override fun toString(): String = relativePath
}

class FilePickerPopup(
    private val project: Project,
    private val onSelected: (String) -> Unit
) {
    private var allFiles: List<FileEntry> = emptyList()

    fun show(component: Component, initialQuery: String = "") {
        if (allFiles.isEmpty()) {
            allFiles = collectProjectFiles()
        }

        val filtered = filterFiles(initialQuery)
        if (filtered.isEmpty()) return

        val step = object : BaseListPopupStep<FileEntry>("Files", filtered) {
            override fun getTextFor(value: FileEntry): String = value.relativePath
            override fun getIconFor(value: FileEntry): Icon {
                return IconUtil.getIcon(value.virtualFile, 0, project)
            }

            override fun onChosen(selected: FileEntry, finalChoice: Boolean): PopupStep<*>? {
                return doFinalStep { onSelected(selected.relativePath) }
            }
        }

        val popup = JBPopupFactory.getInstance().createListPopup(step)
        popup.showUnderneathOf(component)
    }

    fun filterFiles(query: String): List<FileEntry> {
        val trimmed = query.take(MAX_QUERY_LENGTH).trim()
        if (trimmed.isEmpty()) return allFiles.take(MAX_POPUP_ITEMS)

        val lowerQuery = trimmed.lowercase()
        val startsWith = mutableListOf<FileEntry>()
        val contains = mutableListOf<FileEntry>()

        for (entry in allFiles) {
            val lowerPath = entry.relativePath.lowercase()
            when {
                lowerPath.startsWith(lowerQuery) -> startsWith.add(entry)
                lowerPath.contains(lowerQuery) -> contains.add(entry)
            }
            if (startsWith.size + contains.size >= MAX_POPUP_ITEMS) break
        }

        return (startsWith + contains).take(MAX_POPUP_ITEMS)
    }

    private fun collectProjectFiles(): List<FileEntry> {
        val basePath = project.basePath ?: return emptyList()
        val scope = GlobalSearchScope.projectScope(project)
        val result = mutableListOf<FileEntry>()

        val allNames = FilenameIndex.getAllFilenames(project)
        val seen = mutableSetOf<String>()

        for (name in allNames.distinct()) {
            if (seen.size >= MAX_POPUP_ITEMS * MAX_FILE_SCAN_MULTIPLIER) break
            val files = FilenameIndex.getVirtualFilesByName(name, scope)
            for (file in files) {
                if (seen.add(file.path)) {
                    val relative = file.path.removePrefix(basePath).removePrefix("/")
                    result.add(FileEntry(relative, file))
                }
            }
        }

        return result
            .sortedWith(compareBy<FileEntry> { !it.virtualFile.isDirectory }.thenBy { it.relativePath })
            .take(MAX_POPUP_ITEMS * MAX_SORTED_ITEMS_MULTIPLIER)
    }
}
