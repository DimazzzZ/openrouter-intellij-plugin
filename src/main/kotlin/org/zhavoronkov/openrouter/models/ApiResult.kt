package org.zhavoronkov.openrouter.models

/**
 * Represents a success or failure coming back from network/service layers.
 */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T, val statusCode: Int) : ApiResult<T>
    data class Error(
        val message: String,
        val statusCode: Int? = null,
        val throwable: Throwable? = null
    ) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data), statusCode)
    is ApiResult.Error -> this
}

inline fun <T> ApiResult<T>.onSuccess(block: (T) -> Unit): ApiResult<T> = apply {
    if (this is ApiResult.Success) {
        block(data)
    }
}

inline fun <T> ApiResult<T>.onError(block: (ApiResult.Error) -> Unit): ApiResult<T> = apply {
    if (this is ApiResult.Error) {
        block(this)
    }
}

fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Success)?.data
