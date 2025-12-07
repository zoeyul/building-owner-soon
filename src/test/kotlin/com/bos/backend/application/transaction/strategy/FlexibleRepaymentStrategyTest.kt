package com.bos.backend.application.transaction.strategy

import com.bos.backend.application.CommonErrorCode
import com.bos.backend.application.CustomException
import com.bos.backend.domain.transaction.entity.CounterpartCharacter
import com.bos.backend.domain.transaction.entity.RepaymentSchedule
import com.bos.backend.domain.transaction.entity.Transaction
import com.bos.backend.domain.transaction.enum.RelationshipType
import com.bos.backend.domain.transaction.enum.RepaymentStatus
import com.bos.backend.domain.transaction.enum.RepaymentType
import com.bos.backend.domain.transaction.enum.TransactionType
import com.bos.backend.domain.user.entity.CharacterAsset
import com.bos.backend.presentation.transaction.dto.CreateRepaymentRequestDTO
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.net.URI
import java.time.Instant
import java.time.LocalDate

class FlexibleRepaymentStrategyTest : StringSpec({
    val strategy = FlexibleRepaymentStrategy()

    val defaultCharacterAsset = CharacterAsset(id = "default", uri = URI.create("https://example.com"))
    val defaultCounterpartCharacter =
        CounterpartCharacter(
            face = defaultCharacterAsset,
            hand = defaultCharacterAsset,
            skinColor = "#FFFFFF",
            bang = defaultCharacterAsset,
            backHair = defaultCharacterAsset,
            eyes = defaultCharacterAsset,
            mouth = defaultCharacterAsset,
        )

    fun createTransaction(
        totalAmount: BigDecimal,
        completedAmount: BigDecimal = BigDecimal.ZERO,
        initialCompletedAmount: BigDecimal = BigDecimal.ZERO,
    ): Transaction =
        Transaction(
            id = 1L,
            userId = 1L,
            counterpartName = "테스트",
            counterpartCharacter = defaultCounterpartCharacter,
            relationship = RelationshipType.FRIEND,
            customRelationship = null,
            transactionDate = LocalDate.now(),
            totalAmount = totalAmount,
            completedAmount = completedAmount,
            initialCompletedAmount = initialCompletedAmount,
            memo = null,
            repaymentType = RepaymentType.FLEXIBLE,
            targetDate = null,
            monthlyAmount = null,
            paymentDay = null,
            transactionType = TransactionType.BORROW,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    fun createCompletedSchedule(actualAmount: BigDecimal): RepaymentSchedule =
        RepaymentSchedule(
            id = 1L,
            transactionId = 1L,
            scheduledDate = LocalDate.now().minusDays(1),
            scheduledAmount = actualAmount,
            actualDate = LocalDate.now().minusDays(1),
            actualAmount = actualAmount,
            status = RepaymentStatus.COMPLETED,
        )

    "정상 상환 처리 - 새 스케줄 생성 및 완료 처리" {
        val transaction = createTransaction(totalAmount = BigDecimal("100000"))
        val request =
            CreateRepaymentRequestDTO(
                repaymentDate = LocalDate.now(),
                repaymentAmount = BigDecimal("30000"),
            )

        val result =
            runBlocking {
                strategy.processRepayment(transaction, request, emptyList())
            }

        result.completedSchedule.status shouldBe RepaymentStatus.COMPLETED
        result.completedSchedule.actualAmount shouldBe BigDecimal("30000")
        result.completedSchedule.actualDate shouldBe LocalDate.now()
        result.newCompletedAmount shouldBe BigDecimal("30000")
        result.isTransactionCompleted shouldBe false
    }

    "기존 완료된 스케줄이 있는 경우 누적 금액 계산" {
        val transaction =
            createTransaction(
                totalAmount = BigDecimal("100000"),
                completedAmount = BigDecimal("20000"),
                initialCompletedAmount = BigDecimal("10000"),
            )
        val existingSchedules =
            listOf(
                createCompletedSchedule(BigDecimal("10000")),
            )
        val request =
            CreateRepaymentRequestDTO(
                repaymentDate = LocalDate.now(),
                repaymentAmount = BigDecimal("30000"),
            )

        val result =
            runBlocking {
                strategy.processRepayment(transaction, request, existingSchedules)
            }

        // initialCompletedAmount(10000) + existingSchedules(10000) + newRepayment(30000) = 50000
        result.newCompletedAmount shouldBe BigDecimal("50000")
        result.isTransactionCompleted shouldBe false
    }

    "전체 잔여 금액 상환 시 거래 완료 처리" {
        val transaction = createTransaction(totalAmount = BigDecimal("100000"))
        val request =
            CreateRepaymentRequestDTO(
                repaymentDate = LocalDate.now(),
                repaymentAmount = BigDecimal("100000"),
            )

        val result =
            runBlocking {
                strategy.processRepayment(transaction, request, emptyList())
            }

        result.isTransactionCompleted shouldBe true
        result.newCompletedAmount shouldBe BigDecimal("100000")
    }

    "잔여 금액 초과 상환 시 AMOUNT_EXCEEDS_REMAINING 예외" {
        val transaction = createTransaction(totalAmount = BigDecimal("100000"))
        val request =
            CreateRepaymentRequestDTO(
                repaymentDate = LocalDate.now(),
                repaymentAmount = BigDecimal("150000"),
            )

        val exception =
            shouldThrow<CustomException> {
                runBlocking {
                    strategy.processRepayment(transaction, request, emptyList())
                }
            }

        exception.errorCode shouldBe CommonErrorCode.AMOUNT_EXCEEDS_REMAINING.name
    }

    "일부 상환 후 나머지 금액 정확히 상환 시 거래 완료" {
        val transaction =
            createTransaction(
                totalAmount = BigDecimal("100000"),
                initialCompletedAmount = BigDecimal("30000"),
            )
        val existingSchedules =
            listOf(
                createCompletedSchedule(BigDecimal("20000")),
            )
        val request =
            CreateRepaymentRequestDTO(
                repaymentDate = LocalDate.now(),
                repaymentAmount = BigDecimal("50000"),
            )

        val result =
            runBlocking {
                strategy.processRepayment(transaction, request, existingSchedules)
            }

        // initialCompletedAmount(30000) + existingSchedules(20000) + newRepayment(50000) = 100000
        result.newCompletedAmount shouldBe BigDecimal("100000")
        result.isTransactionCompleted shouldBe true
    }

    "adjustedSchedules와 autoCompletedSchedules는 항상 비어있어야 함" {
        val transaction = createTransaction(totalAmount = BigDecimal("100000"))
        val request =
            CreateRepaymentRequestDTO(
                repaymentDate = LocalDate.now(),
                repaymentAmount = BigDecimal("50000"),
            )

        val result =
            runBlocking {
                strategy.processRepayment(transaction, request, emptyList())
            }

        result.adjustedSchedules shouldBe emptyList()
        result.autoCompletedSchedules shouldBe emptyList()
    }
})
