package com.bos.backend.infrastructure.persistence

import com.bos.backend.domain.user.entity.UserDevice
import com.bos.backend.domain.user.repository.UserDeviceRepository
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

interface UserDeviceCoroutineRepository : CoroutineCrudRepository<UserDevice, Long> {
    suspend fun findByUserIdAndDeviceId(
        userId: Long,
        deviceId: String,
    ): UserDevice?

    fun findByUserId(userId: Long): Flow<UserDevice>
}

@Repository
class R2dbcUserDeviceRepositoryImpl(
    private val coroutineRepository: UserDeviceCoroutineRepository,
) : UserDeviceRepository {
    override suspend fun findByUserIdAndDeviceId(
        userId: Long,
        deviceId: String,
    ): UserDevice? = coroutineRepository.findByUserIdAndDeviceId(userId, deviceId)

    override fun findByUserId(userId: Long): Flow<UserDevice> = coroutineRepository.findByUserId(userId)

    override suspend fun save(userDevice: UserDevice): UserDevice = coroutineRepository.save(userDevice)

    override suspend fun deleteByUserIdAndDeviceId(
        userId: Long,
        deviceId: String,
    ) {
        val userDevice = coroutineRepository.findByUserIdAndDeviceId(userId, deviceId)
        userDevice?.let { coroutineRepository.delete(it) }
    }
}
