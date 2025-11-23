package com.bos.backend.domain.transaction.repository

import com.bos.backend.domain.transaction.entity.Transaction
import com.bos.backend.domain.transaction.entity.TransactionWithCounterpart

interface TransactionRepository {
    suspend fun save(transaction: Transaction): Transaction

    suspend fun findById(id: Long): Transaction?

    suspend fun findByIdIncludingDeleted(id: Long): Transaction?

    suspend fun findByIdWithCounterpart(id: Long): TransactionWithCounterpart?

    suspend fun findByUuidWithCounterpart(uuid: String): TransactionWithCounterpart?

    suspend fun findByUuid(uuid: String): Transaction?

    suspend fun findByUserId(userId: Long): List<Transaction>

    suspend fun findByUserIdWithCounterpart(userId: Long): List<TransactionWithCounterpart>

    suspend fun softDeleteById(id: Long): Boolean
}
