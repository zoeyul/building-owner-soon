package com.bos.backend.presentation.push.controller

import com.bos.backend.application.push.ExpoPushService
import com.bos.backend.application.push.PushTestResult
import com.bos.backend.application.push.PushTestService
import com.bos.backend.domain.push.ExpoPushMessage
import com.bos.backend.domain.user.repository.UserDeviceRepository
import com.bos.backend.presentation.push.dto.DeepLinkType
import com.bos.backend.presentation.push.dto.DeviceInfo
import com.bos.backend.presentation.push.dto.ErrorInfo
import com.bos.backend.presentation.push.dto.ExpoPushTestRequestDTO
import com.bos.backend.presentation.push.dto.ExpoPushTestResponseDTO
import com.bos.backend.presentation.push.dto.PushSimpleTestRequestDTO
import com.bos.backend.presentation.push.dto.PushTestRequestDTO
import jakarta.validation.Valid
import kotlinx.coroutines.flow.toList
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/push")
class PushTestController(
    private val pushTestService: PushTestService,
    private val expoPushService: ExpoPushService,
    private val userDeviceRepository: UserDeviceRepository,
) {
    @PostMapping("/test")
    suspend fun sendTestPush(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: PushTestRequestDTO,
    ): PushTestResult {
        val deepLinkType = DeepLinkType.fromString(request.deepLinkType)

        return pushTestService.sendTestPush(
            userId = userId.toLong(),
            message = request.message,
            deepLinkType = deepLinkType,
            transactionId = request.transactionId,
        )
    }

    @PostMapping("/test/simple")
    suspend fun sendSimpleTestPush(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: PushSimpleTestRequestDTO,
    ): PushTestResult =
        pushTestService.sendSimpleTestPush(
            userId = request.userId,
        )

    @PostMapping("/test/expo")
    @Suppress("LongMethod")
    suspend fun sendExpoTestPush(
        @Valid @RequestBody request: ExpoPushTestRequestDTO,
    ): ExpoPushTestResponseDTO {
        // 사용자의 모든 디바이스 조회
        val devices = userDeviceRepository.findByUserId(request.userId).toList()

        if (devices.isEmpty()) {
            return ExpoPushTestResponseDTO(
                success = false,
                message = "등록된 디바이스가 없습니다. 먼저 디바이스를 등록해주세요.",
                sentCount = 0,
                devices = null,
                error = null,
            )
        }

        // Expo 푸시 메시지 생성
        val message =
            ExpoPushMessage(
                to = "",
                title = request.title,
                body = request.body,
            )

        // 디바이스 정보 수집
        val deviceInfoList = mutableListOf<DeviceInfo>()
        var successCount = 0
        var failureCount = 0
        val errorMessages = mutableListOf<String>()

        devices.forEach { device ->
            val result = expoPushService.sendToDeviceWithDetails(device.expoToken!!, message)

            val deviceInfo =
                DeviceInfo(
                    userId = device.userId,
                    token = device.fcmToken,
                    platform = device.platform?.name,
                    updatedAt = device.updatedAt,
                    success = result.success,
                    ticketId = result.ticketId,
                    expoErrorMessage = result.errorMessage,
                    expoErrorDetails = result.errorDetails,
                )

            deviceInfoList.add(deviceInfo)

            if (result.success) {
                successCount++
            } else {
                failureCount++
                val errorMsg =
                    result.errorMessage?.let { "토큰 ${device.fcmToken}: $it" }
                        ?: "토큰 ${device.fcmToken} 전송 실패"
                errorMessages.add(errorMsg)
            }
        }

        return if (failureCount > 0) {
            ExpoPushTestResponseDTO(
                success = successCount > 0,
                message = "푸시 전송 완료: 성공 ${successCount}건, 실패 ${failureCount}건",
                sentCount = successCount,
                devices = deviceInfoList,
                error =
                    ErrorInfo(
                        errorMessage = errorMessages.joinToString(", "),
                        failedDevices = deviceInfoList.filter { !it.success },
                    ),
            )
        } else {
            ExpoPushTestResponseDTO(
                success = true,
                message = "푸시 전송 성공: ${successCount}건",
                sentCount = successCount,
                devices = deviceInfoList,
                error = null,
            )
        }
    }
}
