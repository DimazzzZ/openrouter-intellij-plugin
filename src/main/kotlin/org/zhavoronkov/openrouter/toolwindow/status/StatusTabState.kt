package org.zhavoronkov.openrouter.toolwindow.status

/**
 * Which of the tab's five states to render.
 *
 * Deliberately free of Swing and IntelliJ imports: the fast `test` Gradle task
 * runs headless and excludes platform-touching tests, so keeping this pure is
 * what lets the tab's state logic be covered at all. Previously the tab had no
 * state logic to speak of - loading, staleness, failure and "not configured"
 * all rendered as the same line of text.
 */
object StatusTabState {

    /**
     * @param configured an API key is present
     * @param hasProvisioningKey a management key is present, so analytics is reachable
     * @param hasData previously loaded numbers exist and can still be shown
     * @param isLoading a refresh is in flight
     * @param error the last failure, if any, from the cache
     */
    data class Inputs(
        val configured: Boolean,
        val hasProvisioningKey: Boolean,
        val hasData: Boolean,
        val isLoading: Boolean,
        val error: String?
    )

    enum class State {
        NOT_CONFIGURED,

        /**
         * An API key is present but a management key is not. Measured against the live API
         * (2026-09-21, correction C1): this does NOT mean "no account data" - `/credits` answers
         * for an ordinary API key too, so `hasData` can genuinely be `true` here. It means
         * "reduced capability": the balance is real and shown, while the per-model breakdown,
         * activity and the API key spend cap - all still management-key-only - say so explicitly
         * rather than rendering as if nothing were configured.
         */
        DEGRADED,
        LOADING,
        READY,
        ERROR
    }

    /**
     * Order matters and encodes the spec's decision D7.
     *
     * Configuration problems outrank transport problems: a missing key is not
     * something a retry fixes, so reporting it as an error would send the user
     * to the wrong place. With nothing to show, a refresh in flight outranks a
     * recorded failure, but an idle failure is reported rather than hidden
     * behind a spinner. With data on screen the state stays READY so known
     * numbers are not replaced, the refresh being indicated by the view rather
     * than by a state change.
     *
     * The rendering distinction between "skeleton" and "indicator over existing
     * data" is the VIEW's business and reads `isLoading` itself.
     */
    fun derive(inputs: Inputs): State = when {
        !inputs.configured -> State.NOT_CONFIGURED
        !inputs.hasProvisioningKey -> State.DEGRADED
        !inputs.hasData && !inputs.isLoading && inputs.error != null -> State.ERROR
        !inputs.hasData -> State.LOADING
        inputs.error != null -> State.ERROR
        else -> State.READY
    }
}
