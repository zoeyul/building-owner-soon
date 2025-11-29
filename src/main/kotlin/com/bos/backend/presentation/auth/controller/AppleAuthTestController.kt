package com.bos.backend.presentation.auth.controller

import com.bos.backend.infrastructure.external.AppleWebAuthService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Apple Sign In 테스트용 컨트롤러
 * 웹에서 Apple 로그인을 통해 Identity Token을 얻기 위해 사용합니다.
 * 프로덕션에서는 비활성화하거나 제거해야 합니다.
 *
 * 주의: 웹 OAuth 플로우를 사용하려면 Apple Developer에서
 * "Sign in with Apple for Web" Service ID가 설정되어 있어야 합니다.
 */
@RestController
@RequestMapping("/oauth/apple")
@Suppress("TooManyFunctions")
class AppleAuthTestController(
    private val appleWebAuthService: AppleWebAuthService,
    @Value("\${apple.client-id:}") private val clientId: String,
    @Value("\${apple.redirect-uri:https://api.buildingownersoon.app/oauth/apple}") private val redirectUri: String,
) {
    private val logger = LoggerFactory.getLogger(AppleAuthTestController::class.java)

    companion object {
        private const val TOKEN_PREVIEW_LENGTH = 50
        private const val JWT_PARTS_COUNT = 3
    }

    /**
     * Apple 로그인 페이지로 리다이렉트
     * 브라우저에서 /oauth/apple/login 접속 시 Apple 로그인 화면으로 이동
     */
    @GetMapping("/login")
    fun login(exchange: ServerWebExchange): Mono<Void> {
        if (clientId.isEmpty()) {
            exchange.response.statusCode = HttpStatus.SERVICE_UNAVAILABLE
            return exchange.response.setComplete()
        }

        val authUrl = buildAppleAuthUrl()
        logger.info("Redirecting to Apple auth URL with client_id: $clientId")

        exchange.response.statusCode = HttpStatus.FOUND
        exchange.response.headers.location = URI.create(authUrl)
        return exchange.response.setComplete()
    }

    /**
     * Apple OAuth 콜백 (POST)
     * Apple은 response_mode=form_post 설정 시 POST로 데이터를 전달합니다.
     * response_type=code id_token 설정 시 id_token이 직접 전달됩니다.
     */
    @PostMapping("", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    @Suppress("TooGenericExceptionCaught")
    suspend fun callbackPost(exchange: ServerWebExchange): AppleCallbackResponse {
        val formData = exchange.formData.awaitSingle()
        val callbackData =
            AppleCallbackData(
                idToken = formData.getFirst("id_token"),
                code = formData.getFirst("code"),
                state = formData.getFirst("state"),
                error = formData.getFirst("error"),
                user = formData.getFirst("user"),
            )
        logCallback(callbackData)
        return processCallback(callbackData)
    }

    private fun logCallback(data: AppleCallbackData) {
        logger.info(
            "Apple callback received - " +
                "id_token: ${data.idToken?.take(TOKEN_PREVIEW_LENGTH)}..., " +
                "code: ${data.code?.take(TOKEN_PREVIEW_LENGTH)}..., " +
                "state: ${data.state}, error: ${data.error}",
        )
    }

    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    private suspend fun processCallback(data: AppleCallbackData): AppleCallbackResponse {
        if (data.error != null) {
            return createErrorResponse(data.error)
        }
        if (data.idToken != null) {
            return createSuccessResponseWithToken(data)
        }
        if (data.code != null && appleWebAuthService.isConfigured()) {
            return exchangeAuthorizationCode(data)
        }
        return createMissingTokenResponse(data.code)
    }

    private fun createErrorResponse(error: String) =
        AppleCallbackResponse(
            success = false,
            error = error,
            message = "Apple 로그인이 취소되었거나 오류가 발생했습니다.",
        )

    private fun createSuccessResponseWithToken(data: AppleCallbackData): AppleCallbackResponse {
        val tokenInfo = data.idToken?.let { decodeIdentityToken(it) }
        return AppleCallbackResponse(
            success = true,
            message =
                "Apple 인증 성공! 아래 Identity Token을 확인하세요. " +
                    "(주의: 웹용 토큰은 aud가 다르므로 iOS sign-in API에 직접 사용 불가)",
            identityToken = data.idToken,
            authorizationCode = data.code,
            userDataFromApple = data.user,
            tokenInfo = tokenInfo,
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun exchangeAuthorizationCode(data: AppleCallbackData): AppleCallbackResponse =
        try {
            val tokenResponse = appleWebAuthService.verifyAuthorizationCode(data.code!!)
            val tokenInfo = decodeIdentityToken(tokenResponse.idToken)
            AppleCallbackResponse(
                success = true,
                message =
                    "Authorization Code Exchange 성공! Identity Token을 확인하세요. " +
                        "(주의: 웹용 토큰은 aud가 다르므로 iOS sign-in API에 직접 사용 불가)",
                identityToken = tokenResponse.idToken,
                authorizationCode = data.code,
                userDataFromApple = data.user,
                tokenInfo = tokenInfo,
            )
        } catch (e: Exception) {
            logger.error("Failed to exchange authorization code: ${e.message}", e)
            AppleCallbackResponse(
                success = false,
                error = "token_exchange_failed",
                message = "Authorization Code Exchange 실패: ${e.message}",
                authorizationCode = data.code,
            )
        }

    private fun createMissingTokenResponse(code: String?) =
        AppleCallbackResponse(
            success = false,
            error = "missing_id_token",
            message = "Identity Token이 없습니다. Apple Developer에서 웹 Service ID 설정을 확인하세요.",
            authorizationCode = code,
        )

    /**
     * Apple OAuth 콜백 (GET) - 일부 설정에서는 GET으로 올 수 있음
     */
    @GetMapping("/callback")
    suspend fun callbackGet(
        @RequestParam("id_token", required = false) idToken: String?,
        @RequestParam("code", required = false) code: String?,
        @RequestParam("error", required = false) error: String?,
    ): AppleCallbackResponse {
        logger.info(
            "Apple callback (GET) received - " +
                "id_token: ${idToken?.take(TOKEN_PREVIEW_LENGTH)}..., " +
                "code: ${code?.take(TOKEN_PREVIEW_LENGTH)}..., error: $error",
        )

        if (idToken == null && code == null) {
            return AppleCallbackResponse(
                success = false,
                error = error ?: "missing_token",
                message =
                    "Identity Token 또는 Authorization Code가 없습니다. " +
                        "Apple은 POST로 callback을 보내므로 response_mode=form_post 설정을 확인하세요.",
            )
        }

        return AppleCallbackResponse(
            success = true,
            message =
                if (idToken != null) {
                    "Identity Token을 받았습니다. (주의: 웹용 토큰은 aud가 다르므로 iOS sign-in API에 직접 사용 불가)"
                } else {
                    "Authorization Code를 받았습니다."
                },
            identityToken = idToken,
            authorizationCode = code,
        )
    }

    /**
     * 웹 OAuth 설정 상태 확인
     */
    @GetMapping("/status")
    fun status(): Map<String, Any> =
        mapOf(
            "configured" to appleWebAuthService.isConfigured(),
            "clientId" to (clientId.ifEmpty { "not configured" }),
            "redirectUri" to redirectUri,
        )

    /**
     * Identity Token (JWT)의 payload를 디코딩하여 사용자 정보 추출
     */
    @Suppress("TooGenericExceptionCaught")
    private fun decodeIdentityToken(idToken: String): AppleTokenInfo? =
        try {
            val parts = idToken.split(".")
            if (parts.size != JWT_PARTS_COUNT) {
                logger.warn("Invalid JWT format: expected $JWT_PARTS_COUNT parts, got ${parts.size}")
                null
            } else {
                val payload = String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                val mapper = jacksonObjectMapper()
                val claims: Map<String, Any> = mapper.readValue(payload)

                val tokenInfo =
                    AppleTokenInfo(
                        sub = claims["sub"] as? String,
                        email = claims["email"] as? String,
                        emailVerified = claims["email_verified"]?.toString()?.toBoolean(),
                        aud = claims["aud"] as? String,
                        iss = claims["iss"] as? String,
                        exp = (claims["exp"] as? Number)?.toLong(),
                        iat = (claims["iat"] as? Number)?.toLong(),
                        authTime = (claims["auth_time"] as? Number)?.toLong(),
                        nonceSupported = claims["nonce_supported"]?.toString()?.toBoolean(),
                    )

                logger.info(
                    "Decoded Apple Identity Token - " +
                        "sub: ${tokenInfo.sub}, " +
                        "email: ${tokenInfo.email}, " +
                        "aud: ${tokenInfo.aud}, " +
                        "iss: ${tokenInfo.iss}",
                )

                tokenInfo
            }
        } catch (e: Exception) {
            logger.error("Failed to decode identity token: ${e.message}", e)
            null
        }

    private fun buildAppleAuthUrl(): String {
        val baseUrl = "https://appleid.apple.com/auth/authorize"
        val params =
            mapOf(
                "client_id" to clientId,
                "redirect_uri" to redirectUri,
                "response_type" to "code id_token",
                "scope" to "email",
                "response_mode" to "form_post",
                "state" to "apple_login_test_${System.currentTimeMillis()}",
            )

        val queryString =
            params.entries.joinToString("&") { (key, value) ->
                "$key=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
            }

        return "$baseUrl?$queryString"
    }
}

data class AppleCallbackResponse(
    val success: Boolean,
    val message: String,
    val error: String? = null,
    val identityToken: String? = null,
    val authorizationCode: String? = null,
    val userDataFromApple: String? = null,
    val tokenInfo: AppleTokenInfo? = null,
)

data class AppleTokenInfo(
    val sub: String?,
    val email: String?,
    val emailVerified: Boolean?,
    val aud: String?,
    val iss: String?,
    val exp: Long?,
    val iat: Long?,
    val authTime: Long?,
    val nonceSupported: Boolean?,
)

private data class AppleCallbackData(
    val idToken: String?,
    val code: String?,
    val state: String?,
    val error: String?,
    val user: String?,
)
