package com.bos.backend.infrastructure.persistence

import com.bos.backend.domain.auth.entity.RefreshToken
import com.bos.backend.domain.auth.repository.RefreshTokenRepository
import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant

interface RefreshTokenCoroutineRepository : CoroutineCrudRepository<RefreshToken, Long> {
    suspend fun findByTokenHash(tokenHash: String): RefreshToken?

    suspend fun findByUserId(userId: Long): List<RefreshToken>

    @Modifying
    @Query("UPDATE refresh_tokens SET revoked_at = :revokedAt WHERE user_id = :userId AND revoked_at IS NULL")
    suspend fun revokeByUserId(
        userId: Long,
        revokedAt: Instant = Instant.now(),
    )

    @Modifying
    @Query("UPDATE refresh_tokens SET revoked_at = :revokedAt WHERE token_hash = :tokenHash AND revoked_at IS NULL")
    suspend fun revokeByTokenHash(
        tokenHash: String,
        revokedAt: Instant = Instant.now(),
    )

    @Modifying
    @Query(
        "UPDATE refresh_tokens SET revoked_at = :revokedAt WHERE user_device_id = :userDeviceId AND revoked_at IS NULL",
    )
    suspend fun revokeByUserDeviceId(
        userDeviceId: Long,
        revokedAt: Instant = Instant.now(),
    )

    @Modifying
    @Query("DELETE FROM refresh_tokens WHERE expires_at < :now OR revoked_at IS NOT NULL")
    suspend fun deleteExpiredTokens(now: Instant): Long
}

@Repository
class R2dbcRefreshTokenRepository(
    private val coroutineRepository: RefreshTokenCoroutineRepository,
) : RefreshTokenRepository {
    override suspend fun save(refreshToken: RefreshToken): RefreshToken = coroutineRepository.save(refreshToken)

    override suspend fun findByTokenHash(tokenHash: String): RefreshToken? =
        coroutineRepository.findByTokenHash(tokenHash)

    override suspend fun findByUserId(userId: Long): List<RefreshToken> = coroutineRepository.findByUserId(userId)

    override suspend fun revokeByUserId(userId: Long) {
        coroutineRepository.revokeByUserId(userId)
    }

    override suspend fun revokeByTokenHash(tokenHash: String) {
        coroutineRepository.revokeByTokenHash(tokenHash)
    }

    override suspend fun revokeByUserDeviceId(userDeviceId: Long) {
        coroutineRepository.revokeByUserDeviceId(userDeviceId)
    }

    // TODO: for admin
    override suspend fun deleteExpiredTokens(now: Instant) {
        coroutineRepository.deleteExpiredTokens(now)
    }
}
