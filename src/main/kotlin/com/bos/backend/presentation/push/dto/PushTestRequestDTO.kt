package com.bos.backend.presentation.push.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

data class PushTestRequestDTO(
    @field:NotBlank(message = "푸시 메시지는 필수입니다")
    val message: String,
    @field:NotBlank(message = "딥링크 타입은 필수입니다")
    val deepLinkType: String,
    @field:NotNull(message = "트랜잭션 ID는 필수입니다")
    @field:Positive(message = "트랜잭션 ID는 양수여야 합니다")
    val transactionId: Long,
)

enum class DeepLinkType {
    REPAYMENT,
    TRANSACTION,
    ;

    companion object {
        fun fromString(value: String): DeepLinkType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("지원하지 않는 딥링크 타입입니다: $value")
        }
    }
}
