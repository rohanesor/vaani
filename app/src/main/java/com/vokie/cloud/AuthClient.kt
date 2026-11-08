package com.vokie.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Auth client for VAANI cloud authentication.
 * Handles login, registration, and logout.
 */
class AuthClient(private val baseUrl: String) {
    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 30_000
    }

    /**
     * Registers a new user.
     * @throws AuthException on errors
     */
    suspend fun register(
        email: String,
        password: String,
        displayName: String,
        preferredLanguage: String = "en-IN",
    ): AuthResult = withContext(Dispatchers.IO) {
        val url = URL("${baseUrl.trimEnd('/')}/api/auth/register")
        val body = JSONObject().apply {
            put("email", email)
            put("password", password)
            put("displayName", displayName)
            put("preferredLanguage", preferredLanguage)
        }
        executeAuth(url, body)
    }

    /**
     * Logs in an existing user.
     * @throws AuthException on errors
     */
    suspend fun login(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val url = URL("${baseUrl.trimEnd('/')}/api/auth/login")
        val body = JSONObject().apply {
            put("email", email)
            put("password", password)
        }
        executeAuth(url, body)
    }

    /**
     * Logs out the current session.
     * @throws AuthException on errors
     */
    suspend fun logout(token: String): Unit = withContext(Dispatchers.IO) {
        val url = URL("${baseUrl.trimEnd('/')}/api/auth/logout")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            doOutput = false
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val stream = connection.errorStream
                val errorBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw AuthException.ServerError("Logout failed: $responseCode")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun executeAuth(url: URL, body: JSONObject): AuthResult {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
        }

        try {
            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(body.toString())
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            when (responseCode) {
                200, 201 -> return parseAuthResult(responseBody)
                400 -> {
                    val error = runCatching { JSONObject(responseBody) }.getOrNull()
                    val message = error?.optString("message") ?: "Invalid request"
                    val field = error?.optJSONObject("details")?.optString("field")
                    throw AuthException.ValidationError(message, field)
                }
                401 -> throw AuthException.InvalidCredentials("Email or password is incorrect")
                429 -> throw AuthException.RateLimited("Too many attempts. Please wait and try again.")
                else -> throw AuthException.ServerError("Server error: $responseCode")
            }
        } catch (e: AuthException) {
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            throw AuthException.NetworkError("Connection timeout")
        } catch (e: java.io.IOException) {
            throw AuthException.NetworkError("Network error: ${e.message}")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseAuthResult(json: String): AuthResult {
        val root = JSONObject(json)
        val user = root.getJSONObject("user")
        val settings = root.optJSONObject("settings")

        return AuthResult(
            token = root.getString("token"),
            userId = user.getLong("id"),
            email = user.optString("email"),
            displayName = user.getString("displayName"),
            avatarHue = user.getInt("avatarHue"),
            preferredLanguage = user.optString("preferredLanguage", "en-IN"),
            aiEnabled = settings?.optBoolean("aiEnabled", true) ?: true,
        )
    }
}

data class AuthResult(
    val token: String,
    val userId: Long,
    val email: String?,
    val displayName: String,
    val avatarHue: Int,
    val preferredLanguage: String,
    val aiEnabled: Boolean,
)

sealed class AuthException(message: String) : Exception(message) {
    class ValidationError(message: String, val field: String?) : AuthException(message)
    class InvalidCredentials(message: String) : AuthException(message)
    class RateLimited(message: String) : AuthException(message)
    class NetworkError(message: String) : AuthException(message)
    class ServerError(message: String) : AuthException(message)
}
