package com.bos.backend.presentation.transaction.dto

import com.bos.backend.domain.transaction.enum.RepaymentType
import com.bos.backend.domain.transaction.enum.TransactionType
import com.bos.backend.domain.user.entity.Character
import java.math.BigDecimal

data class TransactionDetailResponseDTO(
    val userProfileImage: Character,
    val transactionType: TransactionType,
    val totalAmount: BigDecimal,
    val remainingAmount: BigDecimal,
    val repaymentType: RepaymentType,
    val monthlyAmount: BigDecimal?,
    val paymentDay: Int?,
    val borrower: String,
    val lender: String,
    val repaymentSchedules: List<RepaymentScheduleDetailDTO>,
)

data class RepaymentScheduleDetailDTO(
    val id: Long,
    val status: String,
    val displayDate: String,
    val displayAmount: BigDecimal,
)
