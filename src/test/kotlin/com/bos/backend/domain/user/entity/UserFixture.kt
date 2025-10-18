package com.bos.backend.domain.user.entity

import java.net.URI
import java.time.Instant

object UserFixture {
    fun defaultUserFixture() =
        User(
            id = 1L,
            nickname = "홍길동",
            character =
                ProfileCharacter(
                    face = CharacterAsset("FACE_TYPE_1", URI.create("https://example.com/face.svg")),
                    hand = CharacterAsset("HAND_TYPE_1", URI.create("https://example.com/hand.svg")),
                    skinColor = "#FFFFFF",
                    bang = CharacterAsset("BANG_TYPE_1", URI.create("https://example.com/bang.svg")),
                    backHair = CharacterAsset("BACK_HAIR_TYPE_1", URI.create("https://example.com/back_hair.svg")),
                    eyes = CharacterAsset("EYES_TYPE_1", URI.create("https://example.com/eyes.svg")),
                    mouth = CharacterAsset("MOUTH_TYPE_1", URI.create("https://example.com/mouth.svg")),
                    home = CharacterAsset("HOME_TYPE_1", URI.create("https://example.com/home.svg")),
                ),
            isNotificationAllowed = true,
            isMarketingAgreed = false,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            deletedAt = null,
        )
}
