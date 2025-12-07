package com.bos.backend.application.transaction.dto

import com.bos.backend.domain.transaction.entity.RepaymentSchedule
import java.math.BigDecimal

data class RepaymentProcessingResult(
    val completedSchedule: RepaymentSchedule,
    val adjustedSchedules: List<RepaymentSchedule> = emptyList(),
    val autoCompletedSchedules: List<RepaymentSchedule> = emptyList(),
    val newCompletedAmount: BigDecimal,
    val isTransactionCompleted: Boolean,
)
