package com.bos.backend.application.transaction.policy

import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * 자유 상환 정책 (FLEXIBLE)
 *
 * 특징:
 * - 미리 정해진 스케줄 없이 자유롭게 상환
 * - 스케줄을 생성하지 않음
 * - 상환 시점과 금액을 사용자가 직접 결정
 *
 * 사용 사례:
 * - 정해진 기한이나 금액 없이 빌린 돈
 * - 여유가 생길 때마다 갚는 방식
 */
@Component
class FlexiblePolicy : RepaymentAmountPolicy {
    override fun calculateSchedule(params: ScheduleCalculationParams): List<PaymentSchedule> {
        // 자유 상환은 스케줄을 생성하지 않음
        return emptyList()
    }

    override fun calculateMonthlyAmount(params: ScheduleCalculationParams): BigDecimal {
        // 자유 상환은 월 납부액이 없음
        return BigDecimal.ZERO
    }

    override fun calculateCompletionDate(params: ScheduleCalculationParams): CompletionDateInfo? {
        // 자유 상환은 완료일을 예측할 수 없음
        return null
    }
}
