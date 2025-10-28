package com.bos.backend.presentation.transaction.dto

import com.bos.backend.domain.transaction.entity.CounterpartCharacter
import com.bos.backend.domain.transaction.enum.RelationshipType
import com.bos.backend.domain.transaction.enum.RepaymentType
import com.bos.backend.domain.transaction.enum.TransactionType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class TransactionResponseDTO(
    val id: Long,
    val transactionUuid: String,
    val transactionType: TransactionType,
    val counterpartName: String,
    val counterpartCharacter: CounterpartCharacter,
    val relationship: RelationshipType,
    val customRelationship: String?,
    val transactionDate: LocalDate,
    val totalAmount: BigDecimal,
    val completedAmount: BigDecimal,
    val remainingAmount: BigDecimal,
    val memo: String?,
    val repaymentType: RepaymentType,
    val targetDate: LocalDate?,
    val monthlyAmount: BigDecimal?,
    val paymentDay: Int?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
