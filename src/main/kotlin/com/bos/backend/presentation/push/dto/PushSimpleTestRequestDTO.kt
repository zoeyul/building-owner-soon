package com.bos.backend.presentation.push.dto

import jakarta.validation.constraints.NotNull

data class PushSimpleTestRequestDTO(
    @field:NotNull(message = "사용자 ID는 필수입니다")
    val userId: Long,
)
