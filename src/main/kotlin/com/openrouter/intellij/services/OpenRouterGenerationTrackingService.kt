package com.openrouter.intellij.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.openrouter.intellij.models.GenerationTrackingInfo
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Service for tracking OpenRouter API generations and their costs
 */
@State(
    name = "OpenRouterGenerationTracking",
    storages = [Storage("openrouter-generations.xml")]
)
class OpenRouterGenerationTrackingService : PersistentStateComponent<OpenRouterGenerationTrackingService.State> {
    
    data class State(
        var generations: MutableList<GenerationTrackingInfo> = mutableListOf()
    )
    
    private var state = State()
    private val settingsService = OpenRouterSettingsService.getInstance()
    
    companion object {
        fun getInstance(): OpenRouterGenerationTrackingService {
            return ApplicationManager.getApplication().getService(OpenRouterGenerationTrackingService::class.java)
        }
    }
    
    override fun getState(): State {
        return state
    }
    
    override fun loadState(state: State) {
        this.state = state
    }
    
    /**
     * Track a new generation
     */
    fun trackGeneration(generationInfo: GenerationTrackingInfo) {
        if (!settingsService.getState().trackGenerations) {
            return
        }
        
        state.generations.add(0, generationInfo) // Add to beginning for most recent first
        
        // Limit the number of tracked generations
        val maxTracked = settingsService.getState().maxTrackedGenerations
        if (state.generations.size > maxTracked) {
            state.generations = state.generations.take(maxTracked).toMutableList()
        }
    }
    
    /**
     * Get recent generations
     */
    fun getRecentGenerations(limit: Int = 10): List<GenerationTrackingInfo> {
        return state.generations.take(limit)
    }
    
    /**
     * Get total cost for recent generations
     */
    fun getTotalRecentCost(limit: Int = 100): Double {
        return state.generations.take(limit)
            .mapNotNull { it.totalCost }
            .sum()
    }
    
    /**
     * Get total tokens for recent generations
     */
    fun getTotalRecentTokens(limit: Int = 100): Int {
        return state.generations.take(limit)
            .mapNotNull { it.totalTokens }
            .sum()
    }
    
    /**
     * Clear all tracked generations
     */
    fun clearGenerations() {
        state.generations.clear()
    }
    
    /**
     * Get generation count
     */
    fun getGenerationCount(): Int {
        return state.generations.size
    }
    
    /**
     * Update generation with detailed stats (when available from /api/v1/generation endpoint)
     */
    fun updateGenerationStats(generationId: String, promptTokens: Int?, completionTokens: Int?, totalTokens: Int?, totalCost: Double?) {
        val generation = state.generations.find { it.generationId == generationId }
        if (generation != null) {
            val index = state.generations.indexOf(generation)
            val updated = generation.copy(
                promptTokens = promptTokens ?: generation.promptTokens,
                completionTokens = completionTokens ?: generation.completionTokens,
                totalTokens = totalTokens ?: generation.totalTokens,
                totalCost = totalCost ?: generation.totalCost
            )
            state.generations[index] = updated
        }
    }
}
