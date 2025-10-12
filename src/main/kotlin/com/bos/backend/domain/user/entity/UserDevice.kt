package com.bos.backend.domain.user.entity

import com.bos.backend.domain.user.enum.Platform
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("user_devices")
data class UserDevice(
    @Id
    val id: Long? = null,
    @Column("user_id")
    val userId: Long,
    @Column("device_id")
    val deviceId: String,
    @Column("fcm_token")
    val fcmToken: String,
    val platform: Platform? = null,
    @Column("device_name")
    val deviceName: String? = null,
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
    @Column("updated_at")
    val updatedAt: Instant = Instant.now(),
)
