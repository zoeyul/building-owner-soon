package com.bos.backend.domain.transaction.entity

import com.bos.backend.domain.counterpart.entity.Counterpart

/**
 * Transaction with joined Counterpart data
 * Used for queries that need counterpart information
 */
data class TransactionWithCounterpart(
    val transaction: Transaction,
    val counterpart: Counterpart,
) {
    val counterpartName: String
        get() = counterpart.name

    val counterpartCharacter: CounterpartCharacter
        get() = counterpart.character
}
