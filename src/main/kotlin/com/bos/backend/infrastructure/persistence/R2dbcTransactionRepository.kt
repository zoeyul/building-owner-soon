package com.bos.backend.infrastructure.persistence

import com.bos.backend.domain.transaction.entity.Transaction
import com.bos.backend.domain.transaction.repository.TransactionRepository
import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

interface TransactionCoroutineRepository : CoroutineCrudRepository<Transaction, Long> {
    @Query("SELECT * FROM transactions WHERE user_id = :userId AND deleted_at IS NULL")
    suspend fun findByUserIdAndDeletedAtIsNull(userId: Long): List<Transaction>

    @Query("SELECT * FROM transactions WHERE uuid = :uuid AND deleted_at IS NULL")
    suspend fun findByUuidAndDeletedAtIsNull(uuid: String): Transaction?

    @Query("SELECT * FROM transactions WHERE id = :id AND deleted_at IS NULL")
    suspend fun findByIdAndDeletedAtIsNull(id: Long): Transaction?

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun findByIdIncludingDeleted(id: Long): Transaction?

    @Modifying
    @Query("UPDATE transactions SET deleted_at = NOW(), updated_at = NOW() WHERE id = :id AND deleted_at IS NULL")
    suspend fun softDeleteById(id: Long): Int
}

@Repository
class R2dbcTransactionRepositoryImpl(
    private val coroutineRepository: TransactionCoroutineRepository,
) : TransactionRepository {
    override suspend fun save(transaction: Transaction): Transaction = coroutineRepository.save(transaction)

    override suspend fun findById(id: Long): Transaction? = coroutineRepository.findByIdAndDeletedAtIsNull(id)

    override suspend fun findByIdIncludingDeleted(id: Long): Transaction? =
        coroutineRepository.findByIdIncludingDeleted(id)

    override suspend fun findByUuid(uuid: String): Transaction? = coroutineRepository.findByUuidAndDeletedAtIsNull(uuid)

    override suspend fun findByUserId(userId: Long): List<Transaction> =
        coroutineRepository.findByUserIdAndDeletedAtIsNull(userId)

    override suspend fun softDeleteById(id: Long): Boolean = coroutineRepository.softDeleteById(id) > 0
}
