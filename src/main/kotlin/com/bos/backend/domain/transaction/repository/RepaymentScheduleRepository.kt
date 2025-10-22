package com.bos.backend.domain.transaction.repository

import com.bos.backend.domain.transaction.entity.RepaymentSchedule
import com.bos.backend.domain.transaction.enum.RepaymentStatus
import java.time.LocalDate

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
        scheduledStatus: RepaymentStatus = RepaymentStatus.SCHEDULED,
        inProgressStatus: RepaymentStatus = RepaymentStatus.IN_PROGRESS,
    ): Int

    suspend fun updateInProgressStatuses(
        startDate: LocalDate,
        endDate: LocalDate,
        inProgressStatus: RepaymentStatus = RepaymentStatus.IN_PROGRESS,
        scheduledStatus: RepaymentStatus = RepaymentStatus.SCHEDULED,
    ): Int
}
