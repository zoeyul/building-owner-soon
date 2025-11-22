package com.bos.backend.domain.auth.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("refresh_tokens")
data class RefreshToken(
    @Id
    val id: Long? = null,
    @Column("user_id")
    val userId: Long,
    @Column("user_device_id")
    val userDeviceId: Long? = null,
    @Column("token_hash")
    val tokenHash: String,
    @Column("expires_at")
    val expiresAt: Instant,
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
    @Column("revoked_at")
    val revokedAt: Instant? = null,
) {
    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)

    fun isRevoked(): Boolean = revokedAt != null

    fun isValid(): Boolean = !isExpired() && !isRevoked()
}
