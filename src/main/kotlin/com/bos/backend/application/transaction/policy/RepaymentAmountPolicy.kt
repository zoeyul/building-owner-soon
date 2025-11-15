package com.bos.backend.application.transaction.policy

import java.math.BigDecimal
import java.time.LocalDate

/**
 * 금액 계산 관련 상수
 */
private const val ROUNDING_UNIT = 100 // 100원 단위 절삭을 위한 상수

/**
 * 상환 금액 계산 정책 인터페이스
 *
 * Strategy Pattern을 적용하여 각 상환 타입별 금액 계산 로직을 분리
 */
interface RepaymentAmountPolicy {
    /**
     * 상환 스케줄 계산
     *
     * @param params 상환 스케줄 계산에 필요한 파라미터
     * @return 계산된 상환 스케줄 리스트
     */
    fun calculateSchedule(params: ScheduleCalculationParams): List<PaymentSchedule>

    /**
     * 월 상환 금액 계산 (미리보기용)
     *
     * @param params 계산 파라미터
     * @return 월 상환 금액
     */
    fun calculateMonthlyAmount(params: ScheduleCalculationParams): BigDecimal

    /**
     * 완료 예정일 계산 (미리보기용)
     *
     * @param params 계산 파라미터
     * @return 완료일 정보 (완료일, 개월 수)
     */
    fun calculateCompletionDate(params: ScheduleCalculationParams): CompletionDateInfo?
}

/**
 * 스케줄 계산 파라미터
 */
data class ScheduleCalculationParams(
    val startDate: LocalDate,
    val remainingAmount: BigDecimal,
    val paymentDay: Int,
    val targetDate: LocalDate? = null,
    val monthlyAmount: BigDecimal? = null,
)

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

/**
 * 다음 납부 날짜 계산 유틸리티
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
 * 100원 단위로 절삭 (내림)
 *
 * 예: 3,333원 -> 3,300원
 */
fun roundDownToHundred(amount: BigDecimal): BigDecimal {
    return BigDecimal(
        amount.divide(BigDecimal(ROUNDING_UNIT)).toBigInteger()
            .multiply(java.math.BigInteger.valueOf(ROUNDING_UNIT.toLong())),
    )
}
