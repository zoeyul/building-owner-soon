package com.bos.backend.presentation.transaction.dto

import com.bos.backend.domain.transaction.entity.CounterpartCharacter
import com.bos.backend.domain.transaction.enum.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

data class CompletedTransactionListItemDTO(
    val id: Long,
    val transactionType: TransactionType,
    val counterpartName: String,
    val counterpartCharacter: CounterpartCharacter,
    val totalAmount: BigDecimal,
    val completionDate: LocalDate,
)
