package com.bos.backend.domain.transaction.repository

import com.bos.backend.domain.transaction.entity.Transaction

interface TransactionRepository {
    suspend fun save(transaction: Transaction): Transaction

    suspend fun findById(id: Long): Transaction?

    suspend fun findByIdIncludingDeleted(id: Long): Transaction?

    suspend fun findByUuid(uuid: String): Transaction?

    suspend fun findByUserId(userId: Long): List<Transaction>

    suspend fun softDeleteById(id: Long): Boolean
}
