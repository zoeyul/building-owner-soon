package com.bos.backend.presentation.user.dto

import com.bos.backend.domain.user.entity.ProfileCharacter
import java.time.Instant

data class UserProfileResponseDTO(
    val id: Long,
    val email: String?,
    val nickname: String,
    val provider: String,
    val character: ProfileCharacter? = null,
    val isNotificationAllowed: Boolean,
    val isMarketingAgreed: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
