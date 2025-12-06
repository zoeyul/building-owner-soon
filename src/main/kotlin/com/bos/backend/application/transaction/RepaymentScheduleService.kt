package com.bos.backend.application.transaction

import com.bos.backend.application.CommonErrorCode
import com.bos.backend.application.CustomException
import com.bos.backend.application.notification.NotificationService
import com.bos.backend.application.push.ExpoPushService
import com.bos.backend.application.push.PushTemplateService
import com.bos.backend.domain.push.PushData
import com.bos.backend.domain.push.PushTemplateType
import com.bos.backend.domain.transaction.entity.RepaymentSchedule
import com.bos.backend.domain.transaction.entity.Transaction
import com.bos.backend.domain.transaction.enum.RepaymentStatus
import com.bos.backend.domain.transaction.enum.RepaymentType
import com.bos.backend.domain.transaction.enum.TransactionType
import com.bos.backend.domain.transaction.repository.RepaymentScheduleRepository
import com.bos.backend.domain.transaction.repository.TransactionRepository
import com.bos.backend.domain.user.repository.UserDeviceRepository
import com.bos.backend.domain.user.repository.UserRepository
import com.bos.backend.presentation.transaction.dto.CreateRepaymentRequestDTO
import com.bos.backend.presentation.transaction.dto.RepaymentManagementResponseDTO
import com.bos.backend.presentation.transaction.dto.RepaymentScheduleItemDTO
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.flow.toList
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
@Suppress("LongParameterList", "TooManyFunctions")
class RepaymentScheduleService(
    private val repaymentScheduleRepository: RepaymentScheduleRepository,
    private val transactionRepository: TransactionRepository,
    private val notificationService: NotificationService,
    private val expoPushService: ExpoPushService,
    private val pushTemplateService: PushTemplateService,
    private val userDeviceRepository: UserDeviceRepository,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(RepaymentScheduleService::class.java)

    companion object {
        private const val ROUNDING_UNIT = 100 // 100원 단위 절삭을 위한 상수
    }

    suspend fun getRepaymentManagement(
        userId: Long,
        transactionId: Long,
    ): RepaymentManagementResponseDTO {
        val transaction =
            transactionRepository.findById(transactionId)
                ?: throw CustomException(CommonErrorCode.RESOURCE_NOT_FOUND)

        if (transaction.userId != userId) {
            throw CustomException(CommonErrorCode.RESOURCE_NOT_FOUND)
        }

        val repaymentSchedules = repaymentScheduleRepository.findByTransactionId(transactionId)
        val repaymentItems = generateRepaymentItems(repaymentSchedules)

        val overdueRepayments =
            repaymentItems
                .filter { it.status == RepaymentStatus.OVERDUE }
                .sortedBy { it.displayDate }

        // 진행중/예정 그룹 (SCHEDULED, IN_PROGRESS) - OVERDUE 제외
        val inProgressOrScheduled =
            repaymentItems
                .filter { it.status in listOf(RepaymentStatus.SCHEDULED, RepaymentStatus.IN_PROGRESS) }
                .sortedBy { it.displayDate }

        // 완료 그룹 (COMPLETED)
        val completed =
            repaymentItems
                .filter { it.status == RepaymentStatus.COMPLETED }
                .sortedBy { it.displayDate }

        // 진행중/예정 그룹을 먼저, 그 다음 완료 그룹
        val regularRepayments = inProgressOrScheduled + completed

        return RepaymentManagementResponseDTO(
            overdueRepayments = overdueRepayments,
            regularRepayments = regularRepayments,
        )
    }

    private fun generateRepaymentItems(repaymentSchedules: List<RepaymentSchedule>): List<RepaymentScheduleItemDTO> =
        repaymentSchedules
            .map { schedule ->
                RepaymentScheduleItemDTO(
                    id = schedule.id!!,
                    status = schedule.status,
                    displayDate = schedule.actualDate ?: schedule.scheduledDate,
                    displayAmount = schedule.actualAmount ?: schedule.scheduledAmount,
                )
            }.sortedBy { it.displayDate }

    suspend fun addRepayment(
        userId: Long,
        transactionId: Long,
        scheduleId: Long,
        createRepaymentRequestDTO: CreateRepaymentRequestDTO,
    ): RepaymentScheduleItemDTO {
        val schedule =
            repaymentScheduleRepository.findById(scheduleId)
                ?: throw CustomException(CommonErrorCode.RESOURCE_NOT_FOUND)

        val transaction =
            transactionRepository.findById(transactionId)
                ?: throw CustomException(CommonErrorCode.RESOURCE_NOT_FOUND)

        validateRepaymentRequest(schedule, transaction, transactionId, userId)

        if (schedule.status == RepaymentStatus.COMPLETED) {
            throw CustomException(CommonErrorCode.REPAYMENT_ALREADY_COMPLETED)
        }

        val updatedSchedule =
            schedule.copy(
                status = RepaymentStatus.COMPLETED,
                actualDate = createRepaymentRequestDTO.repaymentDate,
                actualAmount = createRepaymentRequestDTO.repaymentAmount,
                updatedAt = java.time.Instant.now(),
            )

        val savedSchedule = repaymentScheduleRepository.save(updatedSchedule)

        updateTransactionCompletedAmount(transaction, transactionId)

        return generateRepaymentItems(listOf(savedSchedule)).first()
    }

    private fun validateRepaymentRequest(
        schedule: RepaymentSchedule,
        transaction: Transaction,
        transactionId: Long,
        userId: Long,
    ) {
        if (schedule.transactionId != transactionId ||
            transaction.repaymentType == RepaymentType.FLEXIBLE ||
            transaction.userId != userId
        ) {
            throw CustomException(CommonErrorCode.RESOURCE_NOT_FOUND)
        }
    }

    suspend fun processRepayment(
        userId: Long,
        transactionId: Long,
        createRepaymentRequestDTO: CreateRepaymentRequestDTO,
    ): RepaymentScheduleItemDTO {
        val transaction =
            transactionRepository.findById(transactionId)
                ?: throw CustomException(CommonErrorCode.RESOURCE_NOT_FOUND)

        if (transaction.userId != userId) {
            throw CustomException(CommonErrorCode.RESOURCE_NOT_FOUND)
        }

        return when (transaction.repaymentType) {
            RepaymentType.FLEXIBLE -> processFlexibleRepayment(transaction, transactionId, createRepaymentRequestDTO)
            RepaymentType.DIVIDED_BY_PERIOD,
            RepaymentType.FIXED_MONTHLY,
            -> processScheduledRepayment(transaction, transactionId, createRepaymentRequestDTO)
        }
    }

    private suspend fun processFlexibleRepayment(
        transaction: Transaction,
        transactionId: Long,
        createRepaymentRequestDTO: CreateRepaymentRequestDTO,
    ): RepaymentScheduleItemDTO {
        if (createRepaymentRequestDTO.repaymentAmount > transaction.remainingAmount()) {
            throw CustomException(CommonErrorCode.AMOUNT_EXCEEDS_REMAINING)
        }

        val newSchedule =
            RepaymentSchedule(
                transactionId = transactionId,
                scheduledDate = createRepaymentRequestDTO.repaymentDate,
                scheduledAmount = createRepaymentRequestDTO.repaymentAmount,
                actualDate = createRepaymentRequestDTO.repaymentDate,
                actualAmount = createRepaymentRequestDTO.repaymentAmount,
                status = RepaymentStatus.COMPLETED,
            )

        val savedSchedule = repaymentScheduleRepository.save(newSchedule)
        updateTransactionCompletedAmount(transaction, transactionId)

        return generateRepaymentItems(listOf(savedSchedule)).first()
    }

    private suspend fun processScheduledRepayment(
        transaction: Transaction,
        transactionId: Long,
        createRepaymentRequestDTO: CreateRepaymentRequestDTO,
    ): RepaymentScheduleItemDTO {
        val schedule =
            repaymentScheduleRepository.findByTransactionIdAndScheduledDate(
                transactionId,
                createRepaymentRequestDTO.repaymentDate,
            ) ?: throw CustomException(CommonErrorCode.RESOURCE_NOT_FOUND)

        if (schedule.status == RepaymentStatus.COMPLETED) {
            throw CustomException(CommonErrorCode.REPAYMENT_ALREADY_COMPLETED)
        }

        val updatedSchedule =
            schedule.copy(
                status = RepaymentStatus.COMPLETED,
                actualDate = createRepaymentRequestDTO.repaymentDate,
                actualAmount = createRepaymentRequestDTO.repaymentAmount,
                updatedAt = java.time.Instant.now(),
            )

        val savedSchedule = repaymentScheduleRepository.save(updatedSchedule)

        if (createRepaymentRequestDTO.repaymentAmount != schedule.scheduledAmount) {
            recalculateRemainingSchedules(transaction, transactionId, schedule.scheduledDate)
        }

        updateTransactionCompletedAmount(transaction, transactionId)

        return generateRepaymentItems(listOf(savedSchedule)).first()
    }

    private suspend fun recalculateRemainingSchedules(
        transaction: Transaction,
        transactionId: Long,
        completedScheduleDate: java.time.LocalDate,
    ) {
        val allSchedules =
            repaymentScheduleRepository
                .findByTransactionId(transactionId)
                .sortedBy { it.scheduledDate }

        val completedAmount =
            allSchedules
                .filter { it.status == RepaymentStatus.COMPLETED }
                .sumOf { it.actualAmount ?: java.math.BigDecimal.ZERO }

        val remainingAmount = transaction.totalAmount - completedAmount

        val pendingSchedules =
            allSchedules
                .filter { it.status != RepaymentStatus.COMPLETED && it.scheduledDate > completedScheduleDate }
                .sortedBy { it.scheduledDate }

        if (pendingSchedules.isEmpty() || remainingAmount <= java.math.BigDecimal.ZERO) {
            return
        }

        // 100원 단위 절삭 적용 (DIVIDED_BY_PERIOD와 동일한 로직)
        // 재계산 시에도 일관된 금액 계산 정책 적용
        val amountInHundreds = remainingAmount.divide(java.math.BigDecimal(ROUNDING_UNIT)).toBigInteger()
        val baseInHundreds = amountInHundreds.divide(java.math.BigInteger.valueOf(pendingSchedules.size.toLong()))
        val baseAmount =
            java.math.BigDecimal(
                baseInHundreds.multiply(java.math.BigInteger.valueOf(ROUNDING_UNIT.toLong())),
            )

        val totalBaseAmount = baseAmount.multiply(java.math.BigDecimal(pendingSchedules.size))
        val remainder = remainingAmount.subtract(totalBaseAmount)

        val updatedSchedules =
            pendingSchedules.mapIndexed { index, schedule ->
                val newAmount =
                    if (index == pendingSchedules.size - 1) {
                        // 마지막 납부는 기본 금액 + 나머지 금액
                        baseAmount.add(remainder)
                    } else {
                        baseAmount
                    }

                schedule.copy(
                    scheduledAmount = newAmount,
                    updatedAt = java.time.Instant.now(),
                )
            }

        repaymentScheduleRepository.saveAll(updatedSchedules)
    }

    private suspend fun updateTransactionCompletedAmount(
        transaction: Transaction,
        transactionId: Long,
    ) {
        val allSchedules = repaymentScheduleRepository.findByTransactionId(transactionId)
        val schedulesCompletedAmount =
            allSchedules
                .filter { it.status == RepaymentStatus.COMPLETED }
                .sumOf { it.actualAmount ?: java.math.BigDecimal.ZERO }

        // 초기 completedAmount (Transaction 생성 시 설정된 값) + 스케줄 상환 금액
        val totalCompletedAmount = transaction.initialCompletedAmount + schedulesCompletedAmount
        val updatedTransaction = transaction.updateCompletedAmount(totalCompletedAmount)
        transactionRepository.save(updatedTransaction)

        // 거래 완료 체크 및 푸시/알림 전송
        if (updatedTransaction.remainingAmount().compareTo(BigDecimal.ZERO) == 0) {
            sendTransactionCompletePush(updatedTransaction)
        }
    }

    /**
     * 거래 완료 시 푸시 및 알림 전송
     */
    @Suppress("TooGenericExceptionCaught", "ReturnCount", "LongMethod")
    private suspend fun sendTransactionCompletePush(transaction: Transaction) {
        try {
            val user = userRepository.findById(transaction.userId) ?: return
            if (!user.isNotificationAllowed) {
                logger.info("User {} has notifications disabled, skipping transaction complete push", user.id)
                return
            }

            val devices = userDeviceRepository.findByUserId(user.id!!).toList()
            if (devices.isEmpty()) {
                logger.info("No devices found for user {}, skipping transaction complete push", user.id)
                return
            }

            // 템플릿 타입 결정
            val templateType =
                when (transaction.transactionType) {
                    TransactionType.BORROW -> PushTemplateType.TRANSACTION_COMPLETE_BORROW
                    TransactionType.LEND -> PushTemplateType.TRANSACTION_COMPLETE_LEND
                }

            // Notification 레코드 생성
            val pushData = PushData.forTransaction(transaction.id!!)
            val deepLink = objectMapper.writeValueAsString(pushData)

            val notification =
                notificationService.createNotificationRecord(
                    userId = user.id!!,
                    title = templateType.titleTemplate,
                    content = formatBody(templateType, user.nickname, transaction.counterpartName),
                    category = templateType.toNotificationCategory(),
                    deepLink = deepLink,
                )

            // 푸시 메시지 생성
            val messages =
                devices.mapNotNull { device ->
                    device.expoToken?.let { token ->
                        when (transaction.transactionType) {
                            TransactionType.BORROW ->
                                pushTemplateService.createTransactionCompleteBorrow(
                                    expoToken = token,
                                    counterpartName = transaction.counterpartName,
                                    transactionId = transaction.id!!,
                                    notificationId = notification.id,
                                )
                            TransactionType.LEND ->
                                pushTemplateService.createTransactionCompleteLend(
                                    expoToken = token,
                                    nickname = user.nickname,
                                    counterpartName = transaction.counterpartName,
                                    transactionId = transaction.id!!,
                                    notificationId = notification.id,
                                )
                        }
                    }
                }

            if (messages.isNotEmpty()) {
                val result = expoPushService.sendToMultipleDevices(devices, messages)
                logger.info(
                    "Transaction complete push sent for transaction {}: success={}, failure={}",
                    transaction.id,
                    result.successCount,
                    result.failureCount,
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to send transaction complete push for transaction {}", transaction.id, e)
        }
    }

    private fun formatBody(
        templateType: PushTemplateType,
        nickname: String,
        counterpartName: String,
    ): String {
        return templateType.bodyTemplate
            .replace("{nickname}", nickname)
            .replace("{counterpartName}", counterpartName)
    }
}
