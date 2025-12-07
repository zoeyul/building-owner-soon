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

@Component
class FlexibleRepaymentStrategy : RepaymentStrategy {
    override suspend fun processRepayment(
        transaction: Transaction,
        request: CreateRepaymentRequestDTO,
        allSchedules: List<RepaymentSchedule>,
    ): RepaymentProcessingResult {
        validateRepaymentAmount(transaction, request.repaymentAmount)

        val completedSchedulesAmount = calculateCompletedAmount(allSchedules)
        val newCompletedAmount = transaction.initialCompletedAmount + completedSchedulesAmount + request.repaymentAmount

        val newSchedule = createCompletedSchedule(transaction.id!!, request)
        val isTransactionCompleted = newCompletedAmount >= transaction.totalAmount

        return RepaymentProcessingResult(
            completedSchedule = newSchedule,
            adjustedSchedules = emptyList(),
            autoCompletedSchedules = emptyList(),
            newCompletedAmount = newCompletedAmount,
            isTransactionCompleted = isTransactionCompleted,
        )
    }

    private fun validateRepaymentAmount(
        transaction: Transaction,
        repaymentAmount: BigDecimal,
    ) {
        if (repaymentAmount > transaction.remainingAmount()) {
            throw CustomException(CommonErrorCode.AMOUNT_EXCEEDS_REMAINING)
        }
    }

    private fun calculateCompletedAmount(allSchedules: List<RepaymentSchedule>): BigDecimal =
        allSchedules
            .filter { it.status == RepaymentStatus.COMPLETED }
            .sumOf { it.actualAmount ?: BigDecimal.ZERO }

    private fun createCompletedSchedule(
        transactionId: Long,
        request: CreateRepaymentRequestDTO,
    ): RepaymentSchedule =
        RepaymentSchedule(
            transactionId = transactionId,
            scheduledDate = request.repaymentDate,
            scheduledAmount = request.repaymentAmount,
            actualDate = request.repaymentDate,
            actualAmount = request.repaymentAmount,
            status = RepaymentStatus.COMPLETED,
        )
}
