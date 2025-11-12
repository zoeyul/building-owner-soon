package com.bos.backend.presentation.push.dto

import java.time.Instant

data class ExpoPushTestResponseDTO(
    val success: Boolean,
    val message: String,
    val sentCount: Int,
    val devices: List<DeviceInfo>?,
    val error: ErrorInfo? = null,
)

data class DeviceInfo(
    val userId: Long,
    val token: String,
    val platform: String?,
    val updatedAt: Instant,
    val success: Boolean,
    val ticketId: String? = null,
    val expoErrorMessage: String? = null,
    val expoErrorDetails: Map<String, Any>? = null,
)

data class ErrorInfo(
    val errorMessage: String,
    val failedDevices: List<DeviceInfo>,
)
