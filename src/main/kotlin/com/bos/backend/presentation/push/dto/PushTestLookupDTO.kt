package com.bos.backend.presentation.push.dto

import com.bos.backend.domain.transaction.enum.RepaymentStatus
import com.bos.backend.domain.transaction.enum.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 푸시 테스트 데이터 조회 응답 DTO
 */
data class PushTestLookupResponseDTO(
    val userId: Long,
    val nickname: String,
    val transactions: List<TransactionForPushTestDTO>,
)

/**
 * 푸시 테스트용 거래 정보 DTO
 */
data class TransactionForPushTestDTO(
    val transactionId: Long,
    val counterpartName: String,
    val transactionType: TransactionType,
    val totalAmount: BigDecimal,
    val schedules: List<ScheduleForPushTestDTO>,
    val completedPercentage: Int,
    val lastCompletedAmount: BigDecimal?,
)

/**
 * 푸시 테스트용 스케줄 정보 DTO
 */
data class ScheduleForPushTestDTO(
    val scheduleId: Long,
    val scheduledDate: LocalDate,
    val scheduledAmount: BigDecimal,
    val status: RepaymentStatus,
)
