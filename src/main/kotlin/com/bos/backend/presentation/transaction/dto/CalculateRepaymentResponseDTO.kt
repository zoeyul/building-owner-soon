package com.bos.backend.presentation.transaction.dto

import com.bos.backend.domain.transaction.enum.RepaymentType
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.math.BigDecimal
import java.time.LocalDate

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "repaymentType",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = FixedMonthlyCalculationResponse::class, name = "FIXED_MONTHLY"),
    JsonSubTypes.Type(value = DividedByPeriodCalculationResponse::class, name = "DIVIDED_BY_PERIOD"),
)
sealed interface CalculateRepaymentResponse {
    val repaymentType: RepaymentType
}

data class FixedMonthlyCalculationResponse(
    val monthlyAmount: BigDecimal,
) : CalculateRepaymentResponse {
    override val repaymentType: RepaymentType = RepaymentType.FIXED_MONTHLY
}

data class DividedByPeriodCalculationResponse(
    val completionDate: LocalDate,
    val monthsLater: Int,
) : CalculateRepaymentResponse {
    override val repaymentType: RepaymentType = RepaymentType.DIVIDED_BY_PERIOD
}
