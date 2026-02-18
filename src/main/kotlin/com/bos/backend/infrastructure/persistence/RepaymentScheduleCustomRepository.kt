package com.bos.backend.infrastructure.persistence

import com.bos.backend.domain.transaction.entity.RepaymentSchedule
import com.bos.backend.domain.transaction.enum.RepaymentStatus
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

interface RepaymentScheduleCustomRepository {
    suspend fun findSchedulesForReminderWithFilter(
        targetDate: LocalDate,
        userId: Long?,
        transactionId: Long?,
    ): List<RepaymentSchedule>

    suspend fun findSchedulesForTodayWithFilter(
        today: LocalDate,
        userId: Long?,
        transactionId: Long?,
    ): List<RepaymentSchedule>

    suspend fun findOverdueSchedulesWithFilter(
        yesterday: LocalDate,
        userId: Long?,
        transactionId: Long?,
    ): List<RepaymentSchedule>

    suspend fun findLastCompletedDatesByTransactionIds(transactionIds: List<Long>): Map<Long, LocalDate>
}

@Suppress("LongMethod")
@Repository
class RepaymentScheduleCustomRepositoryImpl(
    private val databaseClient: DatabaseClient,
) : RepaymentScheduleCustomRepository {
    override suspend fun findSchedulesForReminderWithFilter(
        targetDate: LocalDate,
        userId: Long?,
        transactionId: Long?,
    ): List<RepaymentSchedule> {
        val hasFilter = userId != null || transactionId != null

        val sql =
            if (hasFilter) {
                buildString {
                    append(
                        """
                        SELECT rs.* FROM repayment_schedules rs
                        JOIN transactions t ON rs.transaction_id = t.id
                        WHERE rs.scheduled_date = :targetDate
                        AND rs.status IN ('SCHEDULED', 'IN_PROGRESS')
                        """.trimIndent(),
                    )
                    if (userId != null) {
                        append(" AND t.user_id = :userId")
                    }
                    if (transactionId != null) {
                        append(" AND rs.transaction_id = :transactionId")
                    }
                    append(" ORDER BY rs.scheduled_date ASC")
                }
            } else {
                """
                SELECT * FROM repayment_schedules
                WHERE scheduled_date = :targetDate
                AND status IN ('SCHEDULED', 'IN_PROGRESS')
                ORDER BY scheduled_date ASC
                """.trimIndent()
            }

        var spec = databaseClient.sql(sql).bind("targetDate", targetDate)
        if (userId != null) {
            spec = spec.bind("userId", userId)
        }
        if (transactionId != null) {
            spec = spec.bind("transactionId", transactionId)
        }

        return spec.fetch().all().collectList().awaitSingle().map { row ->
            mapToRepaymentSchedule(row)
        }
    }

    override suspend fun findSchedulesForTodayWithFilter(
        today: LocalDate,
        userId: Long?,
        transactionId: Long?,
    ): List<RepaymentSchedule> {
        val hasFilter = userId != null || transactionId != null

        val sql =
            if (hasFilter) {
                buildString {
                    append(
                        """
                        SELECT rs.* FROM repayment_schedules rs
                        JOIN transactions t ON rs.transaction_id = t.id
                        WHERE rs.scheduled_date = :today
                        AND rs.status = 'IN_PROGRESS'
                        """.trimIndent(),
                    )
                    if (userId != null) {
                        append(" AND t.user_id = :userId")
                    }
                    if (transactionId != null) {
                        append(" AND rs.transaction_id = :transactionId")
                    }
                    append(" ORDER BY rs.scheduled_date ASC")
                }
            } else {
                """
                SELECT * FROM repayment_schedules
                WHERE scheduled_date = :today
                AND status = 'IN_PROGRESS'
                ORDER BY scheduled_date ASC
                """.trimIndent()
            }

        var spec = databaseClient.sql(sql).bind("today", today)
        if (userId != null) {
            spec = spec.bind("userId", userId)
        }
        if (transactionId != null) {
            spec = spec.bind("transactionId", transactionId)
        }

        return spec.fetch().all().collectList().awaitSingle().map { row ->
            mapToRepaymentSchedule(row)
        }
    }

    override suspend fun findOverdueSchedulesWithFilter(
        yesterday: LocalDate,
        userId: Long?,
        transactionId: Long?,
    ): List<RepaymentSchedule> {
        val hasFilter = userId != null || transactionId != null

        val sql =
            if (hasFilter) {
                buildString {
                    append(
                        """
                        SELECT rs.* FROM repayment_schedules rs
                        JOIN transactions t ON rs.transaction_id = t.id
                        WHERE rs.status = 'OVERDUE'
                        AND rs.scheduled_date = :yesterday
                        """.trimIndent(),
                    )
                    if (userId != null) {
                        append(" AND t.user_id = :userId")
                    }
                    if (transactionId != null) {
                        append(" AND rs.transaction_id = :transactionId")
                    }
                    append(" ORDER BY rs.scheduled_date ASC")
                }
            } else {
                """
                SELECT * FROM repayment_schedules
                WHERE status = 'OVERDUE'
                AND scheduled_date = :yesterday
                ORDER BY scheduled_date ASC
                """.trimIndent()
            }

        var spec = databaseClient.sql(sql).bind("yesterday", yesterday)
        if (userId != null) {
            spec = spec.bind("userId", userId)
        }
        if (transactionId != null) {
            spec = spec.bind("transactionId", transactionId)
        }

        return spec.fetch().all().collectList().awaitSingle().map { row ->
            mapToRepaymentSchedule(row)
        }
    }

    override suspend fun findLastCompletedDatesByTransactionIds(transactionIds: List<Long>): Map<Long, LocalDate> {
        if (transactionIds.isEmpty()) {
            return emptyMap()
        }

        val placeholders = transactionIds.indices.joinToString(", ") { ":id$it" }

        val sql =
            """
            SELECT transaction_id, MAX(actual_date) AS last_completed_date
            FROM repayment_schedules
            WHERE transaction_id IN ($placeholders)
            AND status = 'COMPLETED'
            AND actual_date IS NOT NULL
            GROUP BY transaction_id
            """.trimIndent()

        var spec = databaseClient.sql(sql)
        transactionIds.forEachIndexed { index, id ->
            spec = spec.bind("id$index", id)
        }

        return spec.fetch().all().collectList().awaitSingle().associate { row ->
            val transactionId = (row["transaction_id"] as Number).toLong()
            val lastCompletedDate = row["last_completed_date"] as LocalDate
            transactionId to lastCompletedDate
        }
    }

    private fun mapToRepaymentSchedule(row: Map<String, Any>): RepaymentSchedule {
        return RepaymentSchedule(
            id = (row["id"] as Number).toLong(),
            transactionId = (row["transaction_id"] as Number).toLong(),
            scheduledDate = row["scheduled_date"] as LocalDate,
            scheduledAmount = row["scheduled_amount"] as BigDecimal,
            actualDate = row["actual_date"] as? LocalDate,
            actualAmount = row["actual_amount"] as? BigDecimal,
            status = RepaymentStatus.valueOf(row["status"] as String),
            createdAt = toInstant(row["created_at"]),
            updatedAt = toInstant(row["updated_at"]),
        )
    }

    private fun toInstant(value: Any?): Instant {
        return when (value) {
            is Instant -> value
            is LocalDateTime -> value.atZone(ZoneId.of("Asia/Seoul")).toInstant()
            else -> Instant.now()
        }
    }
}
