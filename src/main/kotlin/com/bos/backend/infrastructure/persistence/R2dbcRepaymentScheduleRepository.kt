package com.bos.backend.infrastructure.persistence

import com.bos.backend.domain.transaction.entity.RepaymentSchedule
import com.bos.backend.domain.transaction.enum.RepaymentStatus
import com.bos.backend.domain.transaction.repository.RepaymentScheduleRepository
import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface R2dbcRepaymentScheduleRepository :
    RepaymentScheduleRepository,
    CoroutineCrudRepository<RepaymentSchedule, Long> {
    override suspend fun findByTransactionId(transactionId: Long): List<RepaymentSchedule>

    override suspend fun findByTransactionIdIn(transactionIds: List<Long>): List<RepaymentSchedule>

    @Query(
        """
        SELECT * FROM repayment_schedules
        WHERE transaction_id = :transactionId
        AND scheduled_date = :scheduledDate
        LIMIT 1
        """,
    )
    override suspend fun findByTransactionIdAndScheduledDate(
        transactionId: Long,
        scheduledDate: LocalDate,
    ): RepaymentSchedule?

    @Query(
        """
        SELECT * FROM repayment_schedules
        WHERE transaction_id = :transactionId
        AND status != 'COMPLETED'
        ORDER BY scheduled_date ASC
        """,
    )
    override suspend fun findPendingSchedulesByTransactionId(transactionId: Long): List<RepaymentSchedule>

    @Modifying
    @Query(
        """
        UPDATE repayment_schedules
        SET status = :overdueStatus, updated_at = CURRENT_TIMESTAMP
        WHERE scheduled_date < :today
        AND status != :completedStatus
        """,
    )
    override suspend fun updateOverdueStatuses(
        today: LocalDate,
        overdueStatus: RepaymentStatus,
        completedStatus: RepaymentStatus,
    ): Int

    @Modifying
    @Query(
        """
        UPDATE repayment_schedules
        SET status = :inProgressStatus, updated_at = CURRENT_TIMESTAMP
        WHERE scheduled_date >= :startDate
        AND scheduled_date <= :endDate
        AND status != :completedStatus
        """,
    )
    override suspend fun updateInProgressStatuses(
        startDate: LocalDate,
        endDate: LocalDate,
        inProgressStatus: RepaymentStatus,
        completedStatus: RepaymentStatus,
    ): Int

    @Query(
        """
        SELECT * FROM repayment_schedules
        WHERE scheduled_date = :targetDate
        AND status IN ('SCHEDULED', 'IN_PROGRESS')
        ORDER BY scheduled_date ASC
        """,
    )
    override suspend fun findSchedulesForReminder(targetDate: LocalDate): List<RepaymentSchedule>

    @Query(
        """
        SELECT * FROM repayment_schedules
        WHERE scheduled_date = :today
        AND status = 'IN_PROGRESS'
        ORDER BY scheduled_date ASC
        """,
    )
    override suspend fun findSchedulesForToday(today: LocalDate): List<RepaymentSchedule>

    @Query(
        """
        SELECT * FROM repayment_schedules
        WHERE status = 'OVERDUE'
        ORDER BY scheduled_date ASC
        """,
    )
    override suspend fun findOverdueSchedules(): List<RepaymentSchedule>
}
