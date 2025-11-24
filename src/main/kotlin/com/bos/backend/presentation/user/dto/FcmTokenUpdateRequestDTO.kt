package com.bos.backend.presentation.user.dto

import com.bos.backend.domain.user.enum.Platform
import jakarta.validation.constraints.NotBlank

/**
 * FCM/Expo 토큰 업데이트 요청 DTO
 * - expoToken이 null이면 해당 디바이스를 비활성화합니다
 * - expoToken이 있으면 정상적으로 등록/업데이트합니다
 */
data class FcmTokenUpdateRequestDTO(
    @field:NotBlank(message = "디바이스 ID는 필수입니다")
    val deviceId: String,
    val fcmToken: String?,
    val expoToken: String?,
    val platform: Platform?,
    val deviceName: String?,
)
