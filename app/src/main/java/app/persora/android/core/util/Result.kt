package app.persora.android.core.util

import kotlinx.coroutines.CancellationException

/**
 * Like runCatching, but never swallows coroutine cancellation. Compose cancels `rememberCoroutineScope()` jobs with
 * "The coroutine scope left the composition" when a screen is popped — that must propagate, not show as an error.
 */
inline fun <T> runCatchingSafe(block: () -> T): Result<T> = try { Result.success(block()) } catch (e: CancellationException) { throw e } catch (e: Throwable) { Result.failure(e) }

fun Throwable.isCancellation(): Boolean = this is CancellationException
