package com.bos.backend.infrastructure.persistence

import com.bos.backend.domain.user.entity.UserDevice
import com.bos.backend.domain.user.repository.UserDeviceRepository
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

interface UserDeviceCoroutineRepository : CoroutineCrudRepository<UserDevice, Long> {
    suspend fun findByUserIdAndDeviceId(
        userId: Long,
        deviceId: String,
    ): UserDevice?

    fun findByUserId(userId: Long): Flow<UserDevice>

    @Modifying
    @Query("DELETE FROM user_devices WHERE fcm_token = :fcmToken")
    suspend fun deleteByFcmToken(fcmToken: String): Long

    @Modifying
    @Query("DELETE FROM user_devices WHERE expo_token = :expoToken")
    suspend fun deleteByExpoToken(expoToken: String): Long

    @Modifying
    @Query("UPDATE user_devices SET is_active = false WHERE user_id = :userId AND is_active = true")
    suspend fun deactivateByUserId(userId: Long)

    @Modifying
    @Query(
        """
        UPDATE user_devices SET is_active = false WHERE user_id = :userId AND device_id = :deviceId AND is_active = true
    """,
    )
    suspend fun deactivateByUserIdAndDeviceId(
        userId: Long,
        deviceId: String,
    )
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

    override suspend fun deleteByFcmToken(fcmToken: String): Long = coroutineRepository.deleteByFcmToken(fcmToken)

    override suspend fun deleteByExpoToken(expoToken: String): Long = coroutineRepository.deleteByExpoToken(expoToken)

    override suspend fun deactivateByUserId(userId: Long) = coroutineRepository.deactivateByUserId(userId)

    override suspend fun deactivateByUserIdAndDeviceId(
        userId: Long,
        deviceId: String,
    ) = coroutineRepository.deactivateByUserIdAndDeviceId(userId, deviceId)
}
