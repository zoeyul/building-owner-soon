package com.bos.backend.application.transaction

import com.bos.backend.application.CommonErrorCode
import com.bos.backend.application.CustomException
import com.bos.backend.domain.transaction.entity.RepaymentSchedule
import com.bos.backend.domain.transaction.entity.Transaction
import com.bos.backend.domain.transaction.enum.RepaymentStatus
import com.bos.backend.domain.transaction.enum.RepaymentType
import com.bos.backend.domain.transaction.repository.RepaymentScheduleRepository
import com.bos.backend.domain.transaction.repository.TransactionRepository
import com.bos.backend.presentation.transaction.dto.CreateRepaymentRequestDTO
import com.bos.backend.presentation.transaction.dto.RepaymentManagementResponseDTO
import com.bos.backend.presentation.transaction.dto.RepaymentScheduleItemDTO
import org.springframework.stereotype.Service

@Service
class RepaymentScheduleService(
    private val repaymentScheduleRepository: RepaymentScheduleRepository,
    private val transactionRepository: TransactionRepository,
) {
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
                .sortedByDescending { it.displayDate }

        val regularRepayments =
            repaymentItems
                .filter { it.status != RepaymentStatus.OVERDUE }
                .sortedByDescending { it.displayDate }

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

        val averageAmount =
            remainingAmount.divide(
                java.math.BigDecimal(pendingSchedules.size),
                0,
                java.math.RoundingMode.DOWN,
            )
        val remainder = remainingAmount - (averageAmount * java.math.BigDecimal(pendingSchedules.size))

        val updatedSchedules =
            pendingSchedules.mapIndexed { index, schedule ->
                val newAmount =
                    if (index == pendingSchedules.size - 1) {
                        averageAmount + remainder
                    } else {
                        averageAmount
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
        val totalCompletedAmount =
            allSchedules
                .filter { it.status == RepaymentStatus.COMPLETED }
                .sumOf { it.actualAmount ?: java.math.BigDecimal.ZERO }
        val updatedTransaction = transaction.updateCompletedAmount(totalCompletedAmount)
        transactionRepository.save(updatedTransaction)
    }
}
