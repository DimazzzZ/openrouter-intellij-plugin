package org.zhavoronkov.openrouter.toolwindow.requests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneOffset

/** The Requests tab built outside a running IDE, where there is no log to listen to. */
@DisplayName("RequestsTabPanel without an IDE")
class RequestsTabPanelWithoutIdeTest {

    @Test
    @DisplayName("is built without listening to the log, and shows what it reads")
    fun `is built without listening to the log`() {
        val panel = RequestsTabPanel(
            recent = { emptyList() },
            background = Runnable::run,
            edt = Runnable::run,
            clock = { Instant.EPOCH },
            zone = { ZoneOffset.UTC },
            groupBurstsSetting = { true },
            saveGroupBursts = {},
            keepBodiesSetting = { false },
            saveKeepBodies = {}
        )

        assertEquals(0, panel.table.rowCount)
        panel.dispose()
    }
}
