package com.bos.backend.presentation.notification.dto

import com.bos.backend.domain.notification.enums.NotificationCategory
import com.fasterxml.jackson.annotation.JsonRawValue
import java.time.Instant

data class NotificationResponseDTO(
    val id: Long,
    val title: String,
    val content: String,
    val category: NotificationCategory,
    @JsonRawValue
    val deepLink: String?,
    val isRead: Boolean,
    val readAt: Instant?,
    val createdAt: Instant,
)
