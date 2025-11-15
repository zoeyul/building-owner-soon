package com.bos.backend.application.transaction.policy

import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * 균등 분할 상환 정책 (DIVIDED_BY_PERIOD)
 *
 * 특징:
 * - 목표일까지 금액을 균등하게 분할
 * - 100원 단위로 절삭하여 사용자 친화적인 금액 생성
 * - 나머지 금액은 마지막 회차에 포함
 *
 * 계산 로직:
 * 1. 시작일부터 목표일까지의 납부 날짜 목록 생성
 * 2. 전체 금액을 100원 단위로 절삭하여 균등 분할
 * 3. 절삭된 금액으로 각 회차 금액 설정
 * 4. 나머지 금액을 마지막 회차에 더함
 *
 * 예시:
 * - 10,000원 ÷ 3개월
 *   - 기본 금액: (10,000 / 100) / 3 * 100 = 3,300원
 *   - 결과: [3,300, 3,300, 3,400]
 *
 * - 100,000원 ÷ 7개월
 *   - 기본 금액: (100,000 / 100) / 7 * 100 = 14,200원
 *   - 결과: [14,200 × 6회, 14,800]
 */
@Component
class DividedByPeriodPolicy : RepaymentAmountPolicy {
    companion object {
        private const val ROUNDING_UNIT = 100 // 100원 단위 절삭을 위한 상수
    }

    override fun calculateSchedule(params: ScheduleCalculationParams): List<PaymentSchedule> {
        requireNotNull(params.targetDate) { "목표일은 필수입니다" }

        val schedules = mutableListOf<PaymentSchedule>()
        var currentDate = calculateNextPaymentDate(params.startDate, params.paymentDay)

        // 납부 날짜 리스트 생성
        val paymentDates = mutableListOf<java.time.LocalDate>()
        while (currentDate.isBefore(params.targetDate) || currentDate.isEqual(params.targetDate)) {
            paymentDates.add(currentDate)
            // 다음 달로 이동 (paymentDay 조정 포함)
            val nextMonth = currentDate.plusMonths(1)
            val lastDayOfNextMonth = nextMonth.lengthOfMonth()
            val adjustedDay = if (params.paymentDay > lastDayOfNextMonth) lastDayOfNextMonth else params.paymentDay
            currentDate = nextMonth.withDayOfMonth(adjustedDay)
        }

        if (paymentDates.isEmpty()) {
            return emptyList()
        }

        // 100원 단위 절삭 균등 분할 계산
        val baseAmount = calculateRoundedBaseAmount(params.remainingAmount, paymentDates.size)
        val totalBaseAmount = baseAmount.multiply(BigDecimal(paymentDates.size))
        val remainder = params.remainingAmount.subtract(totalBaseAmount)

        paymentDates.forEachIndexed { index, paymentDate ->
            val amount =
                if (index == paymentDates.size - 1) {
                    // 마지막 납부는 기본 금액 + 나머지 금액
                    baseAmount.add(remainder)
                } else {
                    baseAmount
                }

            schedules.add(PaymentSchedule(paymentDate, amount))
        }

        return schedules
    }

    override fun calculateMonthlyAmount(params: ScheduleCalculationParams): BigDecimal {
        requireNotNull(params.targetDate) { "목표일은 필수입니다" }

        // 납부 날짜 리스트 생성
        var currentDate = calculateNextPaymentDate(params.startDate, params.paymentDay)
        val paymentDates = mutableListOf<java.time.LocalDate>()

        while (currentDate.isBefore(params.targetDate) || currentDate.isEqual(params.targetDate)) {
            paymentDates.add(currentDate)
            // 다음 달로 이동 (paymentDay 조정 포함)
            val nextMonth = currentDate.plusMonths(1)
            val lastDayOfNextMonth = nextMonth.lengthOfMonth()
            val adjustedDay = if (params.paymentDay > lastDayOfNextMonth) lastDayOfNextMonth else params.paymentDay
            currentDate = nextMonth.withDayOfMonth(adjustedDay)
        }

        if (paymentDates.isEmpty()) {
            return BigDecimal.ZERO
        }

        return calculateRoundedBaseAmount(params.remainingAmount, paymentDates.size)
    }

    override fun calculateCompletionDate(params: ScheduleCalculationParams): CompletionDateInfo? {
        // DIVIDED_BY_PERIOD는 목표일이 이미 정해져 있으므로 완료일 계산 불필요
        return null
    }

    /**
     * 100원 단위로 절삭된 기본 금액 계산
     *
     * 로직:
     * 1. 전체 금액을 100으로 나눔 (100원 단위 제거)
     * 2. 회차 수로 나눔 (정수 나눗셈으로 자동 내림)
     * 3. 100을 곱함 (100원 단위로 복원)
     *
     * 예: 10,000원 ÷ 3개월
     * - 10,000 / 100 = 100
     * - 100 / 3 = 33 (정수 나눗셈)
     * - 33 * 100 = 3,300원
     */
    private fun calculateRoundedBaseAmount(
        totalAmount: BigDecimal,
        periods: Int,
    ): BigDecimal {
        val amountInHundreds = totalAmount.divide(BigDecimal(ROUNDING_UNIT)).toBigInteger()
        val baseInHundreds = amountInHundreds.divide(java.math.BigInteger.valueOf(periods.toLong()))
        return BigDecimal(baseInHundreds.multiply(java.math.BigInteger.valueOf(ROUNDING_UNIT.toLong())))
    }
}
