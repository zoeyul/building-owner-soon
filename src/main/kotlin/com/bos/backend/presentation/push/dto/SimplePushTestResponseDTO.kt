package com.bos.backend.presentation.push.dto

import java.math.BigDecimal
import java.time.LocalDate

/**
 * 간편 푸시 테스트 응답 DTO
 */
data class SimplePushTestResponseDTO(
    val success: Boolean,
    val message: String,
    val sentCount: Int,
    val usedData: UsedTestData?,
    val error: String? = null,
)

/**
 * 테스트에 사용된 데이터 정보
 */
data class UsedTestData(
    val userId: Long,
    val nickname: String,
    val transactionId: Long,
    val transactionType: String,
    val counterpartName: String,
    val totalAmount: BigDecimal,
    val scheduleId: Long,
    val scheduledDate: LocalDate,
    val scheduledAmount: BigDecimal,
    val templateUsed: String,
)
