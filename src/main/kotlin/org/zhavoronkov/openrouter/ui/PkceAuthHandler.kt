@file:Suppress("TooGenericExceptionCaught", "SwallowedException")

package org.zhavoronkov.openrouter.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.utils.OpenRouterRequestBuilder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Dedicated handler for PKCE (Proof Key for Code Exchange) authentication flow
 * Extracts complex OAuth logic from SetupWizardDialog for better separation of concerns
 */
@Suppress("MaxLineLength", "MagicNumber", "UnusedParameter", "ReturnCount")
class PkceAuthHandler(
    private val coroutineScope: CoroutineScope,
    private val openRouterService: OpenRouterService,
    private val onSuccess: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onStatusUpdate: (String) -> Unit
) {
    private var serverJob: Job? = null

    /**
     * Start the PKCE authentication flow
     */
    fun startAuthFlow() {
        SetupWizardLogger.logPkceEvent("Starting PKCE authentication flow")
        onStatusUpdate("Waiting for browser...")

        val codeVerifier = generateCodeVerifier()
        startLocalServer(codeVerifier)
    }

    /**
     * Generate a cryptographically secure code verifier
     */
    private fun generateCodeVerifier(): String {
        SetupWizardLogger.logPkceEvent("Generating code verifier")
        val secureRandom = SecureRandom()
        val codeVerifier = ByteArray(32)
        secureRandom.nextBytes(codeVerifier)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifier)
    }

    /**
     * Generate code challenge from verifier using SHA-256
     */
    private fun generateCodeChallenge(codeVerifier: String): String {
        val bytes = codeVerifier.toByteArray(Charsets.US_ASCII)
        val messageDigest = MessageDigest.getInstance("SHA-256")
        messageDigest.update(bytes, 0, bytes.size)
        val digest = messageDigest.digest()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /**
     * Start local server to handle OAuth callback
     */
    private fun startLocalServer(codeVerifier: String) {
        serverJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                val port = SetupWizardConfig.PKCE_PORT
                SetupWizardLogger.logPkceEvent("Attempting to bind to port", "port=$port")

                ServerSocket(port).use { serverSocket ->
                    SetupWizardLogger.logPkceEvent("Server started", "port=$port")

                    val callbackUrl = "http://localhost:$port/callback"
                    val codeChallenge = generateCodeChallenge(codeVerifier)

                    // Construct Auth URL with OAuth app name
                    val encodedAppName = java.net.URLEncoder.encode(OpenRouterRequestBuilder.OAUTH_APP_NAME, "UTF-8")
                    val authUrl = "https://openrouter.ai/auth?callback_url=$callbackUrl&code_challenge=$codeChallenge&code_challenge_method=S256&name=$encodedAppName"

                    SetupWizardLogger.logPkceEvent("Opening browser", "url=$authUrl")
                    BrowserUtil.browse(authUrl)

                    // Wait for callback with timeout
                    serverSocket.soTimeout = SetupWizardConfig.PKCE_SERVER_TIMEOUT_MS.toInt()

                    try {
                        serverSocket.accept().use { socket ->
                            SetupWizardLogger.logPkceEvent("Connection accepted", "from=${socket.inetAddress}")

                            val code = handleCallback(socket, codeVerifier)
                            if (code != null) {
                                exchangeCode(code, codeVerifier)
                            }
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        SetupWizardLogger.logPkceEvent("Socket timeout")
                        onError("Authentication timed out. Please try again.")
                    } catch (e: Exception) {
                        SetupWizardLogger.logPkceEvent("Error accepting connection", e.message ?: "Unknown error")
                        onError(SetupWizardErrorHandler.handlePkceError(e, "accepting connection"))
                    }

                    SetupWizardLogger.logPkceEvent("Server stopped")
                }
            } catch (e: Exception) {
                SetupWizardLogger.error("PKCE Flow Error", e)
                onError(SetupWizardErrorHandler.handlePkceError(e, "starting server"))
            }
        }
    }

    /**
     * Handle the OAuth callback and extract the authorization code
     */
    private fun handleCallback(socket: Socket, codeVerifier: String): String? {
        return try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val line = reader.readLine() ?: return null

            SetupWizardLogger.logPkceEvent("Request received", line.take(50) + "...")

            if (line.contains("GET /callback")) {
                val code = line.substringAfter("code=").substringBefore(" ").substringBefore("&")
                SetupWizardLogger.logPkceEvent("Auth code extracted", "code=${code.take(5)}...")

                // Send success response
                val writer = PrintWriter(socket.getOutputStream(), true)
                writer.println("HTTP/1.1 200 OK")
                writer.println("Content-Type: text/html")
                writer.println()
                writer.println(
                    "<html><body><h1>Authentication Successful</h1><p>You can close this window and return to IntelliJ IDEA.</p><script>window.close();</script></body></html>"
                )
                writer.close()

                return code
            } else {
                SetupWizardLogger.logPkceEvent("Ignoring non-callback request")
                // Send 404 for other requests (like favicon)
                val writer = PrintWriter(socket.getOutputStream(), true)
                writer.println("HTTP/1.1 404 Not Found")
                writer.close()
                return null
            }
        } catch (e: Exception) {
            SetupWizardLogger.error("Error handling callback", e)
            null
        }
    }

    /**
     * Exchange authorization code for API key
     * Restored to match working implementation from commit 7475e903
     */
    private fun exchangeCode(code: String, codeVerifier: String) {
        ApplicationManager.getApplication().invokeLater {
            onStatusUpdate("Exchanging code...")
        }

        // Use a supervisor scope to ensure this doesn't get cancelled
        // when the local server coroutine finishes
        SetupWizardLogger.logPkceEvent("Starting exchangeCode coroutine")
        coroutineScope.launch(Dispatchers.IO) {
            try {
                SetupWizardLogger.logPkceEvent("Exchanging code for API key...")
                val result = openRouterService.exchangeAuthCode(code, codeVerifier)
                SetupWizardLogger.logPkceEvent("Exchange result: $result")

                ApplicationManager.getApplication().invokeLater({
                    SetupWizardLogger.logPkceEvent("Inside invokeLater")
                    when (result) {
                        is ApiResult.Success -> {
                            val key = result.data.key
                            SetupWizardLogger.logPkceEvent("Key received, updating UI")
                            onSuccess(key)
                        }
                        is ApiResult.Error -> {
                            SetupWizardLogger.logPkceEvent("Exchange failed: ${result.message}")
                            onError(SetupWizardErrorHandler.handleValidationError(result))
                        }
                    }
                }, ModalityState.any())
            } catch (e: Exception) {
                SetupWizardLogger.logPkceEvent("Error in exchangeCode coroutine", e.message ?: "Unknown error")
                ApplicationManager.getApplication().invokeLater({
                    onError(SetupWizardErrorHandler.handleNetworkError(e, "code exchange"))
                }, ModalityState.any())
            }
        }
    }

    /**
     * Cancel the PKCE flow
     */
    fun cancel() {
        SetupWizardLogger.logPkceEvent("Cancelling PKCE flow")
        serverJob?.cancel()
        serverJob = null
    }
}
