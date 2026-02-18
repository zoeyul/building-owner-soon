package com.bos.backend.infrastructure.persistence

import com.bos.backend.domain.transaction.entity.Transaction
import com.bos.backend.domain.transaction.entity.TransactionWithCounterpart
import com.bos.backend.domain.transaction.enum.TransactionType
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

    @Query(
        """
        SELECT * FROM transactions
        WHERE user_id = :userId AND deleted_at IS NULL AND celebration_completed = false
        """,
    )
    suspend fun findActiveByUserId(userId: Long): List<Transaction>

    @Modifying
    @Query(
        """
        UPDATE transactions SET celebration_completed = true, updated_at = NOW()
        WHERE id = :id AND deleted_at IS NULL
        """,
    )
    suspend fun markCelebrationCompleted(id: Long): Int

    @Modifying
    @Query("UPDATE transactions SET deleted_at = NOW(), updated_at = NOW() WHERE id = :id AND deleted_at IS NULL")
    suspend fun softDeleteById(id: Long): Int

    @Query(
        """
        SELECT * FROM transactions
        WHERE user_id = :userId AND deleted_at IS NULL
        AND completed_amount >= total_amount
        ORDER BY updated_at DESC
        """,
    )
    suspend fun findCompletedByUserId(userId: Long): List<Transaction>

    @Query(
        """
        SELECT * FROM transactions
        WHERE user_id = :userId AND deleted_at IS NULL
        AND completed_amount >= total_amount AND transaction_type = :transactionType
        ORDER BY updated_at DESC
        """,
    )
    suspend fun findCompletedByUserIdAndTransactionType(
        userId: Long,
        transactionType: TransactionType,
    ): List<Transaction>
}

@Repository
@Suppress("TooManyFunctions")
class R2dbcTransactionRepositoryImpl(
    private val coroutineRepository: TransactionCoroutineRepository,
) : TransactionRepository {
    override suspend fun save(transaction: Transaction): Transaction = coroutineRepository.save(transaction)

    override suspend fun findById(id: Long): Transaction? = coroutineRepository.findByIdAndDeletedAtIsNull(id)

    override suspend fun findByIdIncludingDeleted(id: Long): Transaction? =
        coroutineRepository.findByIdIncludingDeleted(id)

    override suspend fun findByIdWithCounterpart(id: Long): TransactionWithCounterpart? {
        // JOIN 쿼리는 서비스 레이어에서 처리
        TODO("This will be handled in service layer by fetching transaction and counterpart separately")
    }

    override suspend fun findByUuidWithCounterpart(uuid: String): TransactionWithCounterpart? {
        // JOIN 쿼리는 서비스 레이어에서 처리
        TODO("This will be handled in service layer by fetching transaction and counterpart separately")
    }

    override suspend fun findByUuid(uuid: String): Transaction? = coroutineRepository.findByUuidAndDeletedAtIsNull(uuid)

    override suspend fun findByUserId(userId: Long): List<Transaction> =
        coroutineRepository.findByUserIdAndDeletedAtIsNull(userId)

    override suspend fun findActiveByUserId(userId: Long): List<Transaction> =
        coroutineRepository.findActiveByUserId(userId)

    override suspend fun markCelebrationCompleted(id: Long): Boolean =
        coroutineRepository.markCelebrationCompleted(id) > 0

    override suspend fun findByUserIdWithCounterpart(userId: Long): List<TransactionWithCounterpart> {
        // JOIN 쿼리는 서비스 레이어에서 처리
        TODO("This will be handled in service layer by fetching transactions and counterparts separately")
    }

    override suspend fun softDeleteById(id: Long): Boolean = coroutineRepository.softDeleteById(id) > 0

    override suspend fun findCompletedByUserId(
        userId: Long,
        transactionType: TransactionType?,
    ): List<Transaction> =
        if (transactionType != null) {
            coroutineRepository.findCompletedByUserIdAndTransactionType(userId, transactionType)
        } else {
            coroutineRepository.findCompletedByUserId(userId)
        }
}
