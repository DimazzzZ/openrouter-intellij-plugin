package org.zhavoronkov.openrouter.toolwindow.status

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("SparklineGeometry")
class SparklineGeometryTest {

    @Test
    @DisplayName("an empty series plots nothing")
    fun `an empty series plots nothing`() {
        assertTrue(SparklineGeometry.plot(emptyList(), 100, 20).isEmpty())
    }

    @Test
    @DisplayName("a single value sits on the baseline at the left edge")
    fun `a single value sits on the baseline`() {
        val points = SparklineGeometry.plot(listOf(5.0), 100, 20)

        assertEquals(1, points.size)
        assertEquals(SparklineGeometry.Point(0, 19), points[0])
    }

    @Test
    @DisplayName("the first and last points touch the left and right edges")
    fun `the series spans the full width`() {
        val points = SparklineGeometry.plot(listOf(1.0, 2.0, 3.0), 100, 20)

        assertEquals(0, points.first().x)
        assertEquals(99, points.last().x)
    }

    @Test
    @DisplayName("the maximum touches the top and the minimum touches the bottom")
    fun `the series spans the full height`() {
        val points = SparklineGeometry.plot(listOf(1.0, 3.0, 2.0), 100, 20)

        assertEquals(19, points[0].y)
        assertEquals(0, points[1].y)
    }

    @Test
    @DisplayName("a flat series sits on the baseline instead of dividing by zero")
    fun `a flat series sits on the baseline`() {
        val points = SparklineGeometry.plot(listOf(4.0, 4.0, 4.0), 100, 20)

        assertEquals(listOf(19, 19, 19), points.map { it.y })
    }

    @Test
    @DisplayName("all-zero spend sits on the baseline rather than filling the box")
    fun `all zero sits on the baseline`() {
        val points = SparklineGeometry.plot(listOf(0.0, 0.0), 100, 20)

        assertEquals(listOf(19, 19), points.map { it.y })
    }

    @Test
    @DisplayName("x positions are evenly spaced")
    fun `x positions are evenly spaced`() {
        val points = SparklineGeometry.plot(listOf(1.0, 2.0, 3.0, 4.0, 5.0), 81, 20)

        assertEquals(listOf(0, 20, 40, 60, 80), points.map { it.x })
    }

    @Test
    @DisplayName("a zero-sized box plots nothing rather than negative coordinates")
    fun `a zero sized box plots nothing`() {
        assertTrue(SparklineGeometry.plot(listOf(1.0, 2.0), 0, 20).isEmpty())
        assertTrue(SparklineGeometry.plot(listOf(1.0, 2.0), 100, 0).isEmpty())
    }

    @Test
    @DisplayName("every point stays inside the box")
    fun `every point stays inside the box`() {
        val points = SparklineGeometry.plot(listOf(0.1, 99.0, 3.0, 0.0, 42.0), 37, 11)

        assertTrue(points.all { it.x in 0..36 }, "x out of range: $points")
        assertTrue(points.all { it.y in 0..10 }, "y out of range: $points")
    }
}
