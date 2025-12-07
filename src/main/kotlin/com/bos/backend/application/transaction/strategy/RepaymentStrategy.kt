package com.bos.backend.application.transaction.strategy

import com.bos.backend.application.transaction.dto.RepaymentProcessingResult
import com.bos.backend.domain.transaction.entity.RepaymentSchedule
import com.bos.backend.domain.transaction.entity.Transaction
import com.bos.backend.presentation.transaction.dto.CreateRepaymentRequestDTO

interface RepaymentStrategy {
    suspend fun processRepayment(
        transaction: Transaction,
        request: CreateRepaymentRequestDTO,
        allSchedules: List<RepaymentSchedule>,
    ): RepaymentProcessingResult
}
