package com.bos.backend.domain.transaction.repository

import com.bos.backend.domain.transaction.entity.RepaymentSchedule
import com.bos.backend.domain.transaction.enum.RepaymentStatus
import java.time.LocalDate

@Suppress("TooManyFunctions")
interface RepaymentScheduleRepository {
    suspend fun save(repaymentSchedule: RepaymentSchedule): RepaymentSchedule

    suspend fun saveAll(repaymentSchedules: List<RepaymentSchedule>): List<RepaymentSchedule>

    suspend fun findById(id: Long): RepaymentSchedule?

    suspend fun findByTransactionId(transactionId: Long): List<RepaymentSchedule>

    suspend fun findByTransactionIdIn(transactionIds: List<Long>): List<RepaymentSchedule>

    suspend fun findByTransactionIdAndScheduledDate(
        transactionId: Long,
        scheduledDate: LocalDate,
    ): RepaymentSchedule?

    suspend fun findPendingSchedulesByTransactionId(transactionId: Long): List<RepaymentSchedule>

    suspend fun updateOverdueStatuses(
        today: LocalDate,
        overdueStatus: RepaymentStatus = RepaymentStatus.OVERDUE,
        completedStatus: RepaymentStatus = RepaymentStatus.COMPLETED,
    ): Int

    suspend fun updateInProgressStatuses(
        startDate: LocalDate,
        endDate: LocalDate,
        inProgressStatus: RepaymentStatus = RepaymentStatus.IN_PROGRESS,
        completedStatus: RepaymentStatus = RepaymentStatus.COMPLETED,
    ): Int

    suspend fun findSchedulesForReminder(targetDate: LocalDate): List<RepaymentSchedule>

    suspend fun findSchedulesForToday(today: LocalDate): List<RepaymentSchedule>

    suspend fun findOverdueSchedules(): List<RepaymentSchedule>
}
