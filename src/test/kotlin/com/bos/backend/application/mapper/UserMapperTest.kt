package com.bos.backend.application.mapper

import com.bos.backend.domain.user.entity.CharacterAsset
import com.bos.backend.domain.user.entity.ProfileCharacter
import com.bos.backend.domain.user.entity.User
import com.bos.backend.domain.user.entity.UserAuthFixture
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.net.URI
import java.time.Instant

class UserMapperTest :
    StringSpec({

        val userMapper = UserMapper.INSTANCE

        "User를 UserProfileDTO로 매핑할 수 있어야 한다" {
            // given
            val user =
                User(
                    id = 1L,
                    nickname = "테스트유저",
                    isNotificationAllowed = true,
                    isMarketingAgreed = false,
                    character =
                        ProfileCharacter(
                            face = CharacterAsset("face_1", URI.create("https://example.com/face.svg")),
                            hand = CharacterAsset("hand_1", URI.create("https://example.com/hand.svg")),
                            skinColor = "#FFDBAC",
                            bang =
                                CharacterAsset(
                                    "front_hair_1",
                                    URI.create("https://example.com/front_hair.svg"),
                                ),
                            backHair =
                                CharacterAsset(
                                    "back_hair_1",
                                    URI.create("https://example.com/back_hair.svg"),
                                ),
                            eyes = CharacterAsset("eyes_1", URI.create("https://example.com/eyes.svg")),
                            mouth = CharacterAsset("mouth_1", URI.create("https://example.com/mouth.svg")),
                            home = CharacterAsset("home_1", URI.create("https://example.com/home.svg")),
                        ),
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            val userAuth = UserAuthFixture.defaultUserAuthFixture()

            // when
            val result = userMapper.toUserProfileDTO(user, userAuth)

            // then
            result.id shouldBe 1L
            result.nickname shouldBe "테스트유저"
            result.isNotificationAllowed shouldBe true
            result.isMarketingAgreed shouldBe false
            result.character shouldNotBe null
            result.character?.face?.id shouldBe "face_1"
            result.character?.home?.id shouldBe "home_1"
            result.createdAt shouldBe user.createdAt
            result.updatedAt shouldBe user.updatedAt
        }

        "기본 Character를 가진 User도 매핑할 수 있어야 한다" {
            // given
            val user =
                User(
                    id = 2L,
                    nickname = "기본캐릭터유저",
                    isNotificationAllowed = false,
                    isMarketingAgreed = true,
                    character =
                        ProfileCharacter(
                            face = CharacterAsset("FACE_TYPE_1", URI.create("https://example.com/face.svg")),
                            hand = CharacterAsset("HAND_TYPE_1", URI.create("https://example.com/hand.svg")),
                            skinColor = "#FFFFFF",
                            bang =
                                CharacterAsset(
                                    "BANG_TYPE_1",
                                    URI.create("https://example.com/bang.svg"),
                                ),
                            backHair =
                                CharacterAsset(
                                    "BACK_HAIR_TYPE_1",
                                    URI.create("https://example.com/back_hair.svg"),
                                ),
                            eyes = CharacterAsset("EYES_TYPE_1", URI.create("https://example.com/eyes.svg")),
                            mouth = CharacterAsset("MOUTH_TYPE_1", URI.create("https://example.com/mouth.svg")),
                            home = CharacterAsset("HOME_TYPE_1", URI.create("https://example.com/home.svg")),
                        ),
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            val userAuth = UserAuthFixture.defaultUserAuthFixture()
            // when
            val result = userMapper.toUserProfileDTO(user, userAuth)

            // then
            result.id shouldBe 2L
            result.nickname shouldBe "기본캐릭터유저"
            result.character shouldNotBe null
            result.character?.face?.id shouldBe "FACE_TYPE_1"
            result.character?.skinColor shouldBe "#FFFFFF"
        }
    })
