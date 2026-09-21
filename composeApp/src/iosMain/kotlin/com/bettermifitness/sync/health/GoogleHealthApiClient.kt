package com.bettermifitness.sync.health

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlin.coroutines.resume

/**
 * REST client for the Google Health API (`health.googleapis.com/v4`) plus the
 * OAuth 2.0 PKCE flow (authorization-code + refresh) for the iOS client.
 */
class GoogleHealthApiClient(
    private val client: HttpClient,
    private val tokenStore: GoogleHealthTokenStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun isConfigured(): Boolean = GoogleHealthAuthBridge.clientId != null

    suspend fun hasSession(): Boolean = tokenStore.hasToken()

    /**
     * Runs the full OAuth sign-in: presents the consent session, exchanges the
     * authorization code, and persists tokens. Throws on cancel/error.
     */
    suspend fun authorize() {
        val clientId = GoogleHealthAuthBridge.clientId
            ?: throw IllegalStateException("Google Health not configured")
        val reversed = GoogleHealthAuthBridge.reversedClientId ?: clientId
        val verifier = generateCodeVerifier()
        val challenge = codeChallenge(verifier)
        val redirectUri = "$reversed:/oauthredirect"

        val url = buildString {
            append(AUTH_ENDPOINT)
            append("?client_id=").append(clientId)
            append("&redirect_uri=").append(redirectUri)
            append("&response_type=code")
            append("&scope=").append(SCOPES)
            append("&code_challenge=").append(challenge)
            append("&code_challenge_method=S256")
            append("&access_type=offline")
            append("&prompt=consent")
        }

        val redirect = presentSession(url, reversed)
            ?: throw Exception("Google sign-in cancelled")
        val code = extractCode(redirect)
            ?: throw Exception("Google sign-in did not return an authorization code")

        val tokens = exchangeCode(clientId, redirectUri, code, verifier)
        tokenStore.save(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            expiresInSec = tokens.expiresInSec,
        )
    }

    /** Posts a single data point to `dataTypes/{type}/dataPoints`. */
    suspend fun createDataPoint(dataType: String, body: String) {
        val token = accessToken()
        val response = client.post("$BASE/users/me/dataTypes/$dataType/dataPoints") {
            header("Authorization", "Bearer $token")
            header("Accept", "application/json")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        handleWriteResponse(response, dataType)
    }

    private suspend fun accessToken(): String {
        val cached = tokenStore.accessToken.first()
        val expiresAt = tokenStore.expiresAtEpochSec.first()
        val now = kotlin.time.Clock.System.now().epochSeconds
        if (cached != null && expiresAt > now + 60) return cached

        val refresh = tokenStore.refreshToken.first()
            ?: throw Exception("Google Health sign-in required")
        val tokens = refreshAccessToken(refresh)
        tokenStore.save(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            expiresInSec = tokens.expiresInSec,
        )
        return tokens.accessToken
    }

    private suspend fun handleWriteResponse(response: HttpResponse, dataType: String) {
        when {
            response.status.isSuccess() -> Unit
            response.status == HttpStatusCode.NotFound ||
                response.status == HttpStatusCode.MethodNotAllowed -> {
                throw MetricUnsupportedException(
                    "Google Health does not support writing $dataType yet",
                )
            }
            else -> {
                val message = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
                throw Exception(
                    "Google Health write failed (${response.status.value})" +
                        "${if (message.isNotBlank()) ": $message" else ""}",
                )
            }
        }
    }

    private suspend fun exchangeCode(
        clientId: String,
        redirectUri: String,
        code: String,
        verifier: String,
    ): TokenResponse {
        val response = client.submitForm(
            url = TOKEN_ENDPOINT,
            formParameters = Parameters.build {
                append("client_id", clientId)
                append("redirect_uri", redirectUri)
                append("grant_type", "authorization_code")
                append("code", code)
                append("code_verifier", verifier)
            },
        )
        return parseTokenResponse(response)
    }

    private suspend fun refreshAccessToken(refreshToken: String): TokenResponse {
        val clientId = GoogleHealthAuthBridge.clientId
            ?: throw IllegalStateException("Google Health not configured")
        val response = client.submitForm(
            url = TOKEN_ENDPOINT,
            formParameters = Parameters.build {
                append("client_id", clientId)
                append("grant_type", "refresh_token")
                append("refresh_token", refreshToken)
            },
        )
        return parseTokenResponse(response)
    }

    private suspend fun parseTokenResponse(response: HttpResponse): TokenResponse {
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw Exception("Google OAuth error (${response.status.value})")
        }
        return json.decodeFromString(TokenResponse.serializer(), text)
    }

    private suspend fun presentSession(url: String, callbackScheme: String): String? {
        val presenter = GoogleHealthAuthBridge.presenter
            ?: throw IllegalStateException("Google auth bridge not registered")
        return suspendCancellableCoroutine { continuation ->
            GoogleHealthAuthBridge.setPending { redirect -> continuation.resume(redirect) }
            presenter(url, callbackScheme)
        }
    }

    private fun extractCode(redirectUrl: String): String? {
        val query = redirectUrl.substringAfter("?", "").ifBlank { return null }
        return query
            .split("&")
            .firstOrNull { it.startsWith("code=") }
            ?.removePrefix("code=")
            ?.takeIf { it.isNotBlank() }
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        Random.Default.nextBytes(bytes)
        return base64UrlNoPad(bytes)
    }

    private fun codeChallenge(verifier: String): String =
        base64UrlNoPad(Sha256.digest(verifier.encodeToByteArray()))

    @OptIn(ExperimentalEncodingApi::class)
    private fun base64UrlNoPad(bytes: ByteArray): String =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresInSec: Long = 3600,
        @SerialName("refresh_token") val refreshToken: String? = null,
    )

    private companion object {
        const val BASE = "https://health.googleapis.com/v4"
        const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"

        const val SCOPES =
            "https://www.googleapis.com/auth/googlehealth.activity_and_fitness.writeonly" +
                " https://www.googleapis.com/auth/googlehealth.health_metrics_and_measurements.writeonly" +
                " https://www.googleapis.com/auth/googlehealth.sleep.writeonly"
    }
}
