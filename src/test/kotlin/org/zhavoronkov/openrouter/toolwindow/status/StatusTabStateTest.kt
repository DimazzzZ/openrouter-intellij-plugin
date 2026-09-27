package org.zhavoronkov.openrouter.toolwindow.status

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("StatusTabState")
class StatusTabStateTest {

    private fun inputs(
        configured: Boolean = true,
        hasProvisioningKey: Boolean = true,
        hasData: Boolean = true,
        isLoading: Boolean = false,
        error: String? = null
    ) = StatusTabState.Inputs(configured, hasProvisioningKey, hasData, isLoading, error)

    @Test
    @DisplayName("an unconfigured plugin reports NOT_CONFIGURED whatever else is true")
    fun `an unconfigured plugin reports NOT_CONFIGURED`() {
        assertEquals(
            StatusTabState.State.NOT_CONFIGURED,
            StatusTabState.derive(inputs(configured = false, hasData = true, isLoading = true))
        )
    }

    @Test
    @DisplayName("a missing provisioning key reports DEGRADED, not an error")
    fun `a missing provisioning key reports DEGRADED`() {
        assertEquals(
            StatusTabState.State.DEGRADED,
            StatusTabState.derive(inputs(hasProvisioningKey = false))
        )
    }

    @Test
    @DisplayName("loading with nothing to show reports LOADING")
    fun `loading with nothing to show reports LOADING`() {
        assertEquals(
            StatusTabState.State.LOADING,
            StatusTabState.derive(inputs(hasData = false, isLoading = true))
        )
    }

    @Test
    @DisplayName("loading over existing data stays READY so the numbers are not replaced by a spinner")
    fun `loading over existing data stays READY`() {
        assertEquals(
            StatusTabState.State.READY,
            StatusTabState.derive(inputs(hasData = true, isLoading = true))
        )
    }

    @Test
    @DisplayName("an error with stale data still reports ERROR so the failure is visible")
    fun `an error with stale data reports ERROR`() {
        assertEquals(
            StatusTabState.State.ERROR,
            StatusTabState.derive(inputs(hasData = true, error = "boom"))
        )
    }

    @Test
    @DisplayName("an error while loading reports LOADING - a retry in flight is not a failure yet")
    fun `an error while loading reports LOADING`() {
        assertEquals(
            StatusTabState.State.LOADING,
            StatusTabState.derive(inputs(hasData = false, isLoading = true, error = "stale boom"))
        )
    }

    @Test
    @DisplayName("nothing loaded, not loading, no error reports LOADING rather than a blank READY")
    fun `nothing loaded and idle reports LOADING`() {
        assertEquals(
            StatusTabState.State.LOADING,
            StatusTabState.derive(inputs(hasData = false, isLoading = false))
        )
    }

    @Test
    @DisplayName("DEGRADED outranks an error, because the cause is configuration not the network")
    fun `DEGRADED outranks an error`() {
        assertEquals(
            StatusTabState.State.DEGRADED,
            StatusTabState.derive(inputs(hasProvisioningKey = false, error = "boom"))
        )
    }

    @Test
    @DisplayName("a failed first load reports ERROR, not a spinner that never resolves")
    fun `a failed first load reports ERROR`() {
        assertEquals(
            StatusTabState.State.ERROR,
            StatusTabState.derive(inputs(hasData = false, isLoading = false, error = "boom"))
        )
    }
}
