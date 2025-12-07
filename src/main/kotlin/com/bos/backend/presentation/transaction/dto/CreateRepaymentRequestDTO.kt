package com.bos.backend.presentation.transaction.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDate

data class CreateRepaymentRequestDTO(
    val scheduleId: Long? = null,
    @field:NotNull(message = "상환일은 필수입니다")
    val repaymentDate: LocalDate,
    @field:NotNull(message = "상환 금액은 필수입니다")
    @field:DecimalMin(value = "0.01", message = "상환 금액은 0보다 커야 합니다")
    val repaymentAmount: BigDecimal,
)
