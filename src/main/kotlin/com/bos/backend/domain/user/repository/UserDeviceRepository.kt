package com.bos.backend.domain.user.repository

import com.bos.backend.domain.user.entity.UserDevice
import kotlinx.coroutines.flow.Flow

interface UserDeviceRepository {
    suspend fun findByUserIdAndDeviceId(
        userId: Long,
        deviceId: String,
    ): UserDevice?

    fun findByUserId(userId: Long): Flow<UserDevice>

    suspend fun save(userDevice: UserDevice): UserDevice

    suspend fun deleteByUserIdAndDeviceId(
        userId: Long,
        deviceId: String,
    )

    suspend fun deleteByFcmToken(fcmToken: String): Long
}
