package com.bos.backend.domain.auth.repository

import com.bos.backend.domain.auth.entity.RefreshToken
import java.time.Instant

interface RefreshTokenRepository {
    suspend fun save(refreshToken: RefreshToken): RefreshToken

    suspend fun findByTokenHash(tokenHash: String): RefreshToken?

    suspend fun findByUserId(userId: Long): List<RefreshToken>

    suspend fun revokeByUserId(userId: Long)

    suspend fun revokeByTokenHash(tokenHash: String)

    suspend fun revokeByUserDeviceId(userDeviceId: Long)

    suspend fun deleteExpiredTokens(now: Instant = Instant.now())
}
