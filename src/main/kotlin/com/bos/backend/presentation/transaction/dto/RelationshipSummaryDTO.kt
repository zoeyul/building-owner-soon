package com.bos.backend.presentation.transaction.dto

import com.bos.backend.domain.transaction.entity.CounterpartCharacter
import com.bos.backend.domain.transaction.enum.RelationshipType
import com.bos.backend.domain.transaction.enum.TransactionType

data class RelationshipSummaryDTO(
    val counterpartName: String,
    val counterpartCharacter: CounterpartCharacter,
    val relationship: RelationshipType,
    val customRelationship: String?,
    val transactionType: TransactionType,
    val totalAmount: Long,
    val upcomingTransactionInfo: UpcomingTransactionInfoDTO?,
    val transactionId: Long,
    val transactionUuid: String,
)
