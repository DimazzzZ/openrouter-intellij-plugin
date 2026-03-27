package org.zhavoronkov.openrouter.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ApiResult Tests")
class ApiResultTest {

    @Nested
    @DisplayName("Success")
    inner class SuccessTests {

        @Test
        fun `Success contains data and status code`() {
            val result = ApiResult.Success("test data", 200)
            assertEquals("test data", result.data)
            assertEquals(200, result.statusCode)
        }

        @Test
        fun `Success with different types works`() {
            val intResult = ApiResult.Success(42, 200)
            assertEquals(42, intResult.data)

            val listResult = ApiResult.Success(listOf(1, 2, 3), 200)
            assertEquals(listOf(1, 2, 3), listResult.data)
        }
    }

    @Nested
    @DisplayName("Error")
    inner class ErrorTests {

        @Test
        fun `Error contains message`() {
            val result = ApiResult.Error("Error message")
            assertEquals("Error message", result.message)
        }

        @Test
        fun `Error can have status code`() {
            val result = ApiResult.Error("Error", 404)
            assertEquals(404, result.statusCode)
        }

        @Test
        fun `Error can have throwable`() {
            val exception = RuntimeException("Test exception")
            val result = ApiResult.Error("Error", 500, exception)
            assertEquals(exception, result.throwable)
        }

        @Test
        fun `Error has null defaults`() {
            val result = ApiResult.Error("Error")
            assertNull(result.statusCode)
            assertNull(result.throwable)
        }
    }

    @Nested
    @DisplayName("map extension")
    inner class MapTests {

        @Test
        fun `map transforms Success data`() {
            val result: ApiResult<Int> = ApiResult.Success(5, 200)
            val mapped = result.map { it * 2 }

            assertTrue(mapped is ApiResult.Success)
            assertEquals(10, (mapped as ApiResult.Success).data)
            assertEquals(200, mapped.statusCode)
        }

        @Test
        fun `map preserves Error`() {
            val result: ApiResult<Int> = ApiResult.Error("Error", 500)
            val mapped = result.map { it * 2 }

            assertTrue(mapped is ApiResult.Error)
            assertEquals("Error", (mapped as ApiResult.Error).message)
        }

        @Test
        fun `map can change type`() {
            val result: ApiResult<Int> = ApiResult.Success(5, 200)
            val mapped = result.map { it.toString() }

            assertTrue(mapped is ApiResult.Success)
            assertEquals("5", (mapped as ApiResult.Success).data)
        }
    }

    @Nested
    @DisplayName("onSuccess extension")
    inner class OnSuccessTests {

        @Test
        fun `onSuccess executes block for Success`() {
            var called = false
            var receivedData: String? = null

            val result: ApiResult<String> = ApiResult.Success("data", 200)
            result.onSuccess {
                called = true
                receivedData = it
            }

            assertTrue(called)
            assertEquals("data", receivedData)
        }

        @Test
        fun `onSuccess does not execute block for Error`() {
            var called = false

            val result: ApiResult<String> = ApiResult.Error("Error")
            result.onSuccess { called = true }

            assertTrue(!called)
        }

        @Test
        fun `onSuccess returns same result`() {
            val result: ApiResult<String> = ApiResult.Success("data", 200)
            val returned = result.onSuccess { }

            assertEquals(result, returned)
        }
    }

    @Nested
    @DisplayName("onError extension")
    inner class OnErrorTests {

        @Test
        fun `onError executes block for Error`() {
            var called = false
            var receivedError: ApiResult.Error? = null

            val result: ApiResult<String> = ApiResult.Error("Error message", 500)
            result.onError {
                called = true
                receivedError = it
            }

            assertTrue(called)
            assertNotNull(receivedError)
            assertEquals("Error message", receivedError?.message)
        }

        @Test
        fun `onError does not execute block for Success`() {
            var called = false

            val result: ApiResult<String> = ApiResult.Success("data", 200)
            result.onError { called = true }

            assertTrue(!called)
        }

        @Test
        fun `onError returns same result`() {
            val result: ApiResult<String> = ApiResult.Error("Error")
            val returned = result.onError { }

            assertEquals(result, returned)
        }
    }

    @Nested
    @DisplayName("getOrNull extension")
    inner class GetOrNullTests {

        @Test
        fun `getOrNull returns data for Success`() {
            val result: ApiResult<String> = ApiResult.Success("data", 200)
            assertEquals("data", result.getOrNull())
        }

        @Test
        fun `getOrNull returns null for Error`() {
            val result: ApiResult<String> = ApiResult.Error("Error")
            assertNull(result.getOrNull())
        }
    }

    @Nested
    @DisplayName("Chaining")
    inner class ChainingTests {

        @Test
        fun `onSuccess and onError can be chained`() {
            var successCalled = false
            var errorCalled = false

            val result: ApiResult<String> = ApiResult.Success("data", 200)
            result
                .onSuccess { successCalled = true }
                .onError { errorCalled = true }

            assertTrue(successCalled)
            assertTrue(!errorCalled)
        }

        @Test
        fun `map and onSuccess can be chained`() {
            var receivedData: Int? = null

            val result: ApiResult<Int> = ApiResult.Success(5, 200)
            result
                .map { it * 2 }
                .onSuccess { receivedData = it }

            assertEquals(10, receivedData)
        }
    }
}
