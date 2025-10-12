package com.bos.backend.presentation.user.dto

import com.bos.backend.domain.user.enum.Platform
import jakarta.validation.constraints.NotBlank

data class FcmTokenUpdateRequestDTO(
    @field:NotBlank(message = "디바이스 ID는 필수입니다")
    val deviceId: String,
    @field:NotBlank(message = "FCM 토큰은 필수입니다")
    val fcmToken: String,
    val platform: Platform?,
    val deviceName: String?,
)
