package com.bos.backend.presentation.auth.controller

import com.bos.backend.infrastructure.external.AppleApiService
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

/**
 * Apple Sign In 테스트용 컨트롤러
 * 프로덕션에서는 비활성화하거나 제거해야 합니다.
 */
@RestController
@RequestMapping("/oauth/apple")
class AppleAuthTestController(
    private val appleApiService: AppleApiService,
    @Value("\${apple.client-id}") private val clientId: String,
    @Value("\${apple.redirect-uri}") private val redirectUri: String,
) {
    private val logger = LoggerFactory.getLogger(AppleAuthTestController::class.java)

    companion object {
        private const val CODE_PREVIEW_LENGTH = 20
    }

    /**
     * Apple 로그인 페이지로 리다이렉트
     * 브라우저에서 /auth/apple/login 접속 시 Apple 로그인 화면으로 이동
     */
    @GetMapping("/login")
    fun login(exchange: ServerWebExchange): Mono<Void> {
        val authUrl = buildAppleAuthUrl()
        logger.info("Redirecting to Apple auth URL: $authUrl")

        exchange.response.statusCode = HttpStatus.FOUND
        exchange.response.headers.location = URI.create(authUrl)
        return exchange.response.setComplete()
    }

    /**
     * Apple OAuth 콜백 (POST)
     * Apple은 authorization code를 POST form data로 전달합니다.
     * redirect-uri가 /oauth/apple 이므로 루트 경로에서 POST를 받습니다.
     */
    @PostMapping("", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    suspend fun callbackPost(exchange: ServerWebExchange): AppleCallbackResponse {
        val formData = exchange.formData.awaitSingle()
        val code = formData.getFirst("code")
        val state = formData.getFirst("state")
        val error = formData.getFirst("error")
        val user = formData.getFirst("user")

        logger.info(
            "Apple callback received - code: ${code?.take(CODE_PREVIEW_LENGTH)}..., " +
                "state: $state, error: $error, user: $user",
        )

        if (error != null) {
            return AppleCallbackResponse(
                success = false,
                error = error,
                message = "Apple 로그인이 취소되었거나 오류가 발생했습니다.",
            )
        }

        if (code == null) {
            return AppleCallbackResponse(
                success = false,
                error = "missing_code",
                message = "Authorization code가 없습니다.",
            )
        }

        return try {
            val userInfo = appleApiService.verifyAuthorizationCode(code)
            AppleCallbackResponse(
                success = true,
                message = "Apple 인증 성공!",
                authorizationCode = code,
                appleUserId = userInfo.sub,
                email = userInfo.email,
                userDataFromApple = user,
            )
        } catch (e: Exception) {
            logger.error("Apple token verification failed", e)
            AppleCallbackResponse(
                success = false,
                error = e.message,
                message = "토큰 검증 실패. 로그를 확인하세요.",
                authorizationCode = code,
            )
        }
    }

    /**
     * Apple OAuth 콜백 (GET) - 일부 설정에서는 GET으로 올 수 있음
     */
    @GetMapping("/callback")
    suspend fun callbackGet(
        @RequestParam("code", required = false) code: String?,
        @RequestParam("error", required = false) error: String?,
    ): AppleCallbackResponse {
        logger.info("Apple callback (GET) received - code: ${code?.take(CODE_PREVIEW_LENGTH)}..., error: $error")

        if (code == null) {
            return AppleCallbackResponse(
                success = false,
                error = error ?: "missing_code",
                message = "Authorization code가 없습니다. Apple은 POST로 callback을 보내므로 response_mode 설정을 확인하세요.",
            )
        }

        return AppleCallbackResponse(
            success = true,
            message = "Authorization code를 받았습니다. 이 코드로 /auth/sign-in API를 호출하세요.",
            authorizationCode = code,
        )
    }

    private fun buildAppleAuthUrl(): String {
        val baseUrl = "https://appleid.apple.com/auth/authorize"
        val params =
            mapOf(
                "client_id" to clientId,
                "redirect_uri" to redirectUri,
                "response_type" to "code",
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
    val authorizationCode: String? = null,
    val appleUserId: String? = null,
    val email: String? = null,
    val userDataFromApple: String? = null,
)
