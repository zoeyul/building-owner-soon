package com.bos.backend.application.builder

import com.bos.backend.application.service.CharacterAssetService
import com.bos.backend.domain.profile.enums.ProfileAssetType
import com.bos.backend.domain.user.entity.CharacterAsset
import com.bos.backend.domain.user.entity.ProfileCharacter
import com.bos.backend.domain.user.factory.CharacterFactory
import com.bos.backend.presentation.user.dto.UpdateCharacterDTO
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import java.net.URI

class CharacterBuilderTest : StringSpec({

    val characterAssetService = mockk<CharacterAssetService>()
    val characterBuilder = CharacterBuilder(characterAssetService)

    "새로운 캐릭터를 완전히 새로 생성할 때 모든 기본값을 사용한다" {
        // given
        val characterDTO = UpdateCharacterDTO()
        val currentCharacter: ProfileCharacter? = null

        val defaultAssets =
            mapOf(
                ProfileAssetType.FACE to CharacterAsset("FACE_TYPE_1", URI.create("https://example.com/face.svg")),
                ProfileAssetType.HAND to CharacterAsset("HAND_TYPE_1", URI.create("https://example.com/hand.svg")),
                ProfileAssetType.BANG to CharacterAsset("BANG_TYPE_1", URI.create("https://example.com/bang.svg")),
                ProfileAssetType.BACK_HAIR to
                    CharacterAsset("BACK_HAIR_TYPE_1", URI.create("https://example.com/back_hair.svg")),
                ProfileAssetType.EYES to CharacterAsset("EYES_TYPE_1", URI.create("https://example.com/eyes.svg")),
                ProfileAssetType.MOUTH to CharacterAsset("MOUTH_TYPE_1", URI.create("https://example.com/mouth.svg")),
                ProfileAssetType.HOME to CharacterAsset("HOME_TYPE_1", URI.create("https://example.com/home.svg")),
            )

        defaultAssets.forEach { (assetType, asset) ->
            coEvery {
                characterAssetService.createCharacterAsset(asset.id, assetType)
            } returns asset
        }

        // when
        val result = runBlocking { characterBuilder.buildCharacter(characterDTO, currentCharacter) }

        // then
        result.face.id shouldBe CharacterFactory.DefaultIds.FACE
        result.hand.id shouldBe CharacterFactory.DefaultIds.HAND
        result.bang.id shouldBe CharacterFactory.DefaultIds.BANG
        result.backHair.id shouldBe CharacterFactory.DefaultIds.BACK_HAIR
        result.eyes.id shouldBe CharacterFactory.DefaultIds.EYES
        result.mouth.id shouldBe CharacterFactory.DefaultIds.MOUTH
        result.home.id shouldBe CharacterFactory.DefaultIds.HOME

        coVerify(exactly = 7) { characterAssetService.createCharacterAsset(any(), any()) }
    }

    "기존 캐릭터가 있을 때 DTO에서 제공된 값만 업데이트한다" {
        // given
        val existingCharacter =
            ProfileCharacter(
                face = CharacterAsset("OLD_FACE", URI.create("https://example.com/old_face.svg")),
                hand = CharacterAsset("OLD_HAND", URI.create("https://example.com/old_hand.svg")),
                skinColor = "#AABBCC",
                bang = CharacterAsset("OLD_BANG", URI.create("https://example.com/old_bang.svg")),
                backHair = CharacterAsset("OLD_BACK_HAIR", URI.create("https://example.com/old_back_hair.svg")),
                eyes = CharacterAsset("OLD_EYES", URI.create("https://example.com/old_eyes.svg")),
                mouth = CharacterAsset("OLD_MOUTH", URI.create("https://example.com/old_mouth.svg")),
                home = CharacterAsset("OLD_HOME", URI.create("https://example.com/old_home.svg")),
            )

        val characterDTO =
            UpdateCharacterDTO(
                face = "NEW_FACE",
                skinColor = "#DDEEFF",
            )

        val newFaceAsset = CharacterAsset("NEW_FACE", URI.create("https://example.com/new_face.svg"))
        coEvery { characterAssetService.createCharacterAsset("NEW_FACE", ProfileAssetType.FACE) } returns newFaceAsset

        // when
        val result = runBlocking { characterBuilder.buildCharacter(characterDTO, existingCharacter) }

        // then
        result.face.id shouldBe "NEW_FACE"
        result.hand.id shouldBe "OLD_HAND" // 변경되지 않음
        result.skinColor shouldBe "#DDEEFF"
        result.bang.id shouldBe "OLD_BANG" // 변경되지 않음
        result.backHair.id shouldBe "OLD_BACK_HAIR" // 변경되지 않음
        result.eyes.id shouldBe "OLD_EYES" // 변경되지 않음
        result.mouth.id shouldBe "OLD_MOUTH" // 변경되지 않음
        result.home.id shouldBe "OLD_HOME" // 변경되지 않음

        coVerify(exactly = 1) { characterAssetService.createCharacterAsset("NEW_FACE", ProfileAssetType.FACE) }
    }
})
