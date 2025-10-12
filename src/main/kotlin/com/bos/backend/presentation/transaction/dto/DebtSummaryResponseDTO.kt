package com.bos.backend.presentation.transaction.dto

data class DebtSummaryResponseDTO(
    val lendSummary: TransactionSummaryDTO,
    val borrowSummary: TransactionSummaryDTO,
)
