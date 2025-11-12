package com.bos.backend.presentation.user.dto

import java.time.Instant

data class UserDeviceInfoResponseDTO(
    val devices: List<DeviceTokenInfo>,
)

data class DeviceTokenInfo(
    val userId: Long,
    val expoToken: String?,
    val platform: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
