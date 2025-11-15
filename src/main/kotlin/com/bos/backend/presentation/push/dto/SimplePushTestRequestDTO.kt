package com.bos.backend.presentation.push.dto

/**
 * 간편 푸시 테스트 요청 DTO
 * userId만 입력하면 자동으로 최신 거래 내역과 스케줄을 조회하여 푸시 전송
 */
data class SimplePushTestRequestDTO(
    val userId: Long,
)
