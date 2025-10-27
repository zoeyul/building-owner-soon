package com.bos.backend.application.transaction

import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * 상환 스케줄 계산 정책
 *
 * 거래내역 생성 시와 계산 API에서 동일한 로직을 사용하도록 통합
 */
@Component
class RepaymentScheduleCalculator {
    companion object {
        private const val MONTHS_PER_YEAR = 12
        private const val DECIMAL_SCALE = 2
    }

    /**
     * DIVIDED_BY_PERIOD: 목표일까지 균등 분할
     * 시작일부터 목표일까지의 납부 날짜를 계산하고, 금액을 균등 분할
     */
    fun calculateDividedByPeriodSchedule(
        startDate: LocalDate,
        targetDate: LocalDate,
        paymentDay: Int,
        remainingAmount: BigDecimal,
    ): List<PaymentSchedule> {
        val schedules = mutableListOf<PaymentSchedule>()
        var currentDate = calculateNextPaymentDate(startDate, paymentDay)

        // 납부 날짜 리스트 생성
        val paymentDates = mutableListOf<LocalDate>()
        while (currentDate.isBefore(targetDate) || currentDate.isEqual(targetDate)) {
            paymentDates.add(currentDate)
            currentDate = calculateNextPaymentDate(currentDate, paymentDay)
        }

        if (paymentDates.isEmpty()) {
            return emptyList()
        }

        // 균등 분할 금액 계산
        val amountPerPeriod = remainingAmount.divide(BigDecimal(paymentDates.size), DECIMAL_SCALE, RoundingMode.HALF_UP)

        paymentDates.forEachIndexed { index, paymentDate ->
            val amount =
                if (index == paymentDates.size - 1) {
                    // 마지막 납부는 나머지 금액 (반올림 오차 보정)
                    remainingAmount - amountPerPeriod.multiply(BigDecimal(paymentDates.size - 1))
                } else {
                    amountPerPeriod
                }

            schedules.add(PaymentSchedule(paymentDate, amount))
        }

        return schedules
    }

    /**
     * FIXED_MONTHLY: 고정 월납
     * 월 납부액이 정해져 있고, 잔액이 0이 될 때까지 반복
     */
    fun calculateFixedMonthlySchedule(
        startDate: LocalDate,
        paymentDay: Int,
        monthlyAmount: BigDecimal,
        remainingAmount: BigDecimal,
    ): List<PaymentSchedule> {
        val schedules = mutableListOf<PaymentSchedule>()
        var currentDate = calculateNextPaymentDate(startDate, paymentDay)
        var remaining = remainingAmount

        while (remaining > BigDecimal.ZERO) {
            val paymentAmount = if (remaining < monthlyAmount) remaining else monthlyAmount

            schedules.add(PaymentSchedule(currentDate, paymentAmount))

            remaining -= paymentAmount
            currentDate = calculateNextPaymentDate(currentDate, paymentDay)
        }

        return schedules
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
        val targetMonth =
            if (baseDate.dayOfMonth >= paymentDay) {
                baseDate.plusMonths(1)
            } else {
                baseDate
            }

        val lastDayOfMonth = targetMonth.lengthOfMonth()
        val adjustedPaymentDay = if (paymentDay > lastDayOfMonth) lastDayOfMonth else paymentDay

        return targetMonth.withDayOfMonth(adjustedPaymentDay)
    }

    /**
     * DIVIDED_BY_PERIOD: 월 납부액으로 완료일 계산
     */
    fun calculateCompletionDate(
        startDate: LocalDate,
        paymentDay: Int,
        monthlyAmount: BigDecimal,
        remainingAmount: BigDecimal,
    ): CompletionDateInfo {
        val schedules = calculateFixedMonthlySchedule(startDate, paymentDay, monthlyAmount, remainingAmount)

        if (schedules.isEmpty()) {
            return CompletionDateInfo(startDate, 0)
        }

        val completionDate = schedules.last().scheduledDate
        val period = java.time.Period.between(startDate, completionDate)
        val totalMonths = period.years * MONTHS_PER_YEAR + period.months

        return CompletionDateInfo(completionDate, totalMonths)
    }

    /**
     * FIXED_MONTHLY: 목표일까지의 월 납부액 계산
     */
    fun calculateMonthlyAmount(
        startDate: LocalDate,
        targetDate: LocalDate,
        paymentDay: Int,
        remainingAmount: BigDecimal,
    ): BigDecimal {
        // 납부 날짜 리스트 생성
        var currentDate = calculateNextPaymentDate(startDate, paymentDay)
        val paymentDates = mutableListOf<LocalDate>()

        while (currentDate.isBefore(targetDate) || currentDate.isEqual(targetDate)) {
            paymentDates.add(currentDate)
            currentDate = calculateNextPaymentDate(currentDate, paymentDay)
        }

        if (paymentDates.isEmpty()) {
            return BigDecimal.ZERO
        }

        return remainingAmount.divide(BigDecimal(paymentDates.size), DECIMAL_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * 납부 스케줄 정보
     */
    data class PaymentSchedule(
        val scheduledDate: LocalDate,
        val scheduledAmount: BigDecimal,
    )

    /**
     * 완료일 정보
     */
    data class CompletionDateInfo(
        val completionDate: LocalDate,
        val monthsLater: Int,
    )
}
