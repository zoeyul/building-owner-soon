package com.bos.backend.infrastructure.persistence

import com.bos.backend.domain.transaction.entity.Transaction
import com.bos.backend.domain.transaction.repository.TransactionRepository
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

interface TransactionCoroutineRepository : CoroutineCrudRepository<Transaction, Long> {
    suspend fun findByUserId(userId: Long): List<Transaction>

    suspend fun findByUuid(uuid: String): Transaction?
}

@Repository
class R2dbcTransactionRepositoryImpl(
    private val coroutineRepository: TransactionCoroutineRepository,
) : TransactionRepository {
    override suspend fun save(transaction: Transaction): Transaction = coroutineRepository.save(transaction)

    override suspend fun findById(id: Long): Transaction? = coroutineRepository.findById(id)

    override suspend fun findByUuid(uuid: String): Transaction? = coroutineRepository.findByUuid(uuid)

    override suspend fun findByUserId(userId: Long): List<Transaction> = coroutineRepository.findByUserId(userId)

    override suspend fun deleteById(id: Long) {
        coroutineRepository.deleteById(id)
    }
}
