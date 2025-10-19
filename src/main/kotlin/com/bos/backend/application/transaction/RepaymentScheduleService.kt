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

    suspend fun addFlexibleRepayment(
        userId: Long,
        transactionId: Long,
        createRepaymentRequestDTO: CreateRepaymentRequestDTO,
    ): RepaymentScheduleItemDTO {
        val transaction =
            transactionRepository.findById(transactionId)
                ?: throw CustomException(CommonErrorCode.RESOURCE_NOT_FOUND)

        validateFlexibleRepaymentRequest(transaction, userId, createRepaymentRequestDTO)

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

    private fun validateFlexibleRepaymentRequest(
        transaction: Transaction,
        userId: Long,
        createRepaymentRequestDTO: CreateRepaymentRequestDTO,
    ) {
        if (transaction.userId != userId) {
            throw CustomException(CommonErrorCode.RESOURCE_NOT_FOUND)
        }

        if (transaction.repaymentType != RepaymentType.FLEXIBLE) {
            throw CustomException(CommonErrorCode.REPAYMENT_TYPE_MISMATCH)
        }

        if (createRepaymentRequestDTO.repaymentAmount > transaction.remainingAmount()) {
            throw CustomException(CommonErrorCode.AMOUNT_EXCEEDS_REMAINING)
        }
    }
}
