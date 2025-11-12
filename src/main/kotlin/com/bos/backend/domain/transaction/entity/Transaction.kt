package com.bos.backend.domain.transaction.entity
import com.bos.backend.domain.transaction.enum.RelationshipType
import com.bos.backend.domain.transaction.enum.RepaymentType
import com.bos.backend.domain.transaction.enum.TransactionType
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Table("transactions")
data class Transaction(
    @Id
    val id: Long? = null,
    val uuid: String = UUID.randomUUID().toString(),
    @Column("user_id")
    val userId: Long,
    @Column("transaction_type")
    val transactionType: TransactionType,
    @Column("counterpart_name")
    val counterpartName: String,
    @Column("counterpart_character")
    val counterpartCharacter: CounterpartCharacter,
    @Column("relationship")
    val relationship: RelationshipType,
    @Column("custom_relationship")
    val customRelationship: String?,
    @Column("transaction_date")
    val transactionDate: LocalDate,
    @Column("total_amount")
    val totalAmount: BigDecimal,
    @Column("completed_amount")
    val completedAmount: BigDecimal = BigDecimal.ZERO,
    val memo: String?,
    @Column("repayment_type")
    val repaymentType: RepaymentType,
    @Column("target_date")
    val targetDate: LocalDate?,
    @Column("monthly_amount")
    val monthlyAmount: BigDecimal?,
    @Column("payment_day")
    val paymentDay: Int?,
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
    @Column("updated_at")
    val updatedAt: Instant = Instant.now(),
    @Column("deleted_at")
    val deletedAt: Instant? = null,
) {
    fun remainingAmount(): BigDecimal = totalAmount - completedAmount

    fun isCompleted(): Boolean = remainingAmount() <= BigDecimal.ZERO

    fun isDeleted(): Boolean = deletedAt != null

    fun updateCompletedAmount(newCompletedAmount: BigDecimal): Transaction =
        this.copy(
            completedAmount = newCompletedAmount,
            updatedAt = Instant.now(),
        )

    fun delete(): Transaction =
        this.copy(
            deletedAt = Instant.now(),
            updatedAt = Instant.now(),
        )
}
