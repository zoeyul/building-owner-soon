package com.bos.backend.application.transaction.policy

import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 고정 월납 상환 정책 (FIXED_MONTHLY)
 *
 * 특징:
 * - 매월 고정된 금액을 상환
 * - 남은 잔액이 월 상환 금액보다 작으면 잔액 전부 상환
 * - 마지막 회차에는 남은 전체 금액을 상환
 *
 * 계산 로직:
 * 1. 잔액이 0이 될 때까지 반복
 * 2. 각 회차는 고정 월 상환 금액 사용
 * 3. 마지막 회차는 남은 잔액 전부 (월 상환 금액보다 클 수도 작을 수도 있음)
 *
 * 예시:
 * - 500,000원을 80,000원씩
 *   - 1~6회: 80,000원
 *   - 7회: 20,000원 (500,000 - 480,000)
 *
 * - 235,000원을 67,000원씩
 *   - 1~3회: 67,000원
 *   - 4회: 34,000원 (235,000 - 201,000)
 *
 * - 50,000원을 100,000원씩
 *   - 1회: 50,000원 (잔액이 월액보다 작음)
 */
@Component
class FixedMonthlyPolicy : RepaymentAmountPolicy {
    companion object {
        private const val DECIMAL_SCALE = 2
    }

    override fun calculateSchedule(params: ScheduleCalculationParams): List<PaymentSchedule> {
        requireNotNull(params.monthlyAmount) { "월 납부액은 필수입니다" }

        val schedules = mutableListOf<PaymentSchedule>()
        var currentDate = calculateNextPaymentDate(params.startDate, params.paymentDay)
        var remaining = params.remainingAmount

        while (remaining > BigDecimal.ZERO) {
            // 남은 잔액이 월 상환 금액보다 작거나 같으면 잔액 전부,
            // 그렇지 않으면 월 상환 금액 사용
            val paymentAmount =
                if (remaining <= params.monthlyAmount) {
                    remaining
                } else {
                    params.monthlyAmount
                }

            schedules.add(PaymentSchedule(currentDate, paymentAmount))

            remaining = remaining.subtract(paymentAmount)
            currentDate = calculateNextPaymentDate(currentDate, params.paymentDay)
        }

        return schedules
    }

    override fun calculateMonthlyAmount(params: ScheduleCalculationParams): BigDecimal {
        // FIXED_MONTHLY는 월 납부액이 이미 정해져 있음
        return params.monthlyAmount ?: BigDecimal.ZERO
    }

    override fun calculateCompletionDate(params: ScheduleCalculationParams): CompletionDateInfo? {
        requireNotNull(params.monthlyAmount) { "월 납부액은 필수입니다" }

        // 남은 개월 수 = ceil(남은 금액 ÷ 월 납부액) - 올림 처리
        val monthsNeeded =
            params
                .remainingAmount
                .divide(params.monthlyAmount, DECIMAL_SCALE, RoundingMode.UP)
                .setScale(0, RoundingMode.UP)
                .toInt()

        if (monthsNeeded <= 0) {
            return CompletionDateInfo(params.startDate, 0)
        }

        // 완료 예정일 = 시작일 + 남은 개월 수 (납부일 적용)
        var completionDate = calculateNextPaymentDate(params.startDate, params.paymentDay)
        repeat(monthsNeeded - 1) {
            completionDate = calculateNextPaymentDate(completionDate, params.paymentDay)
        }

        return CompletionDateInfo(completionDate, monthsNeeded)
    }
}
