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

    suspend fun deleteByExpoToken(expoToken: String): Long

    suspend fun deactivateByUserId(userId: Long)

    suspend fun deactivateByUserIdAndDeviceId(
        userId: Long,
        deviceId: String,
    )
}
