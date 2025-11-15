package com.bos.backend.presentation.push.dto

/**
 * 상환 관련 푸시 테스트 요청 DTO (Base)
 */
data class RepaymentPushTestRequestDTO(
    val userId: Long,
    val nickname: String,
    val partnerName: String,
    val amount: Long,
    val transactionId: Long,
    val scheduleId: Long,
)

/**
 * 상환 지연 경고 푸시 테스트 요청 DTO
 * (partnerName이 필요 없음)
 */
data class RepaymentOverduePushTestRequestDTO(
    val userId: Long,
    val nickname: String,
    val amount: Long,
    val transactionId: Long,
    val scheduleId: Long,
)

/**
 * 부분 상환 완료 푸시 테스트 요청 DTO
 */
data class PartialRepaymentCompletePushTestRequestDTO(
    val userId: Long,
    val nickname: String,
    val partnerName: String,
    val amount: Long,
    val progress: Int,
    val transactionId: Long,
)

/**
 * 거래 완료 푸시 테스트 요청 DTO
 */
data class TransactionCompletePushTestRequestDTO(
    val userId: Long,
    val nickname: String,
    val partnerName: String,
    val transactionId: Long,
)
