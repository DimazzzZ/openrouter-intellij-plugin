package org.zhavoronkov.openrouter.utils

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.OpenRouterResponse
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun Call.await(): Response = withContext(Dispatchers.IO) {
    suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isCancelled) return
                cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
        })

        cont.invokeOnCancellation {
            try {
                cancel()
            } catch (_: Throwable) {
                // ignored
            }
        }
    }
}

/**
 * Response paired with its already-read, already-closed body.
 *
 * The [Response] here has had its body fully consumed and closed, so callers
 * may read metadata (code, headers, isSuccessful) freely without leaking the
 * OkHttp connection. Use this for non-streaming calls; streaming callers must
 * keep using [okhttp3.Response.use] and read the body incrementally.
 */
data class BufferedResponse(val response: Response, val body: String)

/**
 * Suspend variant of [await] that fully reads and closes the response body
 * before returning, so no caller can leak the connection by taking an early
 * return or throwing before consuming the body. [Response.use] guarantees the
 * body is closed even if [okhttp3.ResponseBody.string] throws.
 */
suspend fun Call.awaitWithBody(): BufferedResponse {
    val response = await()
    return response.use { resp ->
        BufferedResponse(resp, resp.body?.string().orEmpty())
    }
}

inline fun <reified T> Response.toApiResult(gson: Gson): ApiResult<T> {
    return use { resp ->
        val bodyString = resp.body?.string().orEmpty()
        when {
            !resp.isSuccessful -> {
                val errorMessage = try {
                    val errorObj = gson.fromJson(bodyString, OpenRouterResponse::class.java)
                    errorObj?.error?.message ?: bodyString
                } catch (_: Exception) {
                    bodyString
                }

                ApiResult.Error(
                    message = errorMessage.ifBlank { resp.message }
                        .ifBlank { "HTTP ${resp.code}" },
                    statusCode = resp.code
                )
            }
            else -> {
                val trimmed = bodyString.trimStart()
                try {
                    val data = gson.fromJson(trimmed, T::class.java)
                    ApiResult.Success(data, resp.code)
                } catch (e: JsonSyntaxException) {
                    ApiResult.Error(
                        message = "Failed to parse response: ${e.message}",
                        statusCode = resp.code,
                        throwable = e
                    )
                }
            }
        }
    }
}
