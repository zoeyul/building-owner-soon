package com.bos.backend.application.transaction

import com.bos.backend.application.transaction.policy.CompletionDateInfo
import com.bos.backend.application.transaction.policy.DividedByPeriodPolicy
import com.bos.backend.application.transaction.policy.FixedMonthlyPolicy
import com.bos.backend.application.transaction.policy.FlexiblePolicy
import com.bos.backend.application.transaction.policy.PaymentSchedule
import com.bos.backend.application.transaction.policy.RepaymentAmountPolicy
import com.bos.backend.application.transaction.policy.ScheduleCalculationParams
import com.bos.backend.application.transaction.policy.calculateNextPaymentDate
import com.bos.backend.domain.transaction.enum.RepaymentType
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 상환 스케줄 계산기
 *
 * Strategy Pattern을 사용하여 각 상환 타입별 정책을 적용
 * 거래내역 생성 시와 계산 API에서 동일한 로직을 사용하도록 통합
 */
@Component
class RepaymentScheduleCalculator(
    private val dividedByPeriodPolicy: DividedByPeriodPolicy,
    private val fixedMonthlyPolicy: FixedMonthlyPolicy,
    private val flexiblePolicy: FlexiblePolicy,
) {
    /**
     * DIVIDED_BY_PERIOD: 목표일까지 균등 분할 (100원 단위 절삭 적용)
     * 시작일부터 목표일까지의 납부 날짜를 계산하고, 금액을 균등 분할
     */
    fun calculateDividedByPeriodSchedule(
        startDate: LocalDate,
        targetDate: LocalDate,
        paymentDay: Int,
        remainingAmount: BigDecimal,
    ): List<PaymentSchedule> {
        val params =
            ScheduleCalculationParams(
                startDate = startDate,
                remainingAmount = remainingAmount,
                paymentDay = paymentDay,
                targetDate = targetDate,
            )
        return dividedByPeriodPolicy.calculateSchedule(params)
    }

    /**
     * FIXED_MONTHLY: 고정 월납 (마지막 회차에 남은 금액 전부)
     * 월 납부액이 정해져 있고, 잔액이 0이 될 때까지 반복
     */
    fun calculateFixedMonthlySchedule(
        startDate: LocalDate,
        paymentDay: Int,
        monthlyAmount: BigDecimal,
        remainingAmount: BigDecimal,
    ): List<PaymentSchedule> {
        val params =
            ScheduleCalculationParams(
                startDate = startDate,
                remainingAmount = remainingAmount,
                paymentDay = paymentDay,
                monthlyAmount = monthlyAmount,
            )
        return fixedMonthlyPolicy.calculateSchedule(params)
    }

    /**
     * 다음 납부 날짜 계산
     *
     * 규칙:
     * - baseDate의 일자가 paymentDay보다 크거나 같으면 다음 달
     * - 그렇지 않으면 이번 달
     * - 해당 월에 paymentDay가 없으면 월말로 조정 (예: 2월 31일 → 2월 28/29일)
     */
    fun calculateNextPaymentDate(
        baseDate: LocalDate,
        paymentDay: Int,
    ): LocalDate {
        return com.bos.backend.application.transaction.policy.calculateNextPaymentDate(baseDate, paymentDay)
    }

    /**
     * FIXED_MONTHLY: 월 납부액으로 완료일 계산
     * 정책: 남은 개월 수 = ceil(남은 금액 ÷ 월 납부액) - 올림 처리
     */
    fun calculateCompletionDate(
        startDate: LocalDate,
        paymentDay: Int,
        monthlyAmount: BigDecimal,
        remainingAmount: BigDecimal,
    ): CompletionDateInfo {
        val params =
            ScheduleCalculationParams(
                startDate = startDate,
                remainingAmount = remainingAmount,
                paymentDay = paymentDay,
                monthlyAmount = monthlyAmount,
            )
        return fixedMonthlyPolicy.calculateCompletionDate(params)
            ?: CompletionDateInfo(startDate, 0)
    }

    /**
     * DIVIDED_BY_PERIOD: 목표일까지의 월 납부액 계산 (100원 단위 절삭 적용)
     * 정책: 월 납부액 = 남은 금액 ÷ 남은 개월 수 (100원 단위 절삭)
     */
    fun calculateMonthlyAmount(
        startDate: LocalDate,
        targetDate: LocalDate,
        paymentDay: Int,
        remainingAmount: BigDecimal,
    ): BigDecimal {
        val params =
            ScheduleCalculationParams(
                startDate = startDate,
                remainingAmount = remainingAmount,
                paymentDay = paymentDay,
                targetDate = targetDate,
            )
        return dividedByPeriodPolicy.calculateMonthlyAmount(params)
    }

    /**
     * 상환 타입에 따라 적절한 정책을 반환
     */
    fun getPolicyForType(repaymentType: RepaymentType): RepaymentAmountPolicy {
        return when (repaymentType) {
            RepaymentType.DIVIDED_BY_PERIOD -> dividedByPeriodPolicy
            RepaymentType.FIXED_MONTHLY -> fixedMonthlyPolicy
            RepaymentType.FLEXIBLE -> flexiblePolicy
        }
    }
}
