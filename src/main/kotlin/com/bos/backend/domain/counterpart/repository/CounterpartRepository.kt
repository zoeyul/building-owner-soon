package com.bos.backend.domain.counterpart.repository

import com.bos.backend.domain.counterpart.entity.Counterpart
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Counterpart domain
 */
interface CounterpartRepository {
    suspend fun save(counterpart: Counterpart): Counterpart

    suspend fun findById(id: Long): Counterpart?

    fun findByUserId(userId: Long): Flow<Counterpart>

    suspend fun update(counterpart: Counterpart): Counterpart

    suspend fun deleteById(id: Long)

    suspend fun countTransactionsByCounterpartId(counterpartId: Long): Long
}
