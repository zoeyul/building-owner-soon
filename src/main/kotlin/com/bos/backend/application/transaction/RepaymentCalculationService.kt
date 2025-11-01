package com.bos.backend.application.transaction

import com.bos.backend.application.CommonErrorCode
import com.bos.backend.application.CustomException
import com.bos.backend.domain.transaction.enum.RepaymentType
import com.bos.backend.presentation.transaction.dto.CalculateRepaymentRequest
import com.bos.backend.presentation.transaction.dto.CalculateRepaymentResponse
import com.bos.backend.presentation.transaction.dto.DividedByPeriodCalculationRequest
import com.bos.backend.presentation.transaction.dto.DividedByPeriodCalculationResponse
import com.bos.backend.presentation.transaction.dto.FixedMonthlyCalculationRequest
import com.bos.backend.presentation.transaction.dto.FixedMonthlyCalculationResponse
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class RepaymentCalculationService(
    private val repaymentScheduleCalculator: RepaymentScheduleCalculator,
) {
    fun calculateRepayment(request: CalculateRepaymentRequest): CalculateRepaymentResponse =
        when (request.repaymentType) {
            RepaymentType.FIXED_MONTHLY -> calculateFixedMonthly(request as FixedMonthlyCalculationRequest)
            RepaymentType.DIVIDED_BY_PERIOD -> calculateDividedByPeriod(request as DividedByPeriodCalculationRequest)
            RepaymentType.FLEXIBLE ->
                throw CustomException(
                    CommonErrorCode.INVALID_PARAMETER.name,
                    "FLEXIBLE 타입은 계산을 지원하지 않습니다",
                )
        }

    private fun calculateFixedMonthly(request: FixedMonthlyCalculationRequest): FixedMonthlyCalculationResponse {
        val remainingAmount = request.totalAmount - (request.completedAmount ?: BigDecimal.ZERO)

        if (remainingAmount <= BigDecimal.ZERO) {
            throw CustomException(CommonErrorCode.INVALID_PARAMETER.name, "남은 금액이 0보다 커야 합니다")
        }

        if (request.monthlyAmount <= BigDecimal.ZERO) {
            throw CustomException(CommonErrorCode.INVALID_PARAMETER.name, "월 납부 금액은 0보다 커야 합니다")
        }

        if (request.monthlyAmount > remainingAmount) {
            throw CustomException(CommonErrorCode.INVALID_PARAMETER.name, "월 납부 금액이 남은 금액보다 클 수 없습니다")
        }

        // RepaymentScheduleCalculator를 사용하여 계산
        val completionDateInfo =
            repaymentScheduleCalculator.calculateCompletionDate(
                startDate = request.startDate,
                paymentDay = request.paymentDay,
                monthlyAmount = request.monthlyAmount,
                remainingAmount = remainingAmount,
            )

        return FixedMonthlyCalculationResponse(
            completionDate = completionDateInfo.completionDate,
            monthsLater = completionDateInfo.monthsLater,
        )
    }

    private fun calculateDividedByPeriod(
        request: DividedByPeriodCalculationRequest,
    ): DividedByPeriodCalculationResponse {
        val remainingAmount = request.totalAmount - (request.completedAmount ?: BigDecimal.ZERO)

        if (remainingAmount <= BigDecimal.ZERO) {
            throw CustomException(CommonErrorCode.INVALID_PARAMETER.name, "남은 금액이 0보다 커야 합니다")
        }

        if (request.targetDate.isBefore(request.startDate) || request.targetDate.isEqual(request.startDate)) {
            throw CustomException(CommonErrorCode.INVALID_PARAMETER.name, "완료 예정일은 시작일 이후여야 합니다")
        }

        // RepaymentScheduleCalculator를 사용하여 계산
        val monthlyAmount =
            repaymentScheduleCalculator.calculateMonthlyAmount(
                startDate = request.startDate,
                targetDate = request.targetDate,
                paymentDay = request.paymentDay,
                remainingAmount = remainingAmount,
            )

        if (monthlyAmount <= BigDecimal.ZERO) {
            throw CustomException(CommonErrorCode.INVALID_PARAMETER.name, "납부 기간이 너무 짧습니다")
        }

        return DividedByPeriodCalculationResponse(monthlyAmount = monthlyAmount)
    }
}
