package com.bos.backend.application.transaction.strategy

import com.bos.backend.application.CommonErrorCode
import com.bos.backend.application.CustomException
import com.bos.backend.application.transaction.dto.RepaymentProcessingResult
import com.bos.backend.domain.transaction.entity.RepaymentSchedule
import com.bos.backend.domain.transaction.entity.Transaction
import com.bos.backend.domain.transaction.enum.RepaymentStatus
import com.bos.backend.presentation.transaction.dto.CreateRepaymentRequestDTO
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant

@Component
class ScheduledRepaymentStrategy : RepaymentStrategy {
    companion object {
        private const val ROUNDING_UNIT = 100
    }

    override suspend fun processRepayment(
        transaction: Transaction,
        request: CreateRepaymentRequestDTO,
        allSchedules: List<RepaymentSchedule>,
    ): RepaymentProcessingResult {
        val scheduleId =
            request.scheduleId
                ?: throw CustomException(CommonErrorCode.SCHEDULE_ID_REQUIRED)

        val targetSchedule = findAndValidateSchedule(allSchedules, scheduleId)
        val completedSchedule = completeSchedule(targetSchedule, request)

        val completedSchedulesAmount = calculateCompletedAmount(allSchedules, targetSchedule.id!!)
        val newTotalCompletedAmount =
            transaction.initialCompletedAmount +
                completedSchedulesAmount + request.repaymentAmount

        val isTransactionCompleted = newTotalCompletedAmount >= transaction.totalAmount

        return if (isTransactionCompleted) {
            handleFullRepayment(
                completedSchedule = completedSchedule,
                allSchedules = allSchedules,
                newCompletedAmount = transaction.totalAmount,
            )
        } else {
            handlePartialOrExcessRepayment(
                transaction = transaction,
                completedSchedule = completedSchedule,
                allSchedules = allSchedules,
                newCompletedAmount = newTotalCompletedAmount,
            )
        }
    }

    private fun findAndValidateSchedule(
        allSchedules: List<RepaymentSchedule>,
        scheduleId: Long,
    ): RepaymentSchedule {
        val schedule =
            allSchedules.find { it.id == scheduleId }
                ?: throw CustomException(CommonErrorCode.RESOURCE_NOT_FOUND)

        validateScheduleStatus(schedule)
        return schedule
    }

    private fun validateScheduleStatus(schedule: RepaymentSchedule) {
        when (schedule.status) {
            RepaymentStatus.COMPLETED ->
                throw CustomException(CommonErrorCode.REPAYMENT_ALREADY_COMPLETED)
            RepaymentStatus.SCHEDULED ->
                throw CustomException(CommonErrorCode.REPAYMENT_NOT_ALLOWED)
            RepaymentStatus.IN_PROGRESS, RepaymentStatus.OVERDUE -> { /* 상환 가능 */ }
        }
    }

    private fun completeSchedule(
        schedule: RepaymentSchedule,
        request: CreateRepaymentRequestDTO,
    ): RepaymentSchedule =
        schedule.copy(
            status = RepaymentStatus.COMPLETED,
            actualDate = request.repaymentDate,
            actualAmount = request.repaymentAmount,
            updatedAt = Instant.now(),
        )

    private fun calculateCompletedAmount(
        allSchedules: List<RepaymentSchedule>,
        excludeScheduleId: Long,
    ): BigDecimal =
        allSchedules
            .filter { it.status == RepaymentStatus.COMPLETED && it.id != excludeScheduleId }
            .sumOf { it.actualAmount ?: BigDecimal.ZERO }

    private fun handleFullRepayment(
        completedSchedule: RepaymentSchedule,
        allSchedules: List<RepaymentSchedule>,
        newCompletedAmount: BigDecimal,
    ): RepaymentProcessingResult {
        val pendingSchedules =
            allSchedules
                .filter { it.id != completedSchedule.id && it.status != RepaymentStatus.COMPLETED }

        val autoCompletedSchedules =
            pendingSchedules.map { schedule ->
                schedule.copy(
                    status = RepaymentStatus.COMPLETED,
                    actualDate = completedSchedule.actualDate,
                    actualAmount = BigDecimal.ZERO,
                    updatedAt = Instant.now(),
                )
            }

        return RepaymentProcessingResult(
            completedSchedule = completedSchedule,
            adjustedSchedules = emptyList(),
            autoCompletedSchedules = autoCompletedSchedules,
            newCompletedAmount = newCompletedAmount,
            isTransactionCompleted = true,
        )
    }

    private fun handlePartialOrExcessRepayment(
        transaction: Transaction,
        completedSchedule: RepaymentSchedule,
        allSchedules: List<RepaymentSchedule>,
        newCompletedAmount: BigDecimal,
    ): RepaymentProcessingResult {
        val pendingSchedules =
            allSchedules
                .filter { it.id != completedSchedule.id && it.status != RepaymentStatus.COMPLETED }
                .sortedBy { it.scheduledDate }

        if (pendingSchedules.isEmpty()) {
            return RepaymentProcessingResult(
                completedSchedule = completedSchedule,
                adjustedSchedules = emptyList(),
                autoCompletedSchedules = emptyList(),
                newCompletedAmount = newCompletedAmount,
                isTransactionCompleted = false,
            )
        }

        val remainingAmount = transaction.totalAmount - newCompletedAmount
        val adjustedSchedules = redistributeAmounts(pendingSchedules, remainingAmount)

        return RepaymentProcessingResult(
            completedSchedule = completedSchedule,
            adjustedSchedules = adjustedSchedules,
            autoCompletedSchedules = emptyList(),
            newCompletedAmount = newCompletedAmount,
            isTransactionCompleted = false,
        )
    }

    private fun redistributeAmounts(
        pendingSchedules: List<RepaymentSchedule>,
        remainingAmount: BigDecimal,
    ): List<RepaymentSchedule> {
        if (remainingAmount <= BigDecimal.ZERO) {
            return pendingSchedules.map { schedule ->
                schedule.copy(
                    scheduledAmount = BigDecimal.ZERO,
                    updatedAt = Instant.now(),
                )
            }
        }

        val scheduleCount = pendingSchedules.size
        val amountInHundreds = remainingAmount.divide(BigDecimal(ROUNDING_UNIT)).toBigInteger()
        val baseInHundreds = amountInHundreds.divide(BigInteger.valueOf(scheduleCount.toLong()))
        val baseAmount = BigDecimal(baseInHundreds.multiply(BigInteger.valueOf(ROUNDING_UNIT.toLong())))

        val totalBaseAmount = baseAmount.multiply(BigDecimal(scheduleCount))
        val remainder = remainingAmount.subtract(totalBaseAmount)

        return pendingSchedules.mapIndexed { index, schedule ->
            val newAmount =
                if (index == scheduleCount - 1) {
                    baseAmount.add(remainder)
                } else {
                    baseAmount
                }

            schedule.copy(
                scheduledAmount = newAmount,
                updatedAt = Instant.now(),
            )
        }
    }
}
