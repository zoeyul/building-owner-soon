package com.bos.backend.domain.user.repository

import com.bos.backend.domain.user.entity.UserDevice

interface UserDeviceRepository {
    suspend fun findByUserIdAndDeviceId(
        userId: Long,
        deviceId: String,
    ): UserDevice?

    suspend fun save(userDevice: UserDevice): UserDevice

    suspend fun deleteByUserIdAndDeviceId(
        userId: Long,
        deviceId: String,
    )
}
