package com.bos.backend.application.push

import com.bos.backend.domain.push.ExpoPushMessage
import com.bos.backend.domain.push.ExpoPushRequest
import com.bos.backend.domain.push.ExpoPushResponse
import com.bos.backend.domain.push.ExpoPushTicket
import com.bos.backend.domain.user.entity.UserDevice
import com.bos.backend.domain.user.repository.UserDeviceRepository
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

/**
 * 디바이스 푸시 처리 결과
 */
private data class DevicePushResult(
    val isSuccess: Boolean,
    val isInvalidToken: Boolean,
)

/**
 * Expo 푸시 전송 상세 결과
 */
data class ExpoPushSendResult(
    val success: Boolean,
    val ticketId: String? = null,
    val errorMessage: String? = null,
    val errorDetails: Map<String, Any>? = null,
)

/**
 * 푸시 전송 결과
 */
data class PushSendResult(
    val successCount: Int,
    val failureCount: Int,
    val deletedTokens: List<String>,
)

/**
 * Expo Push Notification Service
 * Expo Push Notification API (https://exp.host/--/api/v2/push/send)를 사용한 푸시 알림 서비스
 */
@Service
@Suppress("TooManyFunctions")
class ExpoPushService(
    private val webClient: WebClient,
    private val userDeviceRepository: UserDeviceRepository,
) {
    private val logger = LoggerFactory.getLogger(ExpoPushService::class.java)

    companion object {
        private const val EXPO_PUSH_API_URL = "https://exp.host/--/api/v2/push/send"
        private const val EXPO_TOKEN_PREFIX = "ExponentPushToken["
        private const val MIN_VALID_TOKEN_LENGTH = 10
    }

    /**
     * 단일 디바이스에 푸시 메시지 전송
     */
    suspend fun sendToDevice(
        token: String,
        message: ExpoPushMessage,
    ): Boolean {
        val result = sendToDeviceWithDetails(token, message)
        return result.success
    }

    /**
     * 단일 디바이스에 푸시 메시지 전송 (상세 결과 포함)
     */
    suspend fun sendToDeviceWithDetails(
        token: String,
        message: ExpoPushMessage,
    ): ExpoPushSendResult {
        if (!isValidExpoToken(token)) {
            logger.warn("유효하지 않은 Expo Push Token 형식: token=$token")
            return ExpoPushSendResult(
                success = false,
                errorMessage = "유효하지 않은 Expo Push Token 형식",
            )
        }

        return try {
            val formattedToken = formatExpoToken(token)
            val request = ExpoPushRequest.from(message.copy(to = formattedToken))
            val rawResponse = sendPushRequest(request)

            val response = ObjectMapper().readValue(rawResponse, ExpoPushResponse::class.java)
            if (response.data.isNotEmpty()) {
                val ticket = response.data[0]
                if (ticket.isSuccess()) {
                    logger.info("Expo 푸시 메시지 전송 성공: token=$token, ticketId=${ticket.id}")
                    ExpoPushSendResult(
                        success = true,
                        ticketId = ticket.id,
                    )
                } else {
                    logger.error("Expo 푸시 메시지 전송 실패: token=$token, error=${ticket.message}")
                    handleErrorTicket(ticket, token)
                    ExpoPushSendResult(
                        success = false,
                        errorMessage = ticket.message,
                        errorDetails = ticket.details,
                    )
                }
            } else {
                ExpoPushSendResult(
                    success = false,
                    errorMessage = "Expo API 응답이 비어있습니다",
                )
            }
        } catch (e: WebClientResponseException) {
            logger.error("Expo API 호출 실패: token=$token, status=${e.statusCode}, body=${e.responseBodyAsString}", e)
            ExpoPushSendResult(
                success = false,
                errorMessage = "Expo API 호출 실패: ${e.statusCode} ${e.statusText}",
                errorDetails = mapOf("statusCode" to e.statusCode.value(), "body" to e.responseBodyAsString),
            )
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception,
        ) {
            logger.error("Expo 푸시 메시지 전송 중 예외 발생: token=$token", e)
            ExpoPushSendResult(
                success = false,
                errorMessage = "푸시 전송 중 예외 발생: ${e.message}",
            )
        }
    }

    /**
     * 여러 디바이스에 푸시 메시지 전송
     */
    @Suppress("LongMethod", "NestedBlockDepth")
    suspend fun sendToMultipleDevices(
        devices: List<UserDevice>,
        messages: List<ExpoPushMessage>,
    ): PushSendResult {
        if (devices.isEmpty()) {
            logger.warn("Expo 전송: 디바이스 리스트가 비어있음")
            return PushSendResult(0, 0, emptyList())
        }

        var successCount = 0
        var failureCount = 0
        val invalidTokens = mutableListOf<String>()

        devices.zip(messages).forEach { (device, message) ->
            val result = processDevicePush(device, message)
            when {
                result.isSuccess -> successCount++
                result.isInvalidToken -> {
                    device.expoToken?.let { invalidTokens.add(it) }
                    failureCount++
                }
                else -> failureCount++
            }
        }

        // 무효화된 토큰 자동 삭제
        if (invalidTokens.isNotEmpty()) {
            deleteInvalidTokens(invalidTokens)
        }

        logger.info("Expo 전송 완료: 성공=$successCount, 실패=$failureCount, 삭제된 토큰=${invalidTokens.size}")

        return PushSendResult(
            successCount = successCount,
            failureCount = failureCount,
            deletedTokens = invalidTokens,
        )
    }

    /**
     * 단일 디바이스 푸시 처리
     */
    @Suppress("ReturnCount")
    private suspend fun processDevicePush(
        device: UserDevice,
        message: ExpoPushMessage,
    ): DevicePushResult {
        val token = device.expoToken

        if (token == null || !isValidExpoToken(token)) {
            logger.warn("유효하지 않은 Expo 토큰 형식: userId=${device.userId}, token=$token")
            return DevicePushResult(isSuccess = false, isInvalidToken = true)
        }

        return try {
            val formattedToken = formatExpoToken(token)
            val request = ExpoPushRequest.from(message.copy(to = formattedToken))
            val rawResponse = sendPushRequest(request)

            val response = ObjectMapper().readValue(rawResponse, ExpoPushResponse::class.java)
            if (response.data.isEmpty()) {
                return DevicePushResult(isSuccess = false, isInvalidToken = false)
            }

            val ticket = response.data[0]
            handleTicketResult(ticket, device, token)
        } catch (e: WebClientResponseException) {
            logger.error(
                "Expo API 호출 실패: userId=${device.userId}, status=${e.statusCode}, body=${e.responseBodyAsString}",
                e,
            )
            DevicePushResult(isSuccess = false, isInvalidToken = false)
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception,
        ) {
            logger.error("Expo 메시지 전송 중 예외 발생: userId=${device.userId}, token=$token", e)
            DevicePushResult(isSuccess = false, isInvalidToken = false)
        }
    }

    /**
     * 티켓 결과 처리
     */
    private fun handleTicketResult(
        ticket: ExpoPushTicket,
        device: UserDevice,
        token: String,
    ): DevicePushResult {
        return if (ticket.isSuccess()) {
            logger.debug(
                "Expo 메시지 전송 성공: userId=${device.userId}, platform=${device.platform}, ticketId=${ticket.id}",
            )
            DevicePushResult(isSuccess = true, isInvalidToken = false)
        } else {
            logger.error(
                "Expo 메시지 전송 실패: userId=${device.userId}, token=$token, error=${ticket.message}",
            )
            DevicePushResult(isSuccess = false, isInvalidToken = isInvalidTokenError(ticket))
        }
    }

    /**
     * Expo Push API 호출
     * Expo API는 배열로 전송해야 일관된 배열 응답을 받음
     */
    private suspend fun sendPushRequest(request: ExpoPushRequest): String {
        return webClient
            .post()
            .uri(EXPO_PUSH_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(listOf(request))
            .retrieve()
            .bodyToMono(String::class.java)
            .awaitSingle()
    }

    /**
     * Expo Push Token 유효성 검사
     * DB에 저장된 토큰 스트링이 비어있지 않으면 유효한 것으로 간주
     */
    private fun isValidExpoToken(token: String): Boolean {
        return token.isNotBlank() && token.length > MIN_VALID_TOKEN_LENGTH
    }

    /**
     * DB에 저장된 토큰 스트링을 ExponentPushToken 형식으로 변환
     * 이미 ExponentPushToken[] 형식이면 그대로 반환, 아니면 감싸서 반환
     */
    private fun formatExpoToken(token: String): String {
        return if (token.startsWith(EXPO_TOKEN_PREFIX) && token.endsWith("]")) {
            token
        } else {
            "$EXPO_TOKEN_PREFIX$token]"
        }
    }

    /**
     * 무효화된 토큰 에러인지 확인
     */
    private fun isInvalidTokenError(ticket: ExpoPushTicket): Boolean {
        val errorMessage = ticket.message?.lowercase() ?: ""
        return errorMessage.contains("devicenotregistered") ||
            errorMessage.contains("invalid") ||
            errorMessage.contains("expiration")
    }

    /**
     * 에러 티켓 처리
     */
    private fun handleErrorTicket(
        ticket: ExpoPushTicket,
        token: String,
    ) {
        val errorMessage = ticket.message ?: "Unknown error"
        when {
            errorMessage.contains("DeviceNotRegistered", ignoreCase = true) -> {
                logger.warn("디바이스가 등록되지 않음: token=$token, 자동 삭제 예정")
            }
            errorMessage.contains("InvalidCredentials", ignoreCase = true) -> {
                logger.error("Expo 인증 실패: API 키 확인 필요")
            }
            errorMessage.contains("MessageTooBig", ignoreCase = true) -> {
                logger.error("메시지 크기 초과: 페이로드 크기 확인 필요")
            }
            errorMessage.contains("MessageRateExceeded", ignoreCase = true) -> {
                logger.error("메시지 전송 속도 제한 초과: 전송 속도 조절 필요")
            }
            else -> {
                logger.error("Expo 푸시 전송 실패: error=$errorMessage, details=${ticket.details}")
            }
        }
    }

    /**
     * 무효화된 토큰을 DB에서 삭제
     */
    private suspend fun deleteInvalidTokens(tokens: List<String>) {
        tokens.forEach { token ->
            try {
                val deletedCount = userDeviceRepository.deleteByExpoToken(token)
                if (deletedCount > 0) {
                    logger.info("유효하지 않은 Expo 토큰 삭제 완료: token=$token, 삭제된 레코드 수=$deletedCount")
                }
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception,
            ) {
                logger.error("Expo 토큰 삭제 실패: token=$token", e)
            }
        }
    }
}
