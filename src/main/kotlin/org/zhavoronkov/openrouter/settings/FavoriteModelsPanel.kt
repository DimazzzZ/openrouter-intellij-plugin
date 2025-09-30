package org.zhavoronkov.openrouter.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.*
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.util.concurrent.TimeUnit
import javax.swing.*
import javax.swing.table.AbstractTableModel

/**
 * Panel for managing favorite models
 */
class FavoriteModelsPanel : JPanel(BorderLayout()) {

    private val settingsService = OpenRouterSettingsService.getInstance()
    private val openRouterService = OpenRouterService.getInstance()
    
    // UI Components
    private val favoriteModelsTableModel = FavoriteModelsTableModel()
    private val favoriteModelsTable = JBTable(favoriteModelsTableModel)
    private val allModelsTableModel = AllModelsTableModel()
    private val allModelsTable = JBTable(allModelsTableModel)
    private val searchField = JBTextField()
    private val providerFilter = JComboBox<String>()
    
    // Data
    private var allModels: List<OpenRouterModelInfo> = emptyList()
    private var filteredModels: List<OpenRouterModelInfo> = emptyList()
    
    init {
        setupUI()
        loadData()
    }
    
    private fun setupUI() {
        // Left panel - Favorite Models
        val leftPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(10)
            preferredSize = Dimension(400, 0)
        }
        
        leftPanel.add(JBLabel("Favorite Models:"), BorderLayout.NORTH)
        
        val favoriteScrollPane = JBScrollPane(favoriteModelsTable).apply {
            preferredSize = Dimension(380, 300)
        }
        leftPanel.add(favoriteScrollPane, BorderLayout.CENTER)
        
        val favoriteButtonPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JButton("Remove").apply {
                addActionListener { removeFavoriteModel() }
            })
            add(JButton("Move Up").apply {
                addActionListener { moveFavoriteUp() }
            })
            add(JButton("Move Down").apply {
                addActionListener { moveFavoriteDown() }
            })
        }
        leftPanel.add(favoriteButtonPanel, BorderLayout.SOUTH)
        
        // Right panel - All Models Browser
        val rightPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(10)
        }
        
        rightPanel.add(JBLabel("Browse All Models:"), BorderLayout.NORTH)
        
        // Search and filter panel
        val searchPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JBLabel("Search:"))
            add(searchField.apply {
                preferredSize = Dimension(200, preferredSize.height)
                addActionListener { filterModels() }
            })
            add(JBLabel("Provider:"))
            add(providerFilter.apply {
                addActionListener { filterModels() }
            })
            add(JButton("Refresh").apply {
                addActionListener { loadAllModels() }
            })
        }
        rightPanel.add(searchPanel, BorderLayout.NORTH)
        
        val allModelsScrollPane = JBScrollPane(allModelsTable).apply {
            preferredSize = Dimension(500, 300)
        }
        rightPanel.add(allModelsScrollPane, BorderLayout.CENTER)
        
        val addButtonPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JButton("Add to Favorites").apply {
                addActionListener { addToFavorites() }
            })
        }
        rightPanel.add(addButtonPanel, BorderLayout.SOUTH)
        
        // Main layout
        add(leftPanel, BorderLayout.WEST)
        add(rightPanel, BorderLayout.CENTER)
    }
    
    private fun loadData() {
        favoriteModelsTableModel.setFavoriteModels(settingsService.getFavoriteModels())
        loadAllModels()
    }
    
    private fun loadAllModels() {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val modelsResponse = openRouterService.getModels().get(30, TimeUnit.SECONDS)
                if (modelsResponse != null) {
                    allModels = modelsResponse.data
                    
                    // Update provider filter
                    ApplicationManager.getApplication().invokeLater {
                        updateProviderFilter()
                        filterModels()
                    }
                } else {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            this@FavoriteModelsPanel,
                            "Failed to load models from OpenRouter API",
                            "Error"
                        )
                    }
                }
            } catch (e: Exception) {
                PluginLogger.Service.error("Failed to load models", e)
                ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(
                        this@FavoriteModelsPanel,
                        "Error loading models: ${e.message}",
                        "Error"
                    )
                }
            }
        }
    }
    
    private fun updateProviderFilter() {
        val providers = allModels.map { it.id.substringBefore("/") }.distinct().sorted()
        providerFilter.removeAllItems()
        providerFilter.addItem("All Providers")
        providers.forEach { providerFilter.addItem(it) }
    }
    
    private fun filterModels() {
        val searchText = searchField.text.lowercase()
        val selectedProvider = providerFilter.selectedItem as? String
        
        filteredModels = allModels.filter { model ->
            val matchesSearch = searchText.isEmpty() || 
                model.id.lowercase().contains(searchText) ||
                model.name.lowercase().contains(searchText)
            
            val matchesProvider = selectedProvider == null || 
                selectedProvider == "All Providers" ||
                model.id.startsWith("$selectedProvider/")
            
            matchesSearch && matchesProvider
        }
        
        allModelsTableModel.setModels(filteredModels)
    }
    
    private fun addToFavorites() {
        val selectedRow = allModelsTable.selectedRow
        if (selectedRow >= 0) {
            val model = filteredModels[selectedRow]
            if (!settingsService.isFavoriteModel(model.id)) {
                settingsService.addFavoriteModel(model.id)
                favoriteModelsTableModel.setFavoriteModels(settingsService.getFavoriteModels())
            }
        }
    }
    
    private fun removeFavoriteModel() {
        val selectedRow = favoriteModelsTable.selectedRow
        if (selectedRow >= 0) {
            val favoriteModels = settingsService.getFavoriteModels().toMutableList()
            val modelId = favoriteModels[selectedRow]
            settingsService.removeFavoriteModel(modelId)
            favoriteModelsTableModel.setFavoriteModels(settingsService.getFavoriteModels())
        }
    }
    
    private fun moveFavoriteUp() {
        val selectedRow = favoriteModelsTable.selectedRow
        if (selectedRow > 0) {
            val favoriteModels = settingsService.getFavoriteModels().toMutableList()
            val temp = favoriteModels[selectedRow]
            favoriteModels[selectedRow] = favoriteModels[selectedRow - 1]
            favoriteModels[selectedRow - 1] = temp
            settingsService.setFavoriteModels(favoriteModels)
            favoriteModelsTableModel.setFavoriteModels(favoriteModels)
            favoriteModelsTable.setRowSelectionInterval(selectedRow - 1, selectedRow - 1)
        }
    }
    
    private fun moveFavoriteDown() {
        val selectedRow = favoriteModelsTable.selectedRow
        val favoriteModels = settingsService.getFavoriteModels().toMutableList()
        if (selectedRow >= 0 && selectedRow < favoriteModels.size - 1) {
            val temp = favoriteModels[selectedRow]
            favoriteModels[selectedRow] = favoriteModels[selectedRow + 1]
            favoriteModels[selectedRow + 1] = temp
            settingsService.setFavoriteModels(favoriteModels)
            favoriteModelsTableModel.setFavoriteModels(favoriteModels)
            favoriteModelsTable.setRowSelectionInterval(selectedRow + 1, selectedRow + 1)
        }
    }
}

/**
 * Table model for favorite models
 */
class FavoriteModelsTableModel : AbstractTableModel() {
    private val columnNames = arrayOf("Model ID", "Provider")
    private var favoriteModels: List<String> = emptyList()
    
    override fun getRowCount(): Int = favoriteModels.size
    override fun getColumnCount(): Int = columnNames.size
    override fun getColumnName(column: Int): String = columnNames[column]
    
    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val modelId = favoriteModels[rowIndex]
        return when (columnIndex) {
            0 -> modelId
            1 -> modelId.substringBefore("/")
            else -> ""
        }
    }
    
    fun setFavoriteModels(models: List<String>) {
        favoriteModels = models
        fireTableDataChanged()
    }
}

/**
 * Table model for all available models
 */
class AllModelsTableModel : AbstractTableModel() {
    private val columnNames = arrayOf("Model ID", "Name", "Provider")
    private var models: List<OpenRouterModelInfo> = emptyList()
    
    override fun getRowCount(): Int = models.size
    override fun getColumnCount(): Int = columnNames.size
    override fun getColumnName(column: Int): String = columnNames[column]
    
    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val model = models[rowIndex]
        return when (columnIndex) {
            0 -> model.id
            1 -> model.name
            2 -> model.id.substringBefore("/")
            else -> ""
        }
    }
    
    fun setModels(newModels: List<OpenRouterModelInfo>) {
        models = newModels
        fireTableDataChanged()
    }
}
