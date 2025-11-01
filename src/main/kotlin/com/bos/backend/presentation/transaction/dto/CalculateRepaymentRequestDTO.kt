package com.bos.backend.presentation.transaction.dto

import com.bos.backend.domain.transaction.enum.RepaymentType
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal
import java.time.LocalDate

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "repaymentType",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = FixedMonthlyCalculationRequest::class, name = "FIXED_MONTHLY"),
    JsonSubTypes.Type(value = DividedByPeriodCalculationRequest::class, name = "DIVIDED_BY_PERIOD"),
)
sealed interface CalculateRepaymentRequest {
    val repaymentType: RepaymentType
    val startDate: LocalDate
    val totalAmount: BigDecimal
    val completedAmount: BigDecimal?
    val paymentDay: Int
}

data class FixedMonthlyCalculationRequest(
    @field:NotNull(message = "거래 시작일은 필수입니다")
    override val startDate: LocalDate,
    @field:NotNull(message = "전체 금액은 필수입니다")
    @field:DecimalMin(value = "10000", message = "전체 금액은 10000원 이상이어야 합니다")
    override val totalAmount: BigDecimal,
    @field:PositiveOrZero(message = "완료된 금액은 0 이상이어야 합니다")
    override val completedAmount: BigDecimal? = BigDecimal.ZERO,
    @field:NotNull(message = "월 납부 금액은 필수입니다")
    @field:DecimalMin(value = "1000", message = "월 납부 금액은 1000원 이상이어야 합니다")
    val monthlyAmount: BigDecimal,
    @field:NotNull(message = "매달 납부일은 필수입니다")
    @field:Min(value = 1, message = "매달 납부일은 1 이상이어야 합니다")
    @field:Max(value = 31, message = "매달 납부일은 31 이하여야 합니다")
    override val paymentDay: Int,
) : CalculateRepaymentRequest {
    override val repaymentType: RepaymentType = RepaymentType.FIXED_MONTHLY
}

data class DividedByPeriodCalculationRequest(
    @field:NotNull(message = "거래 시작일은 필수입니다")
    override val startDate: LocalDate,
    @field:NotNull(message = "전체 금액은 필수입니다")
    @field:DecimalMin(value = "10000", message = "전체 금액은 10000원 이상이어야 합니다")
    override val totalAmount: BigDecimal,
    @field:PositiveOrZero(message = "완료된 금액은 0 이상이어야 합니다")
    override val completedAmount: BigDecimal? = BigDecimal.ZERO,
    @field:NotNull(message = "완료 예정일은 필수입니다")
    val targetDate: LocalDate,
    @field:NotNull(message = "매달 납부일은 필수입니다")
    @field:Min(value = 1, message = "매달 납부일은 1 이상이어야 합니다")
    @field:Max(value = 31, message = "매달 납부일은 31 이하여야 합니다")
    override val paymentDay: Int,
) : CalculateRepaymentRequest {
    override val repaymentType: RepaymentType = RepaymentType.DIVIDED_BY_PERIOD
}
