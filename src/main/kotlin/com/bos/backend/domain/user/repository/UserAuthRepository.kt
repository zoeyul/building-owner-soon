package com.bos.backend.domain.user.repository

import com.bos.backend.domain.user.entity.UserAuth

interface UserAuthRepository {
    suspend fun save(userAuth: UserAuth): UserAuth

    suspend fun findByUserId(userId: Long): UserAuth?

    suspend fun findByProviderIdAndProviderType(
        providerId: String,
        providerType: String,
    ): UserAuth?

    suspend fun findByEmailAndProviderType(
        email: String,
        providerType: String,
    ): UserAuth?

    suspend fun existsByEmail(email: String): Boolean

    suspend fun updateLastLoginAt(id: Long)

    suspend fun resetPassword(
        email: String,
        newPassword: String,
    )

    suspend fun verifyPassword(
        email: String,
        password: String,
    ): Boolean

    suspend fun updatePassword(
        userId: Long,
        newPassword: String,
    )

    suspend fun deleteByUserId(userId: Long)
}
