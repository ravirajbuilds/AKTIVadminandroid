package com.example.anubhavlifecare.utils

import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Map raw networking/HTTP exceptions to short, user-facing messages.
 * Retrofit surfaces cryptic text ("HTTP 422", "Unable to resolve host ...") that
 * is useless to a receptionist, so translate the common cases here.
 */
fun Throwable.toUserMessage(): String = when (this) {
    is UnknownHostException, is ConnectException ->
        "Cannot reach the AKTIV server. Check the clinic Wi-Fi and that the API is running."
    is SocketTimeoutException ->
        "The AKTIV server took too long to respond. Please try again."
    is HttpException -> httpMessage(this)
    is IOException ->
        "Network error. Check the clinic Wi-Fi and try again."
    else -> message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again."
}

private fun httpMessage(e: HttpException): String {
    val code = e.code()
    // FastAPI raises HTTPException(detail="...") for our handled errors, so pull
    // that string out when present. 422 validation bodies use a list detail and
    // deliberately fall through to the generic message below.
    val detail = try {
        e.response()?.errorBody()?.string()?.let { body ->
            Regex("\"detail\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.getOrNull(1)
        }
    } catch (_: Exception) {
        null
    }
    return when {
        !detail.isNullOrBlank() -> detail
        code == 401 -> "Invalid username or password."
        code == 422 -> "Some details are invalid. Please review and try again."
        code in 500..599 -> "The AKTIV server hit an error (HTTP $code). Please try again."
        else -> "Request failed (HTTP $code)."
    }
}
