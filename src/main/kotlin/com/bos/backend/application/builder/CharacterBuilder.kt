package com.bos.backend.application.builder

import com.bos.backend.application.service.CharacterAssetService
import com.bos.backend.domain.profile.enums.ProfileAssetType
import com.bos.backend.domain.transaction.entity.CounterpartCharacter
import com.bos.backend.domain.user.entity.CharacterAsset
import com.bos.backend.domain.user.entity.ProfileCharacter
import com.bos.backend.domain.user.factory.CharacterFactory
import com.bos.backend.presentation.transaction.dto.CounterpartCharacterDTO
import com.bos.backend.presentation.user.dto.UpdateCharacterDTO
import org.springframework.stereotype.Component

@Component
class CharacterBuilder(
    private val characterAssetService: CharacterAssetService,
) {
    suspend fun buildCharacter(
        characterDTO: UpdateCharacterDTO,
        currentCharacter: ProfileCharacter?,
    ): ProfileCharacter {
        val baseCharacter = currentCharacter ?: CharacterFactory.createDefaultCharacter(characterAssetService)

        return ProfileCharacter(
            face =
                buildCharacterAsset(
                    dtoValue = characterDTO.face,
                    currentAsset = baseCharacter.face,
                    assetType = ProfileAssetType.FACE,
                ),
            hand =
                buildCharacterAsset(
                    dtoValue = characterDTO.hand,
                    currentAsset = baseCharacter.hand,
                    assetType = ProfileAssetType.HAND,
                ),
            skinColor =
                characterDTO.skinColor ?: baseCharacter.skinColor,
            bang =
                buildCharacterAsset(
                    dtoValue = characterDTO.bang,
                    currentAsset = baseCharacter.bang,
                    assetType = ProfileAssetType.BANG,
                ),
            backHair =
                buildCharacterAsset(
                    dtoValue = characterDTO.backHair,
                    currentAsset = baseCharacter.backHair,
                    assetType = ProfileAssetType.BACK_HAIR,
                ),
            eyes =
                buildCharacterAsset(
                    dtoValue = characterDTO.eyes,
                    currentAsset = baseCharacter.eyes,
                    assetType = ProfileAssetType.EYES,
                ),
            mouth =
                buildCharacterAsset(
                    dtoValue = characterDTO.mouth,
                    currentAsset = baseCharacter.mouth,
                    assetType = ProfileAssetType.MOUTH,
                ),
            home =
                buildCharacterAsset(
                    dtoValue = characterDTO.home,
                    currentAsset = baseCharacter.home,
                    assetType = ProfileAssetType.HOME,
                ),
        )
    }

    suspend fun buildCounterpartCharacter(characterDTO: CounterpartCharacterDTO): CounterpartCharacter =
        CounterpartCharacter(
            face = characterAssetService.createCharacterAsset(characterDTO.face, ProfileAssetType.FACE),
            hand = characterAssetService.createCharacterAsset(characterDTO.hand, ProfileAssetType.HAND),
            skinColor = characterDTO.skinColor,
            bang = characterAssetService.createCharacterAsset(characterDTO.bang, ProfileAssetType.BANG),
            backHair = characterAssetService.createCharacterAsset(characterDTO.backHair, ProfileAssetType.BACK_HAIR),
            eyes = characterAssetService.createCharacterAsset(characterDTO.eyes, ProfileAssetType.EYES),
            mouth = characterAssetService.createCharacterAsset(characterDTO.mouth, ProfileAssetType.MOUTH),
        )

    private suspend fun buildCharacterAsset(
        dtoValue: String?,
        currentAsset: CharacterAsset,
        assetType: ProfileAssetType,
    ): CharacterAsset =
        dtoValue?.let {
            characterAssetService.createCharacterAsset(it, assetType)
        } ?: currentAsset
}
