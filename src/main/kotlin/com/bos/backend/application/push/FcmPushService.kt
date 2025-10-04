package com.bos.backend.application.push

import com.bos.backend.domain.push.PushMessage
import com.google.firebase.messaging.BatchResponse
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * FCM 푸시 알림 전송 서비스
 */
@Service
class FcmPushService {
    private val logger = LoggerFactory.getLogger(FcmPushService::class.java)

    /**
     * 단일 디바이스에 푸시 메시지 전송
     *
     * @param token FCM 등록 토큰
     * @param message 푸시 메시지
     * @return 전송 성공 여부
     */
    suspend fun sendToDevice(
        token: String,
        message: PushMessage,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val fcmMessage = message.toFcmMessage(token)
                val response = FirebaseMessaging.getInstance().send(fcmMessage)

                logger.info("FCM 메시지 전송 성공: token=$token, messageId=$response")
                true
            } catch (e: FirebaseMessagingException) {
                logger.error("FCM 메시지 전송 실패: token=$token, errorCode=${e.messagingErrorCode}", e)
                handleMessagingException(e, token)
                false
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception,
            ) {
                logger.error("FCM 메시지 전송 중 예외 발생: token=$token", e)
                false
            }
        }
    }

    /**
     * 여러 디바이스에 동일한 푸시 메시지 전송 (멀티캐스트)
     *
     * @param tokens FCM 등록 토큰 리스트 (최대 500개)
     * @param message 푸시 메시지
     * @return 전송 결과 (성공 개수, 실패 개수)
     */
    suspend fun sendToMultipleDevices(
        tokens: List<String>,
        message: PushMessage,
    ): PushSendResult {
        if (tokens.isEmpty()) {
            logger.warn("FCM 멀티캐스트: 토큰 리스트가 비어있음")
            return PushSendResult(0, 0)
        }

        if (tokens.size > MAX_TOKENS_PER_REQUEST) {
            logger.warn("FCM 멀티캐스트: 토큰 개수 초과 (${tokens.size}개), 최대 $MAX_TOKENS_PER_REQUEST 개까지 지원")
        }

        return withContext(Dispatchers.IO) {
            try {
                val notificationBuilder =
                    Notification
                        .builder()
                        .setTitle(message.title)
                        .setBody(message.body)

                message.imageUrl?.let { notificationBuilder.setImage(it) }

                val multicastMessageBuilder =
                    MulticastMessage
                        .builder()
                        .setNotification(notificationBuilder.build())
                        .addAllTokens(tokens.take(MAX_TOKENS_PER_REQUEST))

                message.data?.let { multicastMessageBuilder.putAllData(it) }

                val multicastMessage = multicastMessageBuilder.build()
                val response: BatchResponse = FirebaseMessaging.getInstance().sendEachForMulticast(multicastMessage)

                logger.info(
                    "FCM 멀티캐스트 전송 완료: 성공=${response.successCount}, 실패=${response.failureCount}",
                )

                // 실패한 토큰 로깅
                if (response.failureCount > 0) {
                    response.responses.forEachIndexed { index, sendResponse ->
                        if (!sendResponse.isSuccessful) {
                            val failedToken = tokens[index]
                            val exception = sendResponse.exception
                            logger.error(
                                "FCM 멀티캐스트 실패: token=$failedToken, " +
                                    "errorCode=${exception?.messagingErrorCode}",
                                exception,
                            )
                        }
                    }
                }

                PushSendResult(
                    successCount = response.successCount,
                    failureCount = response.failureCount,
                )
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception,
            ) {
                logger.error("FCM 멀티캐스트 전송 중 예외 발생", e)
                PushSendResult(0, tokens.size)
            }
        }
    }

    /**
     * FCM 전송 예외 처리
     */
    private fun handleMessagingException(
        exception: FirebaseMessagingException,
        token: String,
    ) {
        when (exception.messagingErrorCode) {
            // 유효하지 않은 토큰 - DB에서 삭제 필요
            com.google.firebase.messaging.MessagingErrorCode.INVALID_ARGUMENT,
            com.google.firebase.messaging.MessagingErrorCode.UNREGISTERED,
            -> {
                logger.warn("유효하지 않은 FCM 토큰: token=$token, DB에서 삭제 필요")
                // TODO: UserDeviceRepository를 통해 토큰 삭제
            }
            // 할당량 초과 - Rate Limiting 필요
            com.google.firebase.messaging.MessagingErrorCode.QUOTA_EXCEEDED -> {
                logger.error("FCM 할당량 초과: 전송 속도 제한 필요")
            }
            // 내부 오류 - 재시도 필요
            com.google.firebase.messaging.MessagingErrorCode.INTERNAL -> {
                logger.error("FCM 내부 오류: 재시도 필요")
            }
            // 기타 오류
            else -> {
                logger.error("FCM 알 수 없는 오류: errorCode=${exception.messagingErrorCode}")
            }
        }
    }

    companion object {
        // FCM 멀티캐스트 최대 토큰 개수
        private const val MAX_TOKENS_PER_REQUEST = 500
    }
}

/**
 * 푸시 전송 결과
 */
data class PushSendResult(
    val successCount: Int,
    val failureCount: Int,
)
