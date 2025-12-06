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

inline fun <reified T> Response.toApiResult(gson: Gson): ApiResult<T> {
    return use { resp ->
        val bodyString = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) {
            return ApiResult.Error(
                message = bodyString.ifBlank { resp.message }
                    .ifBlank { "HTTP ${resp.code}" },
                statusCode = resp.code
            )
        }
        val trimmed = bodyString.trimStart()
        return try {
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
