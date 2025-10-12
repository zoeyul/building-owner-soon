package com.bos.backend.application.user

import com.bos.backend.domain.user.entity.UserDevice
import com.bos.backend.domain.user.enum.Platform
import com.bos.backend.domain.user.repository.UserDeviceRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class UserDeviceService(
    private val userDeviceRepository: UserDeviceRepository,
) {
    suspend fun updateFcmToken(
        userId: Long,
        deviceId: String,
        fcmToken: String,
        platform: Platform?,
        deviceName: String?,
    ) {
        val existing = userDeviceRepository.findByUserIdAndDeviceId(userId, deviceId)

        if (existing != null) {
            // 기존 디바이스 토큰 업데이트
            val updated =
                existing.copy(
                    fcmToken = fcmToken,
                    platform = platform ?: existing.platform,
                    deviceName = deviceName ?: existing.deviceName,
                    updatedAt = Instant.now(),
                )
            userDeviceRepository.save(updated)
        } else {
            // 신규 디바이스 등록
            val newDevice =
                UserDevice(
                    userId = userId,
                    deviceId = deviceId,
                    fcmToken = fcmToken,
                    platform = platform,
                    deviceName = deviceName,
                )
            userDeviceRepository.save(newDevice)
        }
    }
}
