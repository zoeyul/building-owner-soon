package com.bos.backend.domain.user.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.net.URI
import java.time.Instant

@Table("users")
data class User(
    @Id
    val id: Long? = null,
    val nickname: String,
    @Column("notification_allowed")
    val isNotificationAllowed: Boolean = false,
    @Column("marketing_agreed")
    val isMarketingAgreed: Boolean = false,
    @Column("character_components")
    val character: ProfileCharacter,
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
    @Column("updated_at")
    val updatedAt: Instant = Instant.now(),
    @Column("deleted_at")
    val deletedAt: Instant? = null,
) {
    fun isDeleted(): Boolean = deletedAt != null

    fun delete(): User = this.copy(deletedAt = Instant.now(), updatedAt = Instant.now())

    fun update(
        nickname: String?,
        isNotificationAllowed: Boolean?,
        isMarketingAgreed: Boolean?,
        character: ProfileCharacter?,
    ): User =
        this.copy(
            nickname = nickname ?: this.nickname,
            isNotificationAllowed = isNotificationAllowed ?: this.isNotificationAllowed,
            isMarketingAgreed = isMarketingAgreed ?: this.isMarketingAgreed,
            character = character ?: this.character,
            updatedAt = Instant.now(),
        )
}

// TODO: 위치 고민
data class ProfileCharacter(
    val face: CharacterAsset,
    val hand: CharacterAsset,
    val skinColor: String,
    val bang: CharacterAsset,
    val backHair: CharacterAsset,
    val eyes: CharacterAsset,
    val mouth: CharacterAsset,
    val home: CharacterAsset,
)

data class Character(
    val face: CharacterAsset,
    val hand: CharacterAsset,
    val skinColor: String,
    val bang: CharacterAsset,
    val backHair: CharacterAsset,
    val eyes: CharacterAsset,
    val mouth: CharacterAsset,
)

data class CharacterAsset(
    val id: String,
    val uri: URI,
)
