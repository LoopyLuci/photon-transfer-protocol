package com.prism.ot.core

/**
 * Minimal typed Result to avoid the checked-exception ceremony of Kotlin and
 * to make transfer outcomes explicit in the type system.
 *
 * Kotlin's built-in `Result<T>` is a good choice for fine-grained control
 * flow, but transfer operations span hours and need to be serialisable and
 * observed across coroutines, hence a sealed class.
 */
sealed class PrismResult<out T> {

    data class Success<out T>(val value: T) : PrismResult<T>()

    data class Failure(val error: PrismError, val cause: Throwable? = null) :
        PrismResult<Nothing>()

    inline fun onSuccess(block: (T) -> Unit): PrismResult<T> {
        if (this is Success) block(value)
        return this
    }

    inline fun onFailure(block: (PrismError, Throwable?) -> Unit): PrismResult<T> {
        if (this is Failure) block(error, cause)
        return this
    }

    fun getOrNull(): T? = (this as? Success)?.value

    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw PrismException(error, cause)
    }
}

class PrismException(
    val error: PrismError,
    cause: Throwable? = null
) : Exception(error.userMessage, cause)
