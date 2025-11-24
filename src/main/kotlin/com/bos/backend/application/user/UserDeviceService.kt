package com.bos.backend.application.user

import com.bos.backend.domain.user.entity.UserDevice
import com.bos.backend.domain.user.repository.UserDeviceRepository
import com.bos.backend.presentation.user.dto.FcmTokenUpdateRequestDTO
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class UserDeviceService(
    private val userDeviceRepository: UserDeviceRepository,
) {
    /**
     * FCM/Expo 토큰 업데이트
     * - expoToken이 null이면 디바이스를 비활성화합니다 (is_active = false)
     * - expoToken이 있으면 디바이스를 활성화하고 토큰을 업데이트합니다
     */
    suspend fun updateFcmToken(
        userId: Long,
        request: FcmTokenUpdateRequestDTO,
    ) {
        val existing = userDeviceRepository.findByUserIdAndDeviceId(userId, request.deviceId)

        if (existing != null) {
            // 기존 디바이스 토큰 업데이트
            // expoToken이 null이면 비활성화
            val updated =
                existing.copy(
                    fcmToken = request.fcmToken,
                    expoToken = request.expoToken,
                    platform = request.platform ?: existing.platform,
                    deviceName = request.deviceName ?: existing.deviceName,
                    isActive = request.expoToken != null,
                    updatedAt = Instant.now(),
                )
            userDeviceRepository.save(updated)
        } else {
            // 신규 디바이스 등록
            // expoToken이 null이면 비활성 상태로 등록
            val newDevice =
                UserDevice(
                    userId = userId,
                    deviceId = request.deviceId,
                    fcmToken = request.fcmToken,
                    expoToken = request.expoToken,
                    platform = request.platform,
                    deviceName = request.deviceName,
                    isActive = request.expoToken != null,
                )
            userDeviceRepository.save(newDevice)
        }
    }
}
