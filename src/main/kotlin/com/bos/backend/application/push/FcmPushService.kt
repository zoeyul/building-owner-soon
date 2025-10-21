package com.bos.backend.application.push

import com.bos.backend.domain.push.PushMessage
import com.bos.backend.domain.user.entity.UserDevice
import com.bos.backend.domain.user.repository.UserDeviceRepository
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.MessagingErrorCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FcmPushService(
    private val userDeviceRepository: UserDeviceRepository,
) {
    private val logger = LoggerFactory.getLogger(FcmPushService::class.java)

    /**
     * 단일 디바이스에 푸시 메시지 전송
     * 플랫폼 정보를 알 수 없는 경우 사용
     */
    suspend fun sendToDevice(
        token: String,
        message: PushMessage,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val fcmMessage = message.toFcmMessage(token, null)
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
     * 여러 디바이스에 푸시 메시지 전송
     * UserDevice 객체를 받아 플랫폼별 최적화된 메시지 전송
     */
    suspend fun sendToMultipleDevices(
        devices: List<UserDevice>,
        message: PushMessage,
    ): PushSendResult {
        if (devices.isEmpty()) {
            logger.warn("FCM 전송: 디바이스 리스트가 비어있음")
            return PushSendResult(0, 0, emptyList())
        }

        var successCount = 0
        var failureCount = 0
        val invalidTokens = mutableListOf<String>()

        return withContext(Dispatchers.IO) {
            devices.forEach { device ->
                try {
                    // 플랫폼별 최적화된 메시지 생성
                    val fcmMessage = message.toFcmMessage(device.fcmToken, device.platform)
                    val response = FirebaseMessaging.getInstance().send(fcmMessage)

                    logger.debug(
                        "FCM 메시지 전송 성공: " +
                            "userId=${device.userId}, platform=${device.platform}, messageId=$response",
                    )
                    successCount++
                } catch (e: FirebaseMessagingException) {
                    logger.error(
                        "FCM 메시지 전송 실패: " +
                            "userId=${device.userId}, token=${device.fcmToken}, " +
                            "platform=${device.platform}, errorCode=${e.messagingErrorCode}",
                        e,
                    )

                    // 유효하지 않은 토큰 수집
                    if (isInvalidTokenError(e)) {
                        invalidTokens.add(device.fcmToken)
                    }

                    failureCount++
                } catch (
                    @Suppress("TooGenericExceptionCaught")
                    e: Exception,
                ) {
                    logger.error(
                        "FCM 메시지 전송 중 예외 발생: " +
                            "userId=${device.userId}, token=${device.fcmToken}",
                        e,
                    )
                    failureCount++
                }
            }

            // 무효화된 토큰 자동 삭제
            if (invalidTokens.isNotEmpty()) {
                deleteInvalidTokens(invalidTokens)
            }

            logger.info("FCM 전송 완료: 성공=$successCount, 실패=$failureCount, 삭제된 토큰=${invalidTokens.size}")

            PushSendResult(
                successCount = successCount,
                failureCount = failureCount,
                deletedTokens = invalidTokens,
            )
        }
    }

    /**
     * 데이터 전용 메시지 전송 (조용한 푸시)
     */
    suspend fun sendDataOnlyMessage(
        devices: List<UserDevice>,
        data: Map<String, String>,
    ): PushSendResult {
        val dataMessage =
            PushMessage.createDataOnlyMessage(
                title = "Background Update",
                body = "Data sync",
                data = data,
            )

        return sendToMultipleDevices(devices, dataMessage)
    }

    /**
     * 무효화된 토큰인지 확인
     */
    private fun isInvalidTokenError(exception: FirebaseMessagingException): Boolean {
        return when (exception.messagingErrorCode) {
            MessagingErrorCode.INVALID_ARGUMENT,
            MessagingErrorCode.UNREGISTERED,
            -> true
            else -> false
        }
    }

    /**
     * 무효화된 토큰을 DB에서 삭제
     */
    private suspend fun deleteInvalidTokens(tokens: List<String>) {
        tokens.forEach { token ->
            try {
                val deletedCount = userDeviceRepository.deleteByFcmToken(token)
                if (deletedCount > 0) {
                    logger.info("유효하지 않은 FCM 토큰 삭제 완료: token=$token, 삭제된 레코드 수=$deletedCount")
                }
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception,
            ) {
                logger.error("FCM 토큰 삭제 실패: token=$token", e)
            }
        }
    }

    /**
     * FCM 에러 처리
     */
    private fun handleMessagingException(
        exception: FirebaseMessagingException,
        token: String,
    ) {
        when (exception.messagingErrorCode) {
            MessagingErrorCode.INVALID_ARGUMENT,
            MessagingErrorCode.UNREGISTERED,
            -> {
                logger.warn("유효하지 않은 FCM 토큰: token=$token, 자동 삭제 예정")
            }
            MessagingErrorCode.QUOTA_EXCEEDED -> {
                logger.error("FCM 할당량 초과: 전송 속도 제한 필요")
            }
            MessagingErrorCode.INTERNAL -> {
                logger.error("FCM 내부 오류: 재시도 필요")
            }
            MessagingErrorCode.THIRD_PARTY_AUTH_ERROR -> {
                logger.error("FCM 인증 오류: APNs 인증서 또는 서비스 계정 확인 필요")
            }
            else -> {
                logger.error("FCM 알 수 없는 오류: errorCode=${exception.messagingErrorCode}")
            }
        }
    }
}

/**
 * 푸시 전송 결과
 * @param successCount 성공한 전송 개수
 * @param failureCount 실패한 전송 개수
 * @param deletedTokens 삭제된 무효 토큰 목록
 */
data class PushSendResult(
    val successCount: Int,
    val failureCount: Int,
    val deletedTokens: List<String> = emptyList(),
)
