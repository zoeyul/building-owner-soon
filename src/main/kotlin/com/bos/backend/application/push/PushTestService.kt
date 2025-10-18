package com.bos.backend.application.push

import com.bos.backend.domain.push.DeepLinkData
import com.bos.backend.domain.push.PushMessage
import com.bos.backend.domain.user.repository.UserDeviceRepository
import com.bos.backend.presentation.push.dto.DeepLinkType
import kotlinx.coroutines.flow.toList
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PushTestService(
    private val fcmPushService: FcmPushService,
    private val userDeviceRepository: UserDeviceRepository,
) {
    private val logger = LoggerFactory.getLogger(PushTestService::class.java)

    suspend fun sendTestPush(
        userId: Long,
        message: String,
        deepLinkType: DeepLinkType,
        transactionId: Long,
    ): PushTestResult {
        // 사용자의 모든 디바이스 조회
        val devices = userDeviceRepository.findByUserId(userId).toList()

        if (devices.isEmpty()) {
            logger.warn("푸시 테스트 실패: userId=$userId, FCM 토큰이 등록되지 않음")
            return PushTestResult(
                success = false,
                message = "등록된 FCM 토큰이 없습니다. 먼저 디바이스를 등록해주세요.",
                sentCount = 0,
            )
        }

        // DeepLink 데이터 생성
        val deepLinkData =
            when (deepLinkType) {
                DeepLinkType.REPAYMENT -> DeepLinkData.createRepaymentDeepLink(transactionId)
                DeepLinkType.TRANSACTION -> DeepLinkData.createTransactionDeepLink(transactionId)
            }

        // 푸시 메시지 생성
        val pushMessage =
            PushMessage(
                title = "테스트 푸시",
                body = message,
                data =
                    mapOf(
                        "deepLink" to deepLinkData.toJsonString(),
                        "type" to deepLinkType.name,
                        "transactionId" to transactionId.toString(),
                    ),
            )

        // 모든 디바이스에 전송
        val tokens = devices.map { it.fcmToken }
        val sendResult = fcmPushService.sendToMultipleDevices(tokens, pushMessage)

        logger.info(
            "푸시 테스트 완료: userId=$userId, 성공=${sendResult.successCount}, 실패=${sendResult.failureCount}",
        )

        return PushTestResult(
            success = sendResult.successCount > 0,
            message = "푸시 전송 완료: 성공 ${sendResult.successCount}건, 실패 ${sendResult.failureCount}건",
            sentCount = sendResult.successCount,
        )
    }
}

data class PushTestResult(
    val success: Boolean,
    val message: String,
    val sentCount: Int,
)
