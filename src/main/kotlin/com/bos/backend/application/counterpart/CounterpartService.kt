package com.bos.backend.application.counterpart

import com.bos.backend.domain.counterpart.entity.Counterpart
import com.bos.backend.domain.counterpart.repository.CounterpartRepository
import kotlinx.coroutines.flow.Flow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class CounterpartService(
    private val counterpartRepository: CounterpartRepository,
) {
    companion object {
        private const val MAX_NAME_LENGTH = 12
    }

    @Transactional
    suspend fun createCounterpart(
        userId: Long,
        name: String,
        character: com.bos.backend.domain.transaction.entity.CounterpartCharacter,
    ): Counterpart {
        require(name.isNotBlank()) { "Counterpart name cannot be blank" }
        require(name.length <= MAX_NAME_LENGTH) { "Counterpart name cannot exceed $MAX_NAME_LENGTH characters" }

        val counterpart =
            Counterpart(
                userId = userId,
                name = name,
                character = character,
            )

        return counterpartRepository.save(counterpart)
    }

    /**
     * Finds a counterpart by ID.
     *
     * @param id Counterpart ID
     * @return Counterpart if found, null otherwise
     */
    suspend fun findById(id: Long): Counterpart? {
        return counterpartRepository.findById(id)
    }

    /**
     * Retrieves all counterparts for a specific user.
     *
     * @param userId The user ID
     * @return Flow of counterparts owned by the user
     */
    fun findByUserId(userId: Long): Flow<Counterpart> {
        return counterpartRepository.findByUserId(userId)
    }

    /**
     * Updates a counterpart's name.
     * This will automatically propagate to all transactions via the foreign key relationship.
     *
     * @param counterpartId Counterpart ID to update
     * @param newName New name for the counterpart
     * @return Updated counterpart
     * @throws IllegalArgumentException if counterpart not found or name is invalid
     */
    @Transactional
    suspend fun updateCounterpartName(
        counterpartId: Long,
        newName: String,
    ): Counterpart {
        require(newName.isNotBlank()) { "Counterpart name cannot be blank" }
        require(newName.length <= MAX_NAME_LENGTH) { "Counterpart name cannot exceed $MAX_NAME_LENGTH characters" }

        val counterpart =
            counterpartRepository.findById(counterpartId)
                ?: throw IllegalArgumentException("Counterpart not found with id: $counterpartId")

        val updatedCounterpart =
            counterpart.copy(
                name = newName,
                updatedAt = LocalDateTime.now(),
            )

        return counterpartRepository.update(updatedCounterpart)
    }

    /**
     * Deletes a counterpart.
     * Only allowed if no transactions reference this counterpart.
     *
     * @param counterpartId Counterpart ID to delete
     * @throws IllegalStateException if counterpart has associated transactions
     */
    @Transactional
    suspend fun deleteCounterpart(counterpartId: Long) {
        val transactionCount = counterpartRepository.countTransactionsByCounterpartId(counterpartId)

        check(transactionCount == 0L) {
            "Cannot delete counterpart with $transactionCount associated transactions"
        }

        counterpartRepository.deleteById(counterpartId)
    }

    /**
     * Counts the number of transactions associated with a counterpart.
     *
     * @param counterpartId Counterpart ID
     * @return Number of non-deleted transactions
     */
    suspend fun getTransactionCount(counterpartId: Long): Long {
        return counterpartRepository.countTransactionsByCounterpartId(counterpartId)
    }
}
