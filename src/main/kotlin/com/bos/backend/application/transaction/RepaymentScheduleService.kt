package com.bos.backend.application.transaction

import com.bos.backend.application.CommonErrorCode
import com.bos.backend.application.CustomException
import com.bos.backend.application.notification.NotificationService
import com.bos.backend.application.push.ExpoPushService
import com.bos.backend.application.push.PushTemplateService
import com.bos.backend.application.transaction.strategy.RepaymentStrategyFactory
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

@Service
@Suppress("LongParameterList")
class RepaymentScheduleService(
    private val repaymentScheduleRepository: RepaymentScheduleRepository,
    private val transactionRepository: TransactionRepository,
    private val notificationService: NotificationService,
    private val expoPushService: ExpoPushService,
    private val pushTemplateService: PushTemplateService,
    private val userDeviceRepository: UserDeviceRepository,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper,
    private val repaymentStrategyFactory: RepaymentStrategyFactory,
) {
    private val logger = LoggerFactory.getLogger(RepaymentScheduleService::class.java)

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

    suspend fun processRepayment(
        userId: Long,
        transactionId: Long,
        request: CreateRepaymentRequestDTO,
    ): RepaymentScheduleItemDTO {
        val transaction = findAndValidateTransaction(userId, transactionId)
        validateScheduleIdRequirement(transaction.repaymentType, request)

        val allSchedules = repaymentScheduleRepository.findByTransactionId(transactionId)

        val strategy = repaymentStrategyFactory.getStrategy(transaction.repaymentType)
        val result = strategy.processRepayment(transaction, request, allSchedules)

        val savedSchedule = repaymentScheduleRepository.save(result.completedSchedule)

        if (result.adjustedSchedules.isNotEmpty()) {
            repaymentScheduleRepository.saveAll(result.adjustedSchedules)
        }

        if (result.autoCompletedSchedules.isNotEmpty()) {
            repaymentScheduleRepository.saveAll(result.autoCompletedSchedules)
        }

        val updatedTransaction = transaction.updateCompletedAmount(result.newCompletedAmount)
        transactionRepository.save(updatedTransaction)

        if (result.isTransactionCompleted) {
            sendTransactionCompletePush(updatedTransaction)
        }

        return generateRepaymentItems(listOf(savedSchedule)).first()
    }

    private suspend fun findAndValidateTransaction(
        userId: Long,
        transactionId: Long,
    ): Transaction {
        val transaction =
            transactionRepository.findById(transactionId)
                ?: throw CustomException(CommonErrorCode.RESOURCE_NOT_FOUND)

        if (transaction.userId != userId) {
            throw CustomException(CommonErrorCode.RESOURCE_NOT_FOUND)
        }

        return transaction
    }

    private fun validateScheduleIdRequirement(
        repaymentType: RepaymentType,
        request: CreateRepaymentRequestDTO,
    ) {
        if (repaymentType != RepaymentType.FLEXIBLE && request.scheduleId == null) {
            throw CustomException(CommonErrorCode.SCHEDULE_ID_REQUIRED)
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
