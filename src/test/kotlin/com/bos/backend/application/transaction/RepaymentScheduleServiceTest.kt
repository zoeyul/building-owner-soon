package com.bos.backend.application.transaction

import com.bos.backend.application.CommonErrorCode
import com.bos.backend.application.CustomException
import com.bos.backend.application.notification.NotificationService
import com.bos.backend.application.push.ExpoPushService
import com.bos.backend.application.push.PushTemplateService
import com.bos.backend.application.transaction.strategy.FlexibleRepaymentStrategy
import com.bos.backend.application.transaction.strategy.RepaymentStrategyFactory
import com.bos.backend.application.transaction.strategy.ScheduledRepaymentStrategy
import com.bos.backend.domain.transaction.entity.CounterpartCharacter
import com.bos.backend.domain.transaction.entity.RepaymentSchedule
import com.bos.backend.domain.transaction.entity.Transaction
import com.bos.backend.domain.transaction.enum.RelationshipType
import com.bos.backend.domain.transaction.enum.RepaymentStatus
import com.bos.backend.domain.transaction.enum.RepaymentType
import com.bos.backend.domain.transaction.enum.TransactionType
import com.bos.backend.domain.transaction.repository.RepaymentScheduleRepository
import com.bos.backend.domain.transaction.repository.TransactionRepository
import com.bos.backend.domain.user.entity.CharacterAsset
import com.bos.backend.domain.user.repository.UserDeviceRepository
import com.bos.backend.domain.user.repository.UserRepository
import com.bos.backend.presentation.transaction.dto.CreateRepaymentRequestDTO
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigDecimal
import java.net.URI
import java.time.Instant
import java.time.LocalDate

@Suppress("LongParameterList")
class RepaymentScheduleServiceTest : StringSpec({
    val repaymentScheduleRepository = mockk<RepaymentScheduleRepository>()
    val transactionRepository = mockk<TransactionRepository>()
    val notificationService = mockk<NotificationService>()
    val expoPushService = mockk<ExpoPushService>()
    val pushTemplateService = mockk<PushTemplateService>()
    val userDeviceRepository = mockk<UserDeviceRepository>()
    val userRepository = mockk<UserRepository>()
    val objectMapper = mockk<ObjectMapper>()

    val flexibleRepaymentStrategy = FlexibleRepaymentStrategy()
    val scheduledRepaymentStrategy = ScheduledRepaymentStrategy()
    val repaymentStrategyFactory =
        RepaymentStrategyFactory(
            flexibleRepaymentStrategy,
            scheduledRepaymentStrategy,
        )

    val service =
        RepaymentScheduleService(
            repaymentScheduleRepository = repaymentScheduleRepository,
            transactionRepository = transactionRepository,
            notificationService = notificationService,
            expoPushService = expoPushService,
            pushTemplateService = pushTemplateService,
            userDeviceRepository = userDeviceRepository,
            userRepository = userRepository,
            objectMapper = objectMapper,
            repaymentStrategyFactory = repaymentStrategyFactory,
        )

    beforeEach {
        clearMocks(
            repaymentScheduleRepository,
            transactionRepository,
            notificationService,
            expoPushService,
            pushTemplateService,
            userDeviceRepository,
            userRepository,
        )
    }

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
        id: Long = 1L,
        userId: Long = 1L,
        totalAmount: BigDecimal = BigDecimal("300000"),
        completedAmount: BigDecimal = BigDecimal.ZERO,
        initialCompletedAmount: BigDecimal = BigDecimal.ZERO,
        repaymentType: RepaymentType = RepaymentType.DIVIDED_BY_PERIOD,
    ): Transaction =
        Transaction(
            id = id,
            userId = userId,
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
        transactionId: Long = 1L,
        status: RepaymentStatus = RepaymentStatus.IN_PROGRESS,
        scheduledAmount: BigDecimal = BigDecimal("100000"),
        scheduledDate: LocalDate = LocalDate.now(),
    ): RepaymentSchedule =
        RepaymentSchedule(
            id = id,
            transactionId = transactionId,
            scheduledDate = scheduledDate,
            scheduledAmount = scheduledAmount,
            status = status,
        )

    // 라우팅 테스트
    "FLEXIBLE 타입은 FlexibleRepaymentStrategy를 사용한다" {
        val transaction = createTransaction(repaymentType = RepaymentType.FLEXIBLE)
        val request =
            CreateRepaymentRequestDTO(
                repaymentDate = LocalDate.now(),
                repaymentAmount = BigDecimal("50000"),
            )

        coEvery { transactionRepository.findById(1L) } returns transaction
        coEvery { repaymentScheduleRepository.findByTransactionId(1L) } returns emptyList()
        coEvery { repaymentScheduleRepository.save(any()) } answers {
            val schedule = firstArg<RepaymentSchedule>()
            schedule.copy(id = schedule.id ?: 100L)
        }
        coEvery { transactionRepository.save(any()) } answers { firstArg() }

        val result = service.processRepayment(1L, 1L, request)

        result.status shouldBe RepaymentStatus.COMPLETED
        result.displayAmount shouldBe BigDecimal("50000")
    }

    "DIVIDED_BY_PERIOD 타입은 ScheduledRepaymentStrategy를 사용한다" {
        val transaction = createTransaction(repaymentType = RepaymentType.DIVIDED_BY_PERIOD)
        val schedules =
            listOf(
                createSchedule(1L, status = RepaymentStatus.IN_PROGRESS),
                createSchedule(2L, status = RepaymentStatus.SCHEDULED, scheduledDate = LocalDate.now().plusMonths(1)),
            )
        val request =
            CreateRepaymentRequestDTO(
                scheduleId = 1L,
                repaymentDate = LocalDate.now(),
                repaymentAmount = BigDecimal("100000"),
            )

        coEvery { transactionRepository.findById(1L) } returns transaction
        coEvery { repaymentScheduleRepository.findByTransactionId(1L) } returns schedules
        coEvery { repaymentScheduleRepository.save(any()) } answers { firstArg() }
        coEvery { repaymentScheduleRepository.saveAll(any<List<RepaymentSchedule>>()) } returns emptyList()
        coEvery { transactionRepository.save(any()) } answers { firstArg() }

        val result = service.processRepayment(1L, 1L, request)

        result.status shouldBe RepaymentStatus.COMPLETED
    }

    "FIXED_MONTHLY 타입은 ScheduledRepaymentStrategy를 사용한다" {
        val transaction = createTransaction(repaymentType = RepaymentType.FIXED_MONTHLY)
        val schedules =
            listOf(
                createSchedule(1L, status = RepaymentStatus.IN_PROGRESS),
            )
        val request =
            CreateRepaymentRequestDTO(
                scheduleId = 1L,
                repaymentDate = LocalDate.now(),
                repaymentAmount = BigDecimal("100000"),
            )

        coEvery { transactionRepository.findById(1L) } returns transaction
        coEvery { repaymentScheduleRepository.findByTransactionId(1L) } returns schedules
        coEvery { repaymentScheduleRepository.save(any()) } answers { firstArg() }
        coEvery { transactionRepository.save(any()) } answers { firstArg() }

        val result = service.processRepayment(1L, 1L, request)

        result.status shouldBe RepaymentStatus.COMPLETED
    }

    // 검증 테스트
    "FLEXIBLE 아닌데 scheduleId 누락 시 SCHEDULE_ID_REQUIRED 예외" {
        val transaction = createTransaction(repaymentType = RepaymentType.DIVIDED_BY_PERIOD)
        val request =
            CreateRepaymentRequestDTO(
                scheduleId = null,
                repaymentDate = LocalDate.now(),
                repaymentAmount = BigDecimal("100000"),
            )

        coEvery { transactionRepository.findById(1L) } returns transaction

        val exception =
            shouldThrow<CustomException> {
                service.processRepayment(1L, 1L, request)
            }

        exception.errorCode shouldBe CommonErrorCode.SCHEDULE_ID_REQUIRED.name
    }

    "FLEXIBLE 타입은 scheduleId 없어도 성공" {
        val transaction = createTransaction(repaymentType = RepaymentType.FLEXIBLE)
        val request =
            CreateRepaymentRequestDTO(
                scheduleId = null,
                repaymentDate = LocalDate.now(),
                repaymentAmount = BigDecimal("50000"),
            )

        coEvery { transactionRepository.findById(1L) } returns transaction
        coEvery { repaymentScheduleRepository.findByTransactionId(1L) } returns emptyList()
        coEvery { repaymentScheduleRepository.save(any()) } answers {
            val schedule = firstArg<RepaymentSchedule>()
            schedule.copy(id = schedule.id ?: 100L)
        }
        coEvery { transactionRepository.save(any()) } answers { firstArg() }

        val result = service.processRepayment(1L, 1L, request)

        result.status shouldBe RepaymentStatus.COMPLETED
    }

    "트랜잭션 소유자가 아닌 경우 RESOURCE_NOT_FOUND 예외" {
        val transaction = createTransaction(userId = 999L)

        coEvery { transactionRepository.findById(1L) } returns transaction

        val exception =
            shouldThrow<CustomException> {
                service.processRepayment(
                    1L,
                    1L,
                    CreateRepaymentRequestDTO(
                        repaymentDate = LocalDate.now(),
                        repaymentAmount = BigDecimal("100000"),
                    ),
                )
            }

        exception.errorCode shouldBe CommonErrorCode.RESOURCE_NOT_FOUND.name
    }

    "존재하지 않는 트랜잭션 예외" {
        coEvery { transactionRepository.findById(999L) } returns null

        val exception =
            shouldThrow<CustomException> {
                service.processRepayment(
                    1L,
                    999L,
                    CreateRepaymentRequestDTO(
                        repaymentDate = LocalDate.now(),
                        repaymentAmount = BigDecimal("100000"),
                    ),
                )
            }

        exception.errorCode shouldBe CommonErrorCode.RESOURCE_NOT_FOUND.name
    }

    // 통합 동작
    "상환 완료 후 트랜잭션 completedAmount 업데이트" {
        val transaction = createTransaction(repaymentType = RepaymentType.FLEXIBLE)
        val request =
            CreateRepaymentRequestDTO(
                repaymentDate = LocalDate.now(),
                repaymentAmount = BigDecimal("50000"),
            )

        coEvery { transactionRepository.findById(1L) } returns transaction
        coEvery { repaymentScheduleRepository.findByTransactionId(1L) } returns emptyList()
        coEvery { repaymentScheduleRepository.save(any()) } answers {
            val schedule = firstArg<RepaymentSchedule>()
            schedule.copy(id = schedule.id ?: 100L)
        }
        coEvery { transactionRepository.save(any()) } answers { firstArg() }

        service.processRepayment(1L, 1L, request)

        coVerify {
            transactionRepository.save(match { it.completedAmount == BigDecimal("50000") })
        }
    }

    "조정된 스케줄이 있으면 saveAll 호출" {
        val transaction = createTransaction(repaymentType = RepaymentType.DIVIDED_BY_PERIOD)
        val schedules =
            listOf(
                createSchedule(1L, status = RepaymentStatus.IN_PROGRESS),
                createSchedule(2L, status = RepaymentStatus.SCHEDULED, scheduledDate = LocalDate.now().plusMonths(1)),
                createSchedule(3L, status = RepaymentStatus.SCHEDULED, scheduledDate = LocalDate.now().plusMonths(2)),
            )
        val request =
            CreateRepaymentRequestDTO(
                scheduleId = 1L,
                repaymentDate = LocalDate.now(),
                repaymentAmount = BigDecimal("50000"),
            )

        coEvery { transactionRepository.findById(1L) } returns transaction
        coEvery { repaymentScheduleRepository.findByTransactionId(1L) } returns schedules
        coEvery { repaymentScheduleRepository.save(any()) } answers { firstArg() }
        coEvery { repaymentScheduleRepository.saveAll(any<List<RepaymentSchedule>>()) } returns emptyList()
        coEvery { transactionRepository.save(any()) } answers { firstArg() }

        service.processRepayment(1L, 1L, request)

        coVerify(exactly = 1) {
            repaymentScheduleRepository.saveAll(any<List<RepaymentSchedule>>())
        }
    }

    "자동 완료된 스케줄이 있으면 saveAll 호출" {
        val transaction = createTransaction(repaymentType = RepaymentType.DIVIDED_BY_PERIOD)
        val schedules =
            listOf(
                createSchedule(1L, status = RepaymentStatus.IN_PROGRESS),
                createSchedule(2L, status = RepaymentStatus.SCHEDULED, scheduledDate = LocalDate.now().plusMonths(1)),
            )
        // 전체 상환
        val request =
            CreateRepaymentRequestDTO(
                scheduleId = 1L,
                repaymentDate = LocalDate.now(),
                repaymentAmount = BigDecimal("300000"),
            )

        coEvery { transactionRepository.findById(1L) } returns transaction
        coEvery { repaymentScheduleRepository.findByTransactionId(1L) } returns schedules
        coEvery { repaymentScheduleRepository.save(any()) } answers { firstArg() }
        coEvery { repaymentScheduleRepository.saveAll(any<List<RepaymentSchedule>>()) } returns emptyList()
        coEvery { transactionRepository.save(any()) } answers { firstArg() }
        coEvery { userRepository.findById(any()) } returns null

        service.processRepayment(1L, 1L, request)

        // autoCompletedSchedules 저장을 위한 saveAll 호출 확인
        coVerify(atLeast = 1) {
            repaymentScheduleRepository.saveAll(any<List<RepaymentSchedule>>())
        }
    }
})
