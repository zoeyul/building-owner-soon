package com.bos.backend.domain.counterpart.entity

import com.bos.backend.domain.transaction.entity.CounterpartCharacter
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

/**
 * Counterpart entity representing a person the user has financial transactions with.
 *
 * Each counterpart is a separate entity, even if names are identical.
 * This allows:
 * - Multiple people with the same name to be tracked independently
 * - Name and character changes to propagate to all related transactions
 * - Future relationship merging functionality
 */
@Table("counterparts")
data class Counterpart(
    @Id
    val id: Long? = null,
    @Column("user_id")
    val userId: Long,
    val name: String,
    @Column("`character`")
    val character: CounterpartCharacter,
    @Column("created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column("updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
