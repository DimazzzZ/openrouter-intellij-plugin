# Settings Dialog Optimization Plan

## 🐛 Problem Analysis

When opening the OpenRouter settings dialog, there is a noticeable delay caused by **multiple redundant API calls**.

### Current Behavior (from logs):

```
[OpenRouter][DEBUG] OpenRouter Settings Panel INITIALIZED
[OpenRouter][DEBUG] Loading API keys without auto-creation          ← 1st API call
[OpenRouter][DEBUG] Fetching API keys list from OpenRouter...
[OpenRouter][DEBUG] Loaded 5 API keys from OpenRouter

[OpenRouter] REFRESH BUTTON CLICKED - refreshApiKeys() called       ← 2nd API call
[OpenRouter][DEBUG] Fetching API keys list from OpenRouter...
[OpenRouter] Loaded 5 API keys from OpenRouter

[OpenRouter] REFRESH BUTTON CLICKED - refreshApiKeys() called       ← 3rd API call
[OpenRouter][DEBUG] Fetching API keys list from OpenRouter...
[OpenRouter] Loaded 5 API keys from OpenRouter
```

**Result**: 3 identical API calls to `/api/v1/keys` fetching the same data!

### Root Cause:

**File**: `OpenRouterConfigurable.kt`

```kotlin
override fun createComponent(): JComponent? {
    settingsPanel = OpenRouterSettingsPanel()
    
    // Line 25: setProvisioningKey() triggers loadApiKeysWithoutAutoCreate()
    panel.setProvisioningKey(settingsService.getProvisioningKey())  // ← 1st call
    
    panel.setAutoRefresh(settingsService.isAutoRefreshEnabled())
    panel.setRefreshInterval(settingsService.getRefreshInterval())
    panel.setShowCosts(settingsService.shouldShowCosts())
    
    // Line 33: Explicit refresh
    panel.refreshApiKeys()  // ← 2nd call
    
    return panel.getPanel()
}

override fun reset() {
    // Line 86: Reset also refreshes
    panel.refreshApiKeys()  // ← 3rd call (when reset is called)
}
```

## ✅ Optimization Strategy

### 1. **Implement API Keys Caching** (Primary Fix)

Create a caching layer in `ApiKeyManager` similar to `FavoriteModelsService`:

```kotlin
class ApiKeyManager {
    companion object {
        private const val CACHE_DURATION_MS = 60000L // 1 minute
    }
    
    private var cachedApiKeys: List<ApiKeyInfo>? = null
    private var cacheTimestamp: Long = 0L
    
    fun loadApiKeys(forceRefresh: Boolean = false) {
        val now = System.currentTimeMillis()
        val isCacheValid = cachedApiKeys != null && (now - cacheTimestamp) < CACHE_DURATION_MS
        
        if (!forceRefresh && isCacheValid) {
            PluginLogger.Settings.debug("Using cached API keys (${cachedApiKeys?.size} keys)")
            apiKeyTableModel.setApiKeys(cachedApiKeys!!)
            return
        }
        
        // Fetch from API...
        cachedApiKeys = apiKeys
        cacheTimestamp = now
    }
}
```

**Benefits**:
- First call fetches from API
- Subsequent calls within 1 minute use cache
- Explicit "Refresh" button bypasses cache
- Dialog opens instantly on subsequent opens

### 2. **Remove Redundant Calls** (Secondary Fix)

**Option A**: Remove the explicit `refreshApiKeys()` call in `createComponent()`:

```kotlin
override fun createComponent(): JComponent? {
    settingsPanel = OpenRouterSettingsPanel()
    
    panel.setProvisioningKey(settingsService.getProvisioningKey())  // Loads API keys
    panel.setAutoRefresh(settingsService.isAutoRefreshEnabled())
    panel.setRefreshInterval(settingsService.getRefreshInterval())
    panel.setShowCosts(settingsService.shouldShowCosts())
    
    // REMOVED: panel.refreshApiKeys()  ← No longer needed!
    
    return panel.getPanel()
}
```

**Option B**: Make `setProvisioningKey()` NOT load API keys, only `refreshApiKeys()` does:

```kotlin
fun setProvisioningKey(provisioningKey: String) {
    provisioningKeyField.text = provisioningKey
    // REMOVED: apiKeyManager.loadApiKeysWithoutAutoCreate()
}
```

**Recommendation**: Use **Option A** - it's safer and clearer.

### 3. **Lazy Loading for Favorite Models** (Bonus Optimization)

The Favorite Models panel already has caching (5 minutes), but we can defer loading until the user actually opens that sub-page:

```kotlin
class FavoriteModelsSettingsPanel {
    private var dataLoaded = false
    
    fun createPanel(): JPanel {
        // Don't load data in init block
        // Load only when panel becomes visible
    }
    
    override fun addNotify() {
        super.addNotify()
        if (!dataLoaded && keyPresent) {
            loadInitialData()
            dataLoaded = true
        }
    }
}
```

**Benefits**:
- Main settings dialog opens faster
- Models only load when user navigates to Favorite Models
- Cache still prevents redundant loads

## 📊 Expected Performance Improvement

### Before Optimization:
- **3 API calls** to `/api/v1/keys` (each ~200-500ms)
- **Total delay**: ~600-1500ms
- **User experience**: Noticeable lag when opening settings

### After Optimization:
- **1 API call** on first open (cached for 1 minute)
- **0 API calls** on subsequent opens within cache window
- **Total delay**: ~200-500ms (first open), <50ms (cached)
- **User experience**: Dialog opens quickly

### Cache Duration Rationale:

**API Keys Cache**: 1 minute
- API keys don't change frequently
- Users rarely add/remove keys during a session
- 1 minute is short enough to feel "fresh"
- Explicit "Refresh" button for immediate updates

**Models Cache**: 5 minutes (already implemented)
- Model list changes infrequently
- Larger dataset (330 models)
- Longer cache acceptable

## 🎯 Implementation Plan

### Phase 1: Add Caching to ApiKeyManager ✅
1. Add cache fields (`cachedApiKeys`, `cacheTimestamp`)
2. Add `CACHE_DURATION_MS` constant (60 seconds)
3. Modify `loadApiKeys()` to check cache first
4. Modify `refreshApiKeys()` to force refresh (bypass cache)
5. Add `clearCache()` method for testing

### Phase 2: Remove Redundant Calls ✅
1. Remove `panel.refreshApiKeys()` from `createComponent()`
2. Keep `refreshApiKeys()` in `reset()` for when user clicks "Reset"
3. Keep `refreshApiKeys()` in `apply()` when provisioning key changes

### Phase 3: Add Debug Logging ✅
1. Log cache hits/misses
2. Log cache age
3. Log when cache is bypassed

### Phase 4: Testing ✅
1. Test first open (should fetch from API)
2. Test second open within 1 minute (should use cache)
3. Test "Refresh" button (should bypass cache)
4. Test provisioning key change (should bypass cache)
5. Test after 1 minute (should fetch fresh data)

## 🔧 Code Changes Required

### Files to Modify:
1. ✅ `ApiKeyManager.kt` - Add caching logic
2. ✅ `OpenRouterConfigurable.kt` - Remove redundant refresh call
3. ✅ `OpenRouterSettingsPanel.kt` - Update refresh method signature if needed

### Files to Review:
- `FavoriteModelsService.kt` - Already has caching (reference implementation)
- `FavoriteModelsSettingsPanel.kt` - Consider lazy loading optimization

## 📝 Trade-offs

### Pros:
- ✅ Significantly faster dialog opening
- ✅ Reduced API load
- ✅ Better user experience
- ✅ Follows existing caching pattern (FavoriteModelsService)

### Cons:
- ⚠️ Data might be up to 1 minute stale
- ⚠️ Need to ensure cache is cleared on key operations
- ⚠️ Slightly more complex code

### Mitigation:
- Explicit "Refresh" button for immediate updates
- Cache cleared on provisioning key change
- Short cache duration (1 minute) balances freshness vs performance

## 🎉 Success Criteria

- [ ] Settings dialog opens in < 500ms (first time)
- [ ] Settings dialog opens in < 100ms (cached)
- [ ] Only 1 API call on first open
- [ ] 0 API calls on subsequent opens (within cache window)
- [ ] "Refresh" button still works (bypasses cache)
- [ ] Provisioning key change triggers fresh fetch
- [ ] No regression in functionality

---

**Status**: Ready for implementation
**Priority**: High (user-facing performance issue)
**Complexity**: Low (simple caching pattern)
**Risk**: Low (can easily revert if issues arise)

