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

class ScheduledRepaymentStrategyTest : StringSpec({
    val strategy = ScheduledRepaymentStrategy()

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
        repaymentType: RepaymentType = RepaymentType.DIVIDED_BY_PERIOD,
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
            repaymentType = repaymentType,
            targetDate = null,
            monthlyAmount = null,
            paymentDay = null,
            transactionType = TransactionType.BORROW,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    fun createSchedule(
        id: Long,
        status: RepaymentStatus,
        scheduledAmount: BigDecimal,
        scheduledDate: LocalDate = LocalDate.now(),
        actualAmount: BigDecimal? = null,
    ): RepaymentSchedule =
        RepaymentSchedule(
            id = id,
            transactionId = 1L,
            scheduledDate = scheduledDate,
            scheduledAmount = scheduledAmount,
            actualDate = if (status == RepaymentStatus.COMPLETED) scheduledDate else null,
            actualAmount = actualAmount,
            status = status,
        )

    // 상태 검증 테스트
    "OVERDUE 상태 스케줄 상환 성공" {
        val transaction = createTransaction(totalAmount = BigDecimal("300000"))
        val schedules =
            listOf(
                createSchedule(1L, RepaymentStatus.OVERDUE, BigDecimal("100000")),
                createSchedule(2L, RepaymentStatus.SCHEDULED, BigDecimal("100000"), LocalDate.now().plusMonths(1)),
                createSchedule(3L, RepaymentStatus.SCHEDULED, BigDecimal("100000"), LocalDate.now().plusMonths(2)),
            )
        val request =
            CreateRepaymentRequestDTO(
                scheduleId = 1L,
                repaymentDate = LocalDate.now(),
                repaymentAmount = BigDecimal("100000"),
            )

        val result =
            runBlocking {
                strategy.processRepayment(transaction, request, schedules)
            }

        result.completedSchedule.status shouldBe RepaymentStatus.COMPLETED
        result.completedSchedule.actualAmount shouldBe BigDecimal("100000")
    }

    "IN_PROGRESS 상태 스케줄 상환 성공" {
        val transaction = createTransaction(totalAmount = BigDecimal("300000"))
        val schedules =
            listOf(
                createSchedule(1L, RepaymentStatus.IN_PROGRESS, BigDecimal("100000")),
                createSchedule(2L, RepaymentStatus.SCHEDULED, BigDecimal("100000"), LocalDate.now().plusMonths(1)),
                createSchedule(3L, RepaymentStatus.SCHEDULED, BigDecimal("100000"), LocalDate.now().plusMonths(2)),
            )
        val request =
            CreateRepaymentRequestDTO(
                scheduleId = 1L,
                repaymentDate = LocalDate.now(),
                repaymentAmount = BigDecimal("100000"),
            )

        val result =
            runBlocking {
                strategy.processRepayment(transaction, request, schedules)
            }

        result.completedSchedule.status shouldBe RepaymentStatus.COMPLETED
    }

    "SCHEDULED 상태 스케줄 상환 시 REPAYMENT_NOT_ALLOWED 예외" {
        val transaction = createTransaction(totalAmount = BigDecimal("300000"))
        val schedules =
            listOf(
                createSchedule(1L, RepaymentStatus.SCHEDULED, BigDecimal("100000")),
            )
        val request =
            CreateRepaymentRequestDTO(
                scheduleId = 1L,
                repaymentDate = LocalDate.now(),
                repaymentAmount = BigDecimal("100000"),
            )

        val exception =
            shouldThrow<CustomException> {
                runBlocking {
                    strategy.processRepayment(transaction, request, schedules)
                }
            }

        exception.errorCode shouldBe CommonErrorCode.REPAYMENT_NOT_ALLOWED.name
    }

    "COMPLETED 상태 스케줄 상환 시 REPAYMENT_ALREADY_COMPLETED 예외" {
        val transaction = createTransaction(totalAmount = BigDecimal("300000"))
        val schedules =
            listOf(
                createSchedule(
                    1L,
                    RepaymentStatus.COMPLETED,
                    BigDecimal("100000"),
                    actualAmount = BigDecimal("100000"),
                ),
            )
        val request =
            CreateRepaymentRequestDTO(
                scheduleId = 1L,
                repaymentDate = LocalDate.now(),
                repaymentAmount = BigDecimal("100000"),
            )

        val exception =
            shouldThrow<CustomException> {
                runBlocking {
                    strategy.processRepayment(transaction, request, schedules)
                }
            }

        exception.errorCode shouldBe CommonErrorCode.REPAYMENT_ALREADY_COMPLETED.name
    }

    "존재하지 않는 scheduleId 예외" {
        val transaction = createTransaction(totalAmount = BigDecimal("300000"))
        val schedules =
            listOf(
                createSchedule(1L, RepaymentStatus.IN_PROGRESS, BigDecimal("100000")),
            )
        val request =
            CreateRepaymentRequestDTO(
                scheduleId = 999L,
                repaymentDate = LocalDate.now(),
                repaymentAmount = BigDecimal("100000"),
            )

        val exception =
            shouldThrow<CustomException> {
                runBlocking {
                    strategy.processRepayment(transaction, request, schedules)
                }
            }

        exception.errorCode shouldBe CommonErrorCode.RESOURCE_NOT_FOUND.name
    }

    "scheduleId 누락 시 SCHEDULE_ID_REQUIRED 예외" {
        val transaction = createTransaction(totalAmount = BigDecimal("300000"))
        val schedules =
            listOf(
                createSchedule(1L, RepaymentStatus.IN_PROGRESS, BigDecimal("100000")),
            )
        val request =
            CreateRepaymentRequestDTO(
                scheduleId = null,
                repaymentDate = LocalDate.now(),
                repaymentAmount = BigDecimal("100000"),
            )

        val exception =
            shouldThrow<CustomException> {
                runBlocking {
                    strategy.processRepayment(transaction, request, schedules)
                }
            }

        exception.errorCode shouldBe CommonErrorCode.SCHEDULE_ID_REQUIRED.name
    }

    // 정확한 금액 상환
    "예정 금액과 동일한 금액 상환 - 해당 스케줄만 완료, 재분배 없음" {
        val transaction = createTransaction(totalAmount = BigDecimal("300000"))
        val schedules =
            listOf(
                createSchedule(1L, RepaymentStatus.IN_PROGRESS, BigDecimal("100000")),
                createSchedule(2L, RepaymentStatus.SCHEDULED, BigDecimal("100000"), LocalDate.now().plusMonths(1)),
                createSchedule(3L, RepaymentStatus.SCHEDULED, BigDecimal("100000"), LocalDate.now().plusMonths(2)),
            )
        val request =
            CreateRepaymentRequestDTO(
                scheduleId = 1L,
                repaymentDate = LocalDate.now(),
                repaymentAmount = BigDecimal("100000"),
            )

        val result =
            runBlocking {
                strategy.processRepayment(transaction, request, schedules)
            }

        result.completedSchedule.actualAmount shouldBe BigDecimal("100000")
        result.newCompletedAmount shouldBe BigDecimal("100000")
        result.isTransactionCompleted shouldBe false
        // 정확한 금액이므로 재분배 발생하지만 금액은 동일
        result.adjustedSchedules.size shouldBe 2
    }

    // 부분 상환 테스트
    "3개 스케줄 중 첫번째에서 50% 상환 시 나머지 2개 균등 재분배" {
        val transaction = createTransaction(totalAmount = BigDecimal("300000"))
        val schedules =
            listOf(
                createSchedule(1L, RepaymentStatus.IN_PROGRESS, BigDecimal("100000")),
                createSchedule(2L, RepaymentStatus.SCHEDULED, BigDecimal("100000"), LocalDate.now().plusMonths(1)),
                createSchedule(3L, RepaymentStatus.SCHEDULED, BigDecimal("100000"), LocalDate.now().plusMonths(2)),
            )
        val request =
            CreateRepaymentRequestDTO(
                scheduleId = 1L,
                repaymentDate = LocalDate.now(),
                repaymentAmount = BigDecimal("50000"),
            )

        val result =
            runBlocking {
                strategy.processRepayment(transaction, request, schedules)
            }

        result.completedSchedule.actualAmount shouldBe BigDecimal("50000")
        result.newCompletedAmount shouldBe BigDecimal("50000")

        // 남은 금액 250000을 2개 스케줄에 분배
        // 250000 / 2 = 125000 (정확히 나눠짐)
        result.adjustedSchedules.size shouldBe 2
        result.adjustedSchedules[0].scheduledAmount shouldBe BigDecimal("125000")
        result.adjustedSchedules[1].scheduledAmount shouldBe BigDecimal("125000")
    }

    "100원 단위 절삭 적용 확인" {
        val transaction = createTransaction(totalAmount = BigDecimal("300000"))
        val schedules =
            listOf(
                createSchedule(1L, RepaymentStatus.IN_PROGRESS, BigDecimal("100000")),
                createSchedule(2L, RepaymentStatus.SCHEDULED, BigDecimal("100000"), LocalDate.now().plusMonths(1)),
                createSchedule(3L, RepaymentStatus.SCHEDULED, BigDecimal("100000"), LocalDate.now().plusMonths(2)),
            )
        val request =
            CreateRepaymentRequestDTO(
                scheduleId = 1L,
                repaymentDate = LocalDate.now(),
                repaymentAmount = BigDecimal("33333"),
            )

        val result =
            runBlocking {
                strategy.processRepayment(transaction, request, schedules)
            }

        // 남은 금액: 300000 - 33333 = 266667
        // 266667 / 2 = 133333.5 -> 133300 (100원 단위 절삭)
        // 마지막 스케줄: 266667 - 133300 = 133367
        result.adjustedSchedules.size shouldBe 2
        result.adjustedSchedules[0].scheduledAmount shouldBe BigDecimal("133300")
        result.adjustedSchedules[1].scheduledAmount shouldBe BigDecimal("133367")
    }

    // 초과 상환 테스트
    "예정 금액보다 많이 상환 시 나머지 스케줄 금액 감소" {
        val transaction = createTransaction(totalAmount = BigDecimal("300000"))
        val schedules =
            listOf(
                createSchedule(1L, RepaymentStatus.IN_PROGRESS, BigDecimal("100000")),
                createSchedule(2L, RepaymentStatus.SCHEDULED, BigDecimal("100000"), LocalDate.now().plusMonths(1)),
                createSchedule(3L, RepaymentStatus.SCHEDULED, BigDecimal("100000"), LocalDate.now().plusMonths(2)),
            )
        val request =
            CreateRepaymentRequestDTO(
                scheduleId = 1L,
                repaymentDate = LocalDate.now(),
                repaymentAmount = BigDecimal("150000"),
            )

        val result =
            runBlocking {
                strategy.processRepayment(transaction, request, schedules)
            }

        // 남은 금액: 300000 - 150000 = 150000
        // 150000 / 2 = 75000
        result.adjustedSchedules.size shouldBe 2
        result.adjustedSchedules[0].scheduledAmount shouldBe BigDecimal("75000")
        result.adjustedSchedules[1].scheduledAmount shouldBe BigDecimal("75000")
    }

    // 전체 상환 완료 테스트
    "totalAmount 이상 상환 시 남은 스케줄 모두 자동 완료" {
        val transaction = createTransaction(totalAmount = BigDecimal("300000"))
        val schedules =
            listOf(
                createSchedule(1L, RepaymentStatus.IN_PROGRESS, BigDecimal("100000")),
                createSchedule(2L, RepaymentStatus.SCHEDULED, BigDecimal("100000"), LocalDate.now().plusMonths(1)),
                createSchedule(3L, RepaymentStatus.SCHEDULED, BigDecimal("100000"), LocalDate.now().plusMonths(2)),
            )
        val request =
            CreateRepaymentRequestDTO(
                scheduleId = 1L,
                repaymentDate = LocalDate.now(),
                repaymentAmount = BigDecimal("300000"),
            )

        val result =
            runBlocking {
                strategy.processRepayment(transaction, request, schedules)
            }

        result.completedSchedule.actualAmount shouldBe BigDecimal("300000")
        result.isTransactionCompleted shouldBe true
        result.newCompletedAmount shouldBe BigDecimal("300000")

        // 나머지 2개 스케줄은 자동 완료
        result.autoCompletedSchedules.size shouldBe 2
        result.autoCompletedSchedules.all { it.status == RepaymentStatus.COMPLETED } shouldBe true
        result.autoCompletedSchedules.all { it.actualAmount == BigDecimal.ZERO } shouldBe true

        // adjustedSchedules는 비어있어야 함
        result.adjustedSchedules shouldBe emptyList()
    }

    "이미 일부 완료된 상태에서 전체 상환 시 거래 완료" {
        val transaction =
            createTransaction(
                totalAmount = BigDecimal("300000"),
                initialCompletedAmount = BigDecimal("50000"),
            )
        val schedules =
            listOf(
                createSchedule(1L, RepaymentStatus.COMPLETED, BigDecimal("100000"), actualAmount = BigDecimal("50000")),
                createSchedule(2L, RepaymentStatus.IN_PROGRESS, BigDecimal("100000"), LocalDate.now().plusMonths(1)),
                createSchedule(3L, RepaymentStatus.SCHEDULED, BigDecimal("100000"), LocalDate.now().plusMonths(2)),
            )
        val request =
            CreateRepaymentRequestDTO(
                scheduleId = 2L,
                repaymentDate = LocalDate.now(),
                repaymentAmount = BigDecimal("200000"),
            )

        val result =
            runBlocking {
                strategy.processRepayment(transaction, request, schedules)
            }

        // initialCompletedAmount(50000) + completed(50000) + newRepayment(200000) = 300000
        result.isTransactionCompleted shouldBe true
        result.newCompletedAmount shouldBe BigDecimal("300000")
        result.autoCompletedSchedules.size shouldBe 1
    }

    "마지막 스케줄 상환 시 거래 완료" {
        val transaction = createTransaction(totalAmount = BigDecimal("300000"))
        val schedules =
            listOf(
                createSchedule(
                    1L,
                    RepaymentStatus.COMPLETED,
                    BigDecimal("100000"),
                    actualAmount = BigDecimal("100000"),
                ),
                createSchedule(
                    2L,
                    RepaymentStatus.COMPLETED,
                    BigDecimal("100000"),
                    actualAmount = BigDecimal("100000"),
                ),
                createSchedule(3L, RepaymentStatus.IN_PROGRESS, BigDecimal("100000")),
            )
        val request =
            CreateRepaymentRequestDTO(
                scheduleId = 3L,
                repaymentDate = LocalDate.now(),
                repaymentAmount = BigDecimal("100000"),
            )

        val result =
            runBlocking {
                strategy.processRepayment(transaction, request, schedules)
            }

        result.isTransactionCompleted shouldBe true
        result.newCompletedAmount shouldBe BigDecimal("300000")
        result.adjustedSchedules shouldBe emptyList()
        result.autoCompletedSchedules shouldBe emptyList()
    }

    "FIXED_MONTHLY 타입도 동일하게 동작" {
        val transaction =
            createTransaction(
                totalAmount = BigDecimal("300000"),
                repaymentType = RepaymentType.FIXED_MONTHLY,
            )
        val schedules =
            listOf(
                createSchedule(1L, RepaymentStatus.IN_PROGRESS, BigDecimal("100000")),
                createSchedule(2L, RepaymentStatus.SCHEDULED, BigDecimal("100000"), LocalDate.now().plusMonths(1)),
            )
        val request =
            CreateRepaymentRequestDTO(
                scheduleId = 1L,
                repaymentDate = LocalDate.now(),
                repaymentAmount = BigDecimal("100000"),
            )

        val result =
            runBlocking {
                strategy.processRepayment(transaction, request, schedules)
            }

        result.completedSchedule.status shouldBe RepaymentStatus.COMPLETED
        result.completedSchedule.actualAmount shouldBe BigDecimal("100000")
    }
})
