package com.bos.backend.application.counterpart

import com.bos.backend.domain.counterpart.entity.Counterpart
import com.bos.backend.domain.counterpart.repository.CounterpartRepository
import com.bos.backend.domain.transaction.entity.CounterpartCharacter
import com.bos.backend.domain.user.entity.CharacterAsset
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.net.URI

class CounterpartServiceTest : StringSpec({

    val counterpartRepository = mockk<CounterpartRepository>()
    val counterpartService = CounterpartService(counterpartRepository)

    val testCharacter =
        CounterpartCharacter(
            face = CharacterAsset("face1", URI("https://example.com/face1.png")),
            hand = CharacterAsset("hand1", URI("https://example.com/hand1.png")),
            skinColor = "#FFFFFF",
            bang = CharacterAsset("bang1", URI("https://example.com/bang1.png")),
            backHair = CharacterAsset("back1", URI("https://example.com/back1.png")),
            eyes = CharacterAsset("eyes1", URI("https://example.com/eyes1.png")),
            mouth = CharacterAsset("mouth1", URI("https://example.com/mouth1.png")),
        )

    "createCounterpart는 유효한 이름과 캐릭터로 새 상대방을 생성한다" {
        // Given
        val userId = 1L
        val name = "홍길동"
        val savedCounterpart =
            Counterpart(
                id = 1L,
                userId = userId,
                name = name,
                character = testCharacter,
            )

        coEvery { counterpartRepository.save(any()) } returns savedCounterpart

        // When
        val result = counterpartService.createCounterpart(userId, name, testCharacter)

        // Then
        result.id shouldBe 1L
        result.name shouldBe name
        result.character shouldBe testCharacter
        coVerify(exactly = 1) { counterpartRepository.save(any()) }
    }

    "createCounterpart는 빈 이름에 대해 예외를 발생시킨다" {
        // Given
        val userId = 1L
        val name = ""

        // When & Then
        shouldThrow<IllegalArgumentException> {
            counterpartService.createCounterpart(userId, name, testCharacter)
        }
    }

    "createCounterpart는 12자를 초과하는 이름에 대해 예외를 발생시킨다" {
        // Given
        val userId = 1L
        val name = "1234567890123" // 13 characters

        // When & Then
        shouldThrow<IllegalArgumentException> {
            counterpartService.createCounterpart(userId, name, testCharacter)
        }
    }

    "findById는 존재하는 상대방을 반환한다" {
        // Given
        val counterpartId = 1L
        val expectedCounterpart =
            Counterpart(
                id = counterpartId,
                userId = 1L,
                name = "홍길동",
                character = testCharacter,
            )

        coEvery { counterpartRepository.findById(counterpartId) } returns expectedCounterpart

        // When
        val result = counterpartService.findById(counterpartId)

        // Then
        result shouldNotBe null
        result?.id shouldBe counterpartId
        result?.name shouldBe "홍길동"
    }

    "updateCounterpartName은 상대방 이름을 업데이트한다" {
        // Given
        val counterpartId = 1L
        val existingCounterpart =
            Counterpart(
                id = counterpartId,
                userId = 1L,
                name = "홍길동",
                character = testCharacter,
            )
        val newName = "김철수"
        val updatedCounterpart = existingCounterpart.copy(name = newName)

        coEvery { counterpartRepository.findById(counterpartId) } returns existingCounterpart
        coEvery { counterpartRepository.update(any()) } returns updatedCounterpart

        // When
        val result = counterpartService.updateCounterpartName(counterpartId, newName)

        // Then
        result.name shouldBe newName
        coVerify(exactly = 1) { counterpartRepository.update(any()) }
    }

    "deleteCounterpart는 거래가 없는 상대방을 삭제한다" {
        // Given
        val counterpartId = 1L

        coEvery { counterpartRepository.countTransactionsByCounterpartId(counterpartId) } returns 0L
        coEvery { counterpartRepository.deleteById(counterpartId) } returns Unit

        // When
        counterpartService.deleteCounterpart(counterpartId)

        // Then
        coVerify(exactly = 1) { counterpartRepository.deleteById(counterpartId) }
    }

    "deleteCounterpart는 거래가 있는 상대방 삭제 시 예외를 발생시킨다" {
        // Given
        val counterpartId = 1L

        coEvery { counterpartRepository.countTransactionsByCounterpartId(counterpartId) } returns 5L

        // When & Then
        shouldThrow<IllegalStateException> {
            counterpartService.deleteCounterpart(counterpartId)
        }
    }
})
