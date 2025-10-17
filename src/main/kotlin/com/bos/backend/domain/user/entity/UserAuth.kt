package com.bos.backend.domain.user.entity

import com.bos.backend.domain.user.enum.ProviderType
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("user_auths")
data class UserAuth(
    @Id
    val id: Long? = null,
    @Column("user_id")
    val userId: Long,
    @Column("provider_type")
    // TODO: r2dbc가 AttributeConberter 지원하는지 확인 필요
    private val _providerType: String,
    @Column("provider_id")
    val providerId: String? = null,
    @Column("email")
    val email: String?,
    @Column("password_hash")
    val passwordHash: String? = null,
    @Column("last_login_at")
    val lastLoginAt: Instant? = null,
) {
    val providerType: ProviderType
        get() = ProviderType.fromValue(_providerType)

    fun updateLastLoginAt(): UserAuth = this.copy(lastLoginAt = Instant.now())
}
