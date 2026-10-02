package org.zhavoronkov.openrouter.proxy.servlets

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.models.PresetPair
import org.zhavoronkov.openrouter.presets.PresetCopyService
import org.zhavoronkov.openrouter.proxy.checks.ConsumerRequestChecks
import org.zhavoronkov.openrouter.proxy.defaults.ConfiguredDefaults
import org.zhavoronkov.openrouter.proxy.errors.ClearError
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionRequest
import org.zhavoronkov.openrouter.proxy.pairs.PairAvailability
import org.zhavoronkov.openrouter.proxy.pairs.PairProblem
import org.zhavoronkov.openrouter.proxy.pairs.PresetFields
import org.zhavoronkov.openrouter.proxy.validation.MultimodalContentValidator
import org.zhavoronkov.openrouter.requests.ConsumerNames
import org.zhavoronkov.openrouter.requests.RequestBodies
import org.zhavoronkov.openrouter.requests.RequestLogService
import org.zhavoronkov.openrouter.requests.RequestRecord
import org.zhavoronkov.openrouter.requests.RequestSource
import org.zhavoronkov.openrouter.requests.RequestTrace
import org.zhavoronkov.openrouter.services.FavoriteModelsService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.ErrorPatterns
import org.zhavoronkov.openrouter.utils.ExcludeFromCoverage
import org.zhavoronkov.openrouter.utils.KeyValidator
import org.zhavoronkov.openrouter.utils.ModelAvailabilityNotifier
import org.zhavoronkov.openrouter.utils.ModelSuggestions
import org.zhavoronkov.openrouter.utils.OpenRouterRequestBuilder
import org.zhavoronkov.openrouter.utils.PluginLogger
import org.zhavoronkov.openrouter.utils.applicationServiceOrNull
import org.zhavoronkov.openrouter.utils.asObjectOrNull
import org.zhavoronkov.openrouter.utils.asStringOrNull
import org.zhavoronkov.openrouter.utils.bodyText
import java.io.IOException
import java.io.PrintWriter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Servlet that provides OpenAI-compatible /v1/chat/completions endpoint
 * with full streaming support
 */

@Suppress("MaxLineLength", "ReturnCount", "UnusedPrivateMember")
data class StreamingErrorContext(
    val response: Response,
    val writer: PrintWriter,
    val requestId: String,
    val apiKey: String,
    val jsonBody: String,
    val request: Request,
    val trace: RequestTrace
)

/**
 * Holds both the typed OpenAI request (for validation/logging) and the raw JSON
 * (for field-preserving passthrough to OpenRouter). This ensures unknown fields
 * in the incoming request are forwarded verbatim to OpenRouter without being
 * silently dropped by Gson serialization of the typed model.
 *
 * [requestedModel] is the id the Consumer asked for, which its reply names back; for a pair it
 * differs from [typedRequest]'s model, the model the pair names, which validation and the checks
 * see - the body sent keeps the pair's id. [presetFields] are the fields a pair's preset sets, which
 * the plugin's defaults stay out of; null for a pair whose preset is not known, empty for a model.
 */
data class ParsedChatRequest(
    val typedRequest: OpenAIChatCompletionRequest,
    val rawJson: JsonObject,
    val trace: RequestTrace,
    val requestedModel: String = typedRequest.model,
    val presetFields: Set<String>? = emptySet()
)

/**
 * @param httpClient the client every OpenRouter call goes through.
 * @param settingsServiceProvider resolved on first use, not at construction: the servlet is
 *  instantiated while wiring the proxy server, and an eagerly-resolved application service makes
 *  the class unconstructible anywhere the platform is not up (see `OpenRouterService`, which
 *  already carries the same kind of seam).
 * @param openRouterApiUrl the chat-completions endpoint, threaded into
 *  [NonStreamingResponseHandler] too so both request paths agree on it.
 * @param multimodalValidatorProvider resolved on first use for the same reason as
 *  [settingsServiceProvider] - its own default reaches for an application service.
 *
 * Every parameter defaults to exactly what this class used to build inline, so the no-argument
 * construction the proxy server does is unchanged.
 */
@Suppress("TooManyFunctions")
class ChatCompletionServlet(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    settingsServiceProvider: () -> OpenRouterSettingsService = { OpenRouterSettingsService.getInstance() },
    private val openRouterApiUrl: () -> String = ::defaultChatCompletionsUrl,
    multimodalValidatorProvider: () -> MultimodalContentValidator = { MultimodalContentValidator() },
    /** Records one Requests entry; the log is resolved on each call, not at construction. */
    private val requestRecorder: (RequestRecord) -> Unit = { RequestLogService.getInstance().record(it) },
    /** Finds, later, the provider of a generation whose reply could not say which one served it. */
    private val providerLookup: (generationId: String) -> Unit = ::lookUpProviderLater,
    /** Whether a request arriving now keeps its bodies; read once per request, as it arrives. */
    private val keepBodies: () -> Boolean = {
        applicationServiceOrNull(RequestLogService::class.java)?.keepsBodies == true
    },
    /** Keeps a request's bodies under their id; called once, as the request's record is made. */
    private val bodiesSaver: (id: String, bodies: RequestBodies) -> Unit = { id, bodies ->
        RequestLogService.getInstance().saveBodies(id, bodies)
    },
    private val pairsProvider: () -> PairAvailability = PresetCopyService::pairs,
    /**
     * Waits, briefly, for the read a pair's missing preset asks for, so that one made on
     * OpenRouter since the last read is sent rather than refused. Called on a request thread.
     */
    private val readMissingPreset: (slug: String) -> Unit = ::readMissingPresetFromOpenRouter,
    /**
     * The loaded model catalogue - the selected Data Region's, every output modality - or null
     * while it has not loaded.
     */
    private val catalogueProvider: () -> List<OpenRouterModelInfo>? = {
        applicationServiceOrNull(FavoriteModelsService::class.java)?.getCachedCatalogue()
    }
) : HttpServlet() {

    companion object {

        @ExcludeFromCoverage("asks OpenRouter's generation record over the network, for up to half a minute")
        private fun lookUpProviderLater(generationId: String) =
            RequestLogService.getInstance().fillProviderLater(generationId)

        @ExcludeFromCoverage("reads the presets from OpenRouter over the network, waiting up to five seconds")
        private fun readMissingPresetFromOpenRouter(slug: String) {
            runBlocking { PresetCopyService.getInstance().copy.findAfterRead(slug, MISSING_PRESET_WAIT_MILLIS) }
        }

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        /** How long a request for a pair waits for a read of a preset the copy does not have. */
        private const val MISSING_PRESET_WAIT_MILLIS = 5_000L

        // Request tracking - thread-safe counter using AtomicInteger
        private val requestCounter = AtomicInteger(0)

        // HTTP Client timeouts
        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val READ_TIMEOUT_SECONDS = 120L
        private const val WRITE_TIMEOUT_SECONDS = 30L

        // Request ID formatting
        private const val REQUEST_ID_PAD_WIDTH = 6

        // Streaming request constants
        private const val STREAMING_TIMEOUT_MS = 500L
        private const val STREAMING_CHUNK_DISPLAY_LENGTH = 15

        // HTTP status codes
        private const val HTTP_STATUS_UNAUTHORIZED = 401
        private const val HTTP_STATUS_PAYMENT_REQUIRED = 402
        private const val HTTP_STATUS_NOT_FOUND = 404
        private const val HTTP_STATUS_TOO_MANY_REQUESTS = 429
        private const val HTTP_STATUS_INTERNAL_SERVER_ERROR = 500
        private const val HTTP_STATUS_BAD_GATEWAY = 502
        private const val HTTP_STATUS_SERVICE_UNAVAILABLE = 503
        private const val HTTP_STATUS_CLIENT_ERROR_MIN = 400
        private const val HTTP_STATUS_CLIENT_ERROR_MAX = 499

        // Time conversion
        private const val MILLIS_PER_SECOND = 1000
        private const val NANOS_PER_MILLIS = 1_000_000L
    }

    private val gson = Gson()
    private val settingsService: OpenRouterSettingsService by lazy(settingsServiceProvider)
    private val requestValidator by lazy { RequestValidator(settingsService) }
    private val multimodalValidator by lazy(multimodalValidatorProvider)
    private val streamingHandler = StreamingResponseHandler()
    private val nonStreamingHandler by lazy {
        NonStreamingResponseHandler(httpClient, gson, openRouterApiUrl)
    }

    /**
     * Enum representing different types of multimodal content errors
     */
    private enum class MultimodalErrorType {
        IMAGE, AUDIO, VIDEO, FILE;

        fun createErrorMessage(): String = when (this) {
            IMAGE -> createImageInputNotSupportedMessage()
            AUDIO -> createAudioInputNotSupportedMessage()
            VIDEO -> createVideoInputNotSupportedMessage()
            FILE -> createFileInputNotSupportedMessage()
        }

        companion object {
            private fun createImageInputNotSupportedMessage(): String = buildString {
                append("This model doesn't support image input.\n\n")
                append(
                    ModelSuggestions.createSuggestionSection(
                        "Try a vision-capable model like:",
                        ModelSuggestions.VISION_MODELS
                    )
                )
                append("\n\nCheck model capabilities: https://openrouter.ai/models")
            }

            private fun createAudioInputNotSupportedMessage(): String = buildString {
                append("This model doesn't support audio input.\n\n")
                append(
                    ModelSuggestions.createSuggestionSection(
                        "Try an audio-capable model like:",
                        ModelSuggestions.AUDIO_MODELS
                    )
                )
                append("\n\nCheck model capabilities: https://openrouter.ai/models")
            }

            private fun createVideoInputNotSupportedMessage(): String = buildString {
                append("This model doesn't support video input.\n\n")
                append(
                    ModelSuggestions.createSuggestionSection(
                        "Try a video-capable model like:",
                        ModelSuggestions.VIDEO_MODELS
                    )
                )
                append("\n\nCheck model capabilities: https://openrouter.ai/models")
            }

            private fun createFileInputNotSupportedMessage(): String = buildString {
                append("This model doesn't support PDF/file input.\n\n")
                append(
                    ModelSuggestions.createSuggestionSection(
                        "Try a document-capable model like:",
                        ModelSuggestions.FILE_MODELS
                    )
                )
                append("\n\nCheck model capabilities: https://openrouter.ai/models")
            }
        }
    }

    /**
     * Detect multimodal error type from error body
     */
    private fun detectMultimodalErrorType(errorBody: String): MultimodalErrorType? {
        return when {
            ErrorPatterns.isImageNotSupported(errorBody) -> MultimodalErrorType.IMAGE
            ErrorPatterns.isAudioNotSupported(errorBody) -> MultimodalErrorType.AUDIO
            ErrorPatterns.isVideoNotSupported(errorBody) -> MultimodalErrorType.VIDEO
            ErrorPatterns.isFileNotSupported(errorBody) -> MultimodalErrorType.FILE
            else -> null
        }
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val requestNumber = requestCounter.incrementAndGet()
        val requestId = requestNumber.toString().padStart(REQUEST_ID_PAD_WIDTH, '0')
        val startNs = System.nanoTime()

        logRequestBoundary(requestId, isStart = true, requestNumber = requestNumber)

        // Every request becomes one Requests entry, however handling ends: `finish` in the finally
        // emits it once, with whatever the reply or the first failure reported.
        val trace = RequestTrace(
            source = RequestSource.PROXY,
            sender = ConsumerNames.fromUserAgent(req.getHeader("User-Agent")),
            record = { unlessLogFails(Unit, "record the request") { requestRecorder(it) } },
            lookUpProvider = { unlessLogFails(Unit, "look up the provider") { providerLookup(it) } },
            keepBodies = unlessLogFails(false, "read whether to keep request bodies") { keepBodies() },
            saveBodies = { id, bodies ->
                unlessLogFails(Unit, "keep the request's bodies") { bodiesSaver(id, bodies) }
            }
        )
        try {
            processRequest(req, resp, requestId, startNs, trace)
        } catch (e: IOException) {
            trace.fail("Network error: ${e.message}")
            handleException(e, resp, requestId)
        } catch (e: IllegalArgumentException) {
            trace.fail("Invalid request: ${e.message}")
            handleException(e, resp, requestId)
        } catch (e: IllegalStateException) {
            // A body that does not parse is answered inside processRequest, and an unreadable reply
            // by the response handlers, so no JsonSyntaxException gets this far
            trace.fail("Internal error: ${e.message}")
            handleException(e, resp, requestId)
        } finally {
            trace.finish()
            val durationMs = (System.nanoTime() - startNs) / NANOS_PER_MILLIS
            logRequestBoundary(requestId, isStart = false, durationMs = durationMs)
        }
    }

    /**
     * Log request start/completion with optional duration
     */
    private fun logRequestBoundary(
        requestId: String,
        isStart: Boolean,
        requestNumber: Int = 0,
        durationMs: Long = 0
    ) {
        PluginLogger.Service.info("═══════════════════════════════════════════════════════")
        if (isStart) {
            val timestamp = System.currentTimeMillis()
            PluginLogger.Service.info("[Chat-$requestId] NEW CHAT COMPLETION REQUEST RECEIVED")
            PluginLogger.Service.info("[Chat-$requestId] Timestamp: $timestamp")
            PluginLogger.Service.info("[Chat-$requestId] Total chat requests so far: $requestNumber")
        } else {
            PluginLogger.Service.info("[Chat-$requestId] REQUEST COMPLETE (${durationMs}ms)")
        }
        PluginLogger.Service.info("═══════════════════════════════════════════════════════")
    }

    /**
     * Runs [block], one of the Requests log's steps, and answers [fallback] if it throws: a log
     * that cannot be written must not fail the request it describes.
     */
    private inline fun <T> unlessLogFails(fallback: T, what: String, block: () -> T): T = try {
        block()
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        PluginLogger.Service.debug("Could not $what: ${e.message}")
        fallback
    }

    /**
     * Process the chat completion request
     */
    private fun processRequest(
        req: HttpServletRequest,
        resp: HttpServletResponse,
        requestId: String,
        startNs: Long,
        trace: RequestTrace
    ) {
        logRequestDiagnostics(req, requestId)

        val requestBody = req.reader.readText()
        trace.received(requestBody)
        checkForDuplicateRequest(requestBody, req, requestId)

        val apiKey = validateAndGetApiKey(resp, requestId)
            ?: return trace.fail("API key not configured")
        val body = parseRequestBody(requestBody, resp, requestId, trace)
            ?: return trace.fail("Invalid request body")
        trace.requestedModel(body.typedRequest.model)
        // Before validation, so every later step - validation, the defaults, logging - sees the
        // real model rather than the pair
        val parsed = applyPreset(body, resp, requestId, trace) ?: return
        consumerRequestProblem(parsed)?.let { return refuse(resp, requestId, trace, it, body.typedRequest.model) }
        val openAIRequest = parsed.typedRequest
        PluginLogger.Service.info("[Chat-$requestId] 📝 Model: '${openAIRequest.model}'")

        // Pre-validate multimodal content against model capabilities
        val validationResult = multimodalValidator.validate(openAIRequest, requestId)
        if (validationResult is MultimodalContentValidator.ValidationResult.Invalid) {
            val input = validationResult.contentType.displayName
            PluginLogger.Service.warn(
                "[Chat-$requestId] ⚠️ Pre-validation failed: $input not supported by model " +
                    "'${validationResult.modelId}'"
            )
            trace.fail("$input input is not supported by ${validationResult.modelId}")
            sendMultimodalValidationError(resp, validationResult, requestId)
        } else {
            routeRequest(resp, parsed, apiKey, requestId, startNs)
        }
    }

    /**
     * [parsed] for a pair: refused when it cannot be sent with its preset, else with the
     * Consumer's own fields the preset sets removed, so the preset wins. The body keeps the pair's
     * id - OpenRouter applies the preset - while every later step, validation and the checks, sees
     * the model it names. [parsed] as it is for a plain model; null means the refusal has been answered.
     */
    private fun applyPreset(
        parsed: ParsedChatRequest,
        resp: HttpServletResponse,
        requestId: String,
        trace: RequestTrace
    ): ParsedChatRequest? {
        val pair = PresetPair.parse(parsed.typedRequest.model) ?: return parsed
        var pairs = pairsProvider()
        if (pairs.problem(pair) is PairProblem.MissingPreset) {
            readMissingPreset(pair.preset)
            pairs = pairsProvider()
        }
        pairs.problem(pair)?.let {
            refuse(resp, requestId, trace, ClearError.of(it), pair.id)
            return null
        }
        val config = pairs.presetConfig(pair)
        val removed = config?.let { PresetFields.strip(parsed.rawJson, it) }.orEmpty()
        trace.preset(pair.preset, removed)
        if (removed.isNotEmpty()) {
            PluginLogger.Service.info(
                "[Chat-$requestId] '${pair.id}': removed ${removed.joinToString()} set by its preset"
            )
        }
        return parsed.copy(
            typedRequest = parsed.typedRequest.copy(model = pair.model),
            presetFields = config?.keySet()?.toSet()
        )
    }

    /**
     * Check for duplicate requests
     */
    private fun checkForDuplicateRequest(requestBody: String, req: HttpServletRequest, requestId: String) {
        requestValidator.checkForDuplicateRequest(requestBody, req, requestId)
    }

    /**
     * Validate and get API key from settings
     */
    private fun validateAndGetApiKey(resp: HttpServletResponse, requestId: String): String? {
        return requestValidator.validateAndGetApiKey(resp, requestId)
    }

    /**
     * Route request to streaming or non-streaming handler
     */
    private fun routeRequest(
        resp: HttpServletResponse,
        parsed: ParsedChatRequest,
        apiKey: String,
        requestId: String,
        startNs: Long
    ) {
        val openAIRequest = parsed.typedRequest
        if (openAIRequest.stream == true) {
            PluginLogger.Service.info("[Chat-$requestId] 🌊 STREAMING requested - handling SSE response")
            handleStreamingRequest(resp, parsed, apiKey, requestId)
        } else {
            PluginLogger.Service.info("[Chat-$requestId] 📦 NON-STREAMING request - handling standard response")
            handleNonStreamingRequest(resp, parsed, apiKey, requestId, startNs)
        }
    }

    /**
     * Handle timeout exception
     */
    private fun handleException(
        e: Exception,
        resp: HttpServletResponse,
        requestId: String
    ) {
        when (e) {
            is java.io.IOException -> {
                val msg = "[Chat-$requestId] IO error during chat completion: ${e.message}"
                PluginLogger.Service.error(msg, e)
                val errMsg = "Network error: ${e.message}"
                sendErrorResponse(resp, errMsg, HttpServletResponse.SC_SERVICE_UNAVAILABLE)
            }
            is IllegalArgumentException -> {
                val msg = "[Chat-$requestId] Invalid argument in chat completion: ${e.message}"
                PluginLogger.Service.error(msg, e)
                val errMsg = "Invalid request: ${e.message}"
                sendErrorResponse(resp, errMsg, HttpServletResponse.SC_BAD_REQUEST)
            }
            // The only other one doPost hands here is an IllegalStateException
            else -> {
                val msg = "[Chat-$requestId] Runtime error during chat completion: ${e.message}"
                PluginLogger.Service.error(msg, e)
                val errMsg = "Internal server error: ${e.message}"
                sendErrorResponse(resp, errMsg, HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
            }
        }
    }

    /**
     * Handles streaming requests using Server-Sent Events (SSE)
     */
    private fun handleStreamingRequest(
        resp: HttpServletResponse,
        parsed: ParsedChatRequest,
        apiKey: String,
        requestId: String
    ) {
        PluginLogger.Service.info("[Chat-$requestId] Setting up SSE stream...")

        // Set up Server-Sent Events headers
        resp.contentType = "text/event-stream"
        resp.characterEncoding = "UTF-8"
        resp.setHeader("Cache-Control", "no-cache")
        resp.setHeader("Connection", "keep-alive")
        resp.setHeader("X-Accel-Buffering", "no") // Disable nginx buffering
        resp.status = HttpServletResponse.SC_OK

        val writer = resp.writer
        writer.flush() // Flush headers immediately

        try {
            val jsonBody = prepareRequest(parsed, requestId, isStreaming = true)
            val request = buildOpenRouterRequest(jsonBody, apiKey)
            executeStreamingRequest(request, writer, requestId, apiKey, jsonBody, parsed)
        } catch (e: IOException) {
            parsed.trace.fail("Network error: ${e.message}")
            handleStreamingError(e, writer, requestId)
        } catch (e: JsonSyntaxException) {
            parsed.trace.fail("Invalid response: ${e.message}")
            handleStreamingError(e, writer, requestId)
        } catch (e: IllegalStateException) {
            parsed.trace.fail("Internal error: ${e.message}")
            handleStreamingError(e, writer, requestId)
        } catch (e: IllegalArgumentException) {
            parsed.trace.fail("Invalid request: ${e.message}")
            handleStreamingError(e, writer, requestId)
        }
    }

    private fun configuredDefaults() = ConfiguredDefaults.Settings(
        defaultMaxTokens = settingsService.uiPreferencesManager.defaultMaxTokens,
        routing = settingsService.providerRoutingManager,
        routerDefaults = settingsService.routerDefaultsManager,
        webSearch = settingsService.webSearchManager.current(),
        schemas = settingsService.outputSchemasManager.all()
    )

    /**
     * Prepare the request body for OpenRouter by serializing the raw JSON.
     * This preserves all fields from the original request — including OpenRouter-specific
     * parameters (provider, models[], route, transforms, response_format, plugins, preset,
     * usage, etc.) that the typed model doesn't declare. Only applies configured defaults
     * for temperature/max_tokens when not already present in the request.
     */
    private fun prepareRequest(
        parsed: ParsedChatRequest,
        requestId: String,
        isStreaming: Boolean
    ): String {
        // Apply configured defaults only when not already present in the request
        ConfiguredDefaults.apply(parsed.rawJson, ::configuredDefaults, gson, requestId, parsed.presetFields)
        // Asked only of a request that names a preset
        parsed.trace.sent(parsed.rawJson) { slug -> pairsProvider().presetConfig(slug) }

        val jsonBody = gson.toJson(parsed.rawJson)
        val bodyPreview = jsonBody.take(STREAMING_TIMEOUT_MS.toInt())
        val mode = if (isStreaming) "Streaming" else "Non-streaming"
        PluginLogger.Service.debug("[Chat-$requestId] $mode request body (passthrough): $bodyPreview...")
        return jsonBody
    }

    /**
     * Log OpenRouter-specific response metadata headers at debug level.
     * These include routing decisions, model used, generation ID, etc.
     */
    private fun logOpenRouterMetadata(response: Response, requestId: String) {
        val metadataHeaders = response.headers.names()
            .filter(::isOpenRouterMetadataHeader)

        if (metadataHeaders.isNotEmpty()) {
            val headerSummary = metadataHeaders.joinToString(", ") { name ->
                "$name=${response.header(name)}"
            }
            PluginLogger.Service.debug("[Chat-$requestId] OpenRouter metadata: $headerSummary")
        }
    }

    /**
     * Build the HTTP request to OpenRouter
     */
    private fun buildOpenRouterRequest(jsonBody: String, apiKey: String): Request {
        return OpenRouterRequestBuilder.buildPostRequest(
            url = openRouterApiUrl(),
            jsonBody = jsonBody,
            authType = OpenRouterRequestBuilder.AuthType.API_KEY,
            authToken = apiKey
        )
    }

    /**
     * Execute the streaming request and handle the response
     */
    private fun executeStreamingRequest(
        request: Request,
        writer: PrintWriter,
        requestId: String,
        apiKey: String,
        jsonBody: String,
        parsed: ParsedChatRequest
    ) {
        val trace = parsed.trace
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorContext = StreamingErrorContext(response, writer, requestId, apiKey, jsonBody, request, trace)
                handleStreamingErrorResponse(errorContext)
                return
            }

            logOpenRouterMetadata(response, requestId)
            PluginLogger.Service.info("[Chat-$requestId] Streaming response from OpenRouter...")
            // A pair's stream names the pair, as its whole reply does
            val shownModel = parsed.requestedModel.takeIf { it != parsed.typedRequest.model }
            streamingHandler.streamResponseToClient(response, writer, requestId, trace, shownModel)
        }
    }

    /**
     * Handle error response from OpenRouter
     * Sends error as OpenAI-compatible streaming chunk so AI Assistant can display it properly
     */
    private fun handleStreamingErrorResponse(context: StreamingErrorContext) {
        val errorBody = context.response.bodyText()

        // Log each piece of information separately to avoid IntelliJ logger truncation
        val keyDisplay = KeyValidator.maskApiKey(context.apiKey)
        val keyLength = context.apiKey.length
        val statusCode = context.response.code
        val requestId = context.requestId

        // Use warn() for expected API errors (404, 429, etc.) to avoid stack traces
        // Use error() only for unexpected errors (500, network failures, etc.)
        val isClientError = statusCode in HTTP_STATUS_CLIENT_ERROR_MIN..HTTP_STATUS_CLIENT_ERROR_MAX
        if (isClientError) {
            PluginLogger.Service.warn("[Chat-$requestId] ❌ OpenRouter API Error: $statusCode")
        } else {
            PluginLogger.Service.warn("[Chat-$requestId] ❌ OpenRouter API Error: $statusCode (server error)")
        }

        // Log error body separately so it's always visible for debugging
        PluginLogger.Service.warn("[Chat-$requestId] ❌ Error response body: $errorBody")
        PluginLogger.Service.debug("[Chat-$requestId] Request URL: ${context.request.url}")
        PluginLogger.Service.debug("[Chat-$requestId] API key: $keyDisplay (length: $keyLength)")

        val bodyPreview = context.jsonBody.take(STREAMING_TIMEOUT_MS.toInt())
        PluginLogger.Service.debug("[Chat-$requestId] ❌ Full request body: $bodyPreview")

        // Create user-friendly error message
        val userFriendlyMessage = createUserFriendlyErrorMessage(errorBody, context.response.code)
        context.trace.fail(userFriendlyMessage)

        // Send error as OpenAI-compatible streaming chunk
        // This ensures AI Assistant can parse and display the error properly
        sendOpenAICompatibleErrorChunk(context.writer, userFriendlyMessage, context.requestId)
    }

    /**
     * Sends an error message as an OpenAI-compatible streaming chunk
     * This format is required for AI Assistant to properly parse and display errors
     */
    private fun sendOpenAICompatibleErrorChunk(writer: PrintWriter, message: String, requestId: String) {
        val escapedMessage = message.replace("\"", "\\\"").replace("\n", "\\n")
        val chunkId = "chatcmpl-error-$requestId"
        val timestamp = System.currentTimeMillis() / MILLIS_PER_SECOND

        // Create a valid OpenAI streaming chunk with the error message in the content
        val errorChunk = buildString {
            append("{\"id\":\"$chunkId\",")
            append("\"object\":\"chat.completion.chunk\",")
            append("\"created\":$timestamp,")
            append("\"model\":\"error\",")
            append("\"choices\":[{")
            append("\"index\":0,")
            append("\"delta\":{\"role\":\"assistant\",\"content\":\"$escapedMessage\"},")
            append("\"finish_reason\":\"stop\"")
            append("}]}")
        }

        writer.println("data: $errorChunk")
        writer.println()
        writer.println("data: [DONE]")
        writer.println()
        writer.flush()
    }

    /**
     * Create a user-friendly error message based on the error response
     * Handles all multimodal content type errors (image, audio, video, PDF/file)
     */
    @Suppress("ReturnCount")
    private fun createUserFriendlyErrorMessage(errorBody: String, statusCode: Int): String {
        // First, try to extract the message from JSON error body
        val extractedMessage = try {
            // Null for an empty or blank body, and for an `error` or `message` of another shape
            gson.fromJson(errorBody, JsonObject::class.java)
                ?.get("error")?.asObjectOrNull()?.get("message")?.asStringOrNull()
        } catch (e: JsonSyntaxException) {
            PluginLogger.Service.warn("Failed to parse error response", e)
            null
        }

        // Check for "No endpoints found" pattern - can occur with 404 or 500 status codes
        // OpenRouter may return different status codes for model availability issues
        if (errorBody.contains("No endpoints found", ignoreCase = true)) {
            return handleNoEndpointsFoundError(errorBody)
        }

        // Check for free tier ended / migrate to paid errors (typically 404)
        val freeTierError = handleFreeTierEndedError(errorBody)
        if (freeTierError != null) {
            return freeTierError
        }

        // Check for multimodal content type errors (typically 404)
        if (statusCode == HTTP_STATUS_NOT_FOUND) {
            val multimodalError = handleMultimodalContentTypeError(errorBody)
            if (multimodalError != null) {
                return multimodalError
            }
        }

        // Handle specific HTTP status codes with user-friendly messages
        return when (statusCode) {
            HTTP_STATUS_UNAUTHORIZED -> {
                if (extractedMessage != null) {
                    "Authentication failed: $extractedMessage"
                } else {
                    "Authentication failed. Please check your API key."
                }
            }
            HTTP_STATUS_PAYMENT_REQUIRED -> {
                if (extractedMessage != null) {
                    "Insufficient credits: $extractedMessage"
                } else {
                    "Insufficient credits. Please add credits to your OpenRouter account."
                }
            }
            HTTP_STATUS_TOO_MANY_REQUESTS -> createRateLimitMessage(extractedMessage)
            HTTP_STATUS_INTERNAL_SERVER_ERROR,
            HTTP_STATUS_BAD_GATEWAY,
            HTTP_STATUS_SERVICE_UNAVAILABLE -> createServerErrorMessage(extractedMessage, statusCode)
            else -> extractedMessage ?: "Request failed (HTTP $statusCode). Please try again."
        }
    }

    /**
     * Handle "free period ended" / "migrate to paid" errors
     * Returns null if no such error pattern is detected
     */
    private fun handleFreeTierEndedError(errorBody: String): String? {
        // Check for free period ended pattern using ErrorPatterns
        if (ErrorPatterns.isFreeTierEnded(errorBody)) {
            // Extract model name from error message
            val modelNameRegex = """migrate to the paid slug[:\s]+([^\s"]+)""".toRegex(RegexOption.IGNORE_CASE)
            val paidSlug = modelNameRegex.find(errorBody)?.groupValues?.get(1)

            // Show notification about the model change
            val modelName = paidSlug ?: "the requested model"
            ModelAvailabilityNotifier.notifyModelUnavailable(modelName, errorBody)

            return createFreeTierEndedMessage(paidSlug)
        }
        return null
    }

    /**
     * Create user-friendly message for free tier ended errors
     */
    private fun createFreeTierEndedMessage(paidSlug: String?): String = buildString {
        append("⚠️ **Free Tier Ended**\n\n")
        append("The free period for this model has ended.\n\n")
        if (paidSlug != null) {
            append("To continue using this model, switch to the paid version: `$paidSlug`\n\n")
        }
        append("**Alternatives:**\n")
        append("• Select a different model in OpenRouter settings\n")
        append("• Use another free model if available\n")
        append("• Add credits to your OpenRouter account for paid models")
    }

    /**
     * Handle "No endpoints found" errors - model unavailable or doesn't support requested features
     */
    private fun handleNoEndpointsFoundError(errorBody: String): String {
        // Check for specific capability errors first using centralized detection
        // Note: These are NOT model unavailability issues - the model is available but doesn't support the feature
        // So we don't show "Model Unavailable" notification for these cases
        detectMultimodalErrorType(errorBody)?.let { return it.createErrorMessage() }

        // Generic "No endpoints found for <model>" error - this IS a true model unavailability issue
        val modelNameRegex = """No endpoints found for ([^.]+)""".toRegex()
        val modelName = modelNameRegex.find(errorBody)?.groupValues?.get(1) ?: "the requested model"
        ModelAvailabilityNotifier.notifyModelUnavailable(modelName, errorBody)
        return createModelUnavailableMessage(modelName)
    }

    /**
     * Handle multimodal content type errors (image, audio, video, PDF/file not supported)
     * Returns null if no multimodal error pattern is detected
     */
    private fun handleMultimodalContentTypeError(errorBody: String): String? {
        // Note: These are NOT model unavailability issues - the model is available but doesn't support the feature
        // So we don't show "Model Unavailable" notification for these cases
        return detectMultimodalErrorType(errorBody)?.createErrorMessage()
    }

    /**
     * Create a user-friendly message for server errors (HTTP 500+)
     */
    private fun createServerErrorMessage(extractedMessage: String?, statusCode: Int): String = buildString {
        append("OpenRouter server error (HTTP $statusCode).\n\n")
        if (extractedMessage != null) {
            append("Details: $extractedMessage\n\n")
        }
        append("This is usually a temporary issue. Please try again in a moment.\n")
        append("If the problem persists, check OpenRouter status: https://status.openrouter.ai")
    }

    private fun createModelUnavailableMessage(modelName: String): String = buildString {
        append("Model Unavailable: $modelName\n\n")
        append(
            ModelSuggestions.createSuggestionSection(
                "This model is currently unavailable. Try:",
                ModelSuggestions.GENERAL_MODELS
            )
        )
        append("\n\nCheck model status: https://openrouter.ai/models")
    }

    private fun createRateLimitMessage(extractedMessage: String?): String = buildString {
        append("Rate limit exceeded. Please wait a moment and try again.\n\n")
        if (extractedMessage != null && extractedMessage.contains(ErrorPatterns.FREE, ignoreCase = true)) {
            append("Tip: Free tier models have lower rate limits. ")
            append("Consider using a paid model for higher limits.")
        }
    }

    private fun handleStreamingError(e: Exception, writer: PrintWriter, requestId: String) {
        streamingHandler.handleStreamingError(e, writer, requestId)
    }

    /**
     * Handles non-streaming requests (standard JSON response)
     */
    private fun handleNonStreamingRequest(
        resp: HttpServletResponse,
        parsed: ParsedChatRequest,
        apiKey: String,
        requestId: String,
        startNs: Long
    ) {
        val requestBody = prepareRequest(parsed, requestId, isStreaming = false)
        nonStreamingHandler.handleNonStreamingRequest(
            resp = resp,
            requestBody = requestBody,
            apiKey = apiKey,
            originalModel = parsed.requestedModel,
            requestId = requestId,
            startNs = startNs,
            trace = parsed.trace
        )
    }

    private fun logRequestDiagnostics(req: HttpServletRequest, requestId: String) {
        val requestURI = req.requestURI
        val servletPath = req.servletPath
        val pathInfo = req.pathInfo
        val method = req.method
        val contentType = req.contentType
        val contentLength = req.contentLength

        PluginLogger.Service.info(
            "[Chat-$requestId] Incoming $method $requestURI (servletPath=$servletPath, pathInfo=$pathInfo)"
        )
        PluginLogger.Service.debug("[Chat-$requestId] Content-Type=$contentType, Content-Length=$contentLength")

        val headers = req.headerNames.asSequence().associateWith { name ->
            val value = req.getHeader(name)
            if (name.equals("Authorization", ignoreCase = true)) {
                value?.let { it.take(STREAMING_CHUNK_DISPLAY_LENGTH) + "…(redacted)" }
            } else {
                value
            }
        }
        PluginLogger.Service.debug("[Chat-$requestId] Headers: $headers")
    }

    /**
     * Send error response for multimodal validation failure.
     * Returns a user-friendly error message explaining the capability mismatch.
     */
    private fun sendMultimodalValidationError(
        resp: HttpServletResponse,
        validationResult: MultimodalContentValidator.ValidationResult.Invalid,
        requestId: String
    ) {
        resp.contentType = "application/json"
        resp.status = HttpServletResponse.SC_BAD_REQUEST

        val errorResponse = mapOf(
            "error" to mapOf(
                "message" to validationResult.errorMessage,
                "type" to "invalid_request_error",
                "code" to "model_capability_error",
                "param" to "model"
            )
        )

        PluginLogger.Service.info(
            "[Chat-$requestId] Returning pre-validation error for ${validationResult.contentType.displayName}"
        )
        resp.writer.write(gson.toJson(errorResponse))
    }

    /**
     * What the proxy can tell is wrong with the request before sending it, or null. Asked of the
     * request as it will be sent - a pair already expanded to its model.
     */
    private fun consumerRequestProblem(parsed: ParsedChatRequest): ClearError? = ConsumerRequestChecks.problem(
        body = parsed.rawJson,
        model = parsed.typedRequest.model,
        catalogue = catalogueProvider(),
        region = settingsService::getDataRegion,
        schemas = { settingsService.outputSchemasManager.all() }
    )

    /**
     * Refuses the request with [error], before anything was sent - streaming or not, no SSE is
     * written - and records it as the request's error.
     */
    private fun refuse(
        resp: HttpServletResponse,
        requestId: String,
        trace: RequestTrace,
        error: ClearError,
        requested: String
    ) {
        PluginLogger.Service.warn("[Chat-$requestId] Refused '$requested': ${error.message}")
        trace.refuse(error.prefixedMessage, error.fixAt)
        resp.contentType = "application/json"
        resp.status = HttpServletResponse.SC_BAD_REQUEST
        resp.writer.write(gson.toJson(error.body()))
    }

    private fun sendErrorResponse(resp: HttpServletResponse, message: String, statusCode: Int) {
        resp.contentType = "application/json"
        resp.status = statusCode
        val errorResponse = mapOf(
            "error" to mapOf(
                "message" to message,
                "type" to "invalid_request_error",
                "code" to statusCode
            )
        )
        resp.writer.write(gson.toJson(errorResponse))
    }

    /**
     * Parse request body from string instead of reading from request again
     */
    @Suppress("ReturnCount") // Early-return guard clauses (invalid JSON, empty messages) read
    // more clearly than nested conditionals; each return maps to a distinct 400 response.
    private fun parseRequestBody(
        requestBody: String,
        resp: HttpServletResponse,
        requestId: String,
        trace: RequestTrace
    ): ParsedChatRequest? {
        return try {
            // Parse into JsonObject first so we can preserve all fields verbatim for
            // outbound passthrough. Then deserialize the same JsonObject into the typed
            // model for validation/logging/multimodal checks.
            // A body that is not a JSON object - `null`, an array, a bare string or number -
            // makes Gson throw, but an empty or blank one makes it answer null instead. Both
            // end in the catch below, as the same 400.
            val rawJson = gson.fromJson(requestBody, JsonObject::class.java)
                ?: throw JsonSyntaxException("Request body is empty")
            val openAIRequest = gson.fromJson(rawJson, OpenAIChatCompletionRequest::class.java)

            if (openAIRequest.messages.isEmpty()) {
                PluginLogger.Service.error(
                    "[Chat-$requestId] Request validation failed: messages cannot be null or empty"
                )
                sendErrorResponse(resp, "Messages cannot be null or empty", HttpServletResponse.SC_BAD_REQUEST)
                return null
            }

            val msgCount = openAIRequest.messages.size
            val streamStr = openAIRequest.stream
            val model = openAIRequest.model
            PluginLogger.Service.info(
                "[Chat-$requestId] Processing request for model: $model with $msgCount messages, stream=$streamStr"
            )
            ParsedChatRequest(typedRequest = openAIRequest, rawJson = rawJson, trace = trace)
        } catch (e: JsonSyntaxException) {
            PluginLogger.Service.error("[Chat-$requestId] Failed to parse request JSON: ${e.message}", e)
            sendErrorResponse(resp, "Invalid JSON format", HttpServletResponse.SC_BAD_REQUEST)
            null
        }
    }
}
