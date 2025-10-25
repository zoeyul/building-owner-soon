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
    suspend fun updateFcmToken(
        userId: Long,
        request: FcmTokenUpdateRequestDTO,
    ) {
        val existing = userDeviceRepository.findByUserIdAndDeviceId(userId, request.deviceId)

        if (existing != null) {
            // 기존 디바이스 토큰 업데이트
            val updated =
                existing.copy(
                    fcmToken = request.fcmToken,
                    expoToken = request.expoToken,
                    platform = request.platform ?: existing.platform,
                    deviceName = request.deviceName ?: existing.deviceName,
                    updatedAt = Instant.now(),
                )
            userDeviceRepository.save(updated)
        } else {
            // 신규 디바이스 등록
            val newDevice =
                UserDevice(
                    userId = userId,
                    deviceId = request.deviceId,
                    fcmToken = request.fcmToken,
                    expoToken = request.expoToken,
                    platform = request.platform,
                    deviceName = request.deviceName,
                )
            userDeviceRepository.save(newDevice)
        }
    }
}
