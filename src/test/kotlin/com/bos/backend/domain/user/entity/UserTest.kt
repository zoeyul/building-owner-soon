package com.bos.backend.domain.user.entity

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.net.URI
import java.time.Instant

class UserTest :
    StringSpec({
        val sut =
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

        "유저는 초기화 시 삭제되지 않은 상태여야 한다" {
            sut.isDeleted() shouldBe false
        }

        "delete 호출시 삭제된 상태를 확인할 수 있어야 한다" {
            val deletedUser = sut.delete()
            deletedUser.isDeleted() shouldBe true
        }
    })
