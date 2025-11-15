package com.bos.backend.application.transaction.policy

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.LocalDate

class FixedMonthlyPolicyTest : StringSpec({
    val policy = FixedMonthlyPolicy()

    "500,000원을 80,000원씩 갚으면 6회 80,000원, 마지막 20,000원" {
        val params =
            ScheduleCalculationParams(
                startDate = LocalDate.of(2025, 1, 1),
                remainingAmount = BigDecimal("500000"),
                paymentDay = 15,
                monthlyAmount = BigDecimal("80000"),
            )

        val schedules = policy.calculateSchedule(params)

        schedules.size shouldBe 7
        for (i in 0..5) {
            schedules[i].scheduledAmount shouldBe BigDecimal("80000")
        }
        schedules[6].scheduledAmount shouldBe BigDecimal("20000")

        // 합계 검증
        val total = schedules.sumOf { it.scheduledAmount }
        total shouldBe BigDecimal("500000")
    }

    "300,000원을 100,000원씩 갚으면 딱 3회" {
        val params =
            ScheduleCalculationParams(
                startDate = LocalDate.of(2025, 1, 1),
                remainingAmount = BigDecimal("300000"),
                paymentDay = 15,
                monthlyAmount = BigDecimal("100000"),
            )

        val schedules = policy.calculateSchedule(params)

        schedules.size shouldBe 3
        schedules[0].scheduledAmount shouldBe BigDecimal("100000")
        schedules[1].scheduledAmount shouldBe BigDecimal("100000")
        schedules[2].scheduledAmount shouldBe BigDecimal("100000")
    }

    "235,000원을 67,000원씩 갚으면 3회 67,000원, 마지막 34,000원" {
        val params =
            ScheduleCalculationParams(
                startDate = LocalDate.of(2025, 1, 1),
                remainingAmount = BigDecimal("235000"),
                paymentDay = 15,
                monthlyAmount = BigDecimal("67000"),
            )

        val schedules = policy.calculateSchedule(params)

        schedules.size shouldBe 4
        schedules[0].scheduledAmount shouldBe BigDecimal("67000")
        schedules[1].scheduledAmount shouldBe BigDecimal("67000")
        schedules[2].scheduledAmount shouldBe BigDecimal("67000")
        schedules[3].scheduledAmount shouldBe BigDecimal("34000")

        // 합계 검증
        val total = schedules.sumOf { it.scheduledAmount }
        total shouldBe BigDecimal("235000")
    }

    "50,000원을 100,000원씩 갚으려 하면 1회에 50,000원만" {
        val params =
            ScheduleCalculationParams(
                startDate = LocalDate.of(2025, 1, 1),
                remainingAmount = BigDecimal("50000"),
                paymentDay = 15,
                monthlyAmount = BigDecimal("100000"),
            )

        val schedules = policy.calculateSchedule(params)

        schedules.size shouldBe 1
        schedules[0].scheduledAmount shouldBe BigDecimal("50000")
    }

    "1,000,000원을 90,000원씩 갚으면 11회 90,000원, 마지막 10,000원" {
        val params =
            ScheduleCalculationParams(
                startDate = LocalDate.of(2025, 1, 1),
                remainingAmount = BigDecimal("1000000"),
                paymentDay = 15,
                monthlyAmount = BigDecimal("90000"),
            )

        val schedules = policy.calculateSchedule(params)

        schedules.size shouldBe 12
        for (i in 0..10) {
            schedules[i].scheduledAmount shouldBe BigDecimal("90000")
        }
        schedules[11].scheduledAmount shouldBe BigDecimal("10000")

        // 합계 검증
        val total = schedules.sumOf { it.scheduledAmount }
        total shouldBe BigDecimal("1000000")
    }

    "완료일 계산 - 500,000원을 80,000원씩 갚으면 7개월 소요" {
        val params =
            ScheduleCalculationParams(
                startDate = LocalDate.of(2025, 1, 1),
                remainingAmount = BigDecimal("500000"),
                paymentDay = 15,
                monthlyAmount = BigDecimal("80000"),
            )

        val completionInfo = policy.calculateCompletionDate(params)

        completionInfo?.monthsLater shouldBe 7
        completionInfo?.completionDate shouldBe LocalDate.of(2025, 7, 15)
    }

    "완료일 계산 - 300,000원을 100,000원씩 갚으면 3개월 소요" {
        val params =
            ScheduleCalculationParams(
                startDate = LocalDate.of(2025, 1, 1),
                remainingAmount = BigDecimal("300000"),
                paymentDay = 15,
                monthlyAmount = BigDecimal("100000"),
            )

        val completionInfo = policy.calculateCompletionDate(params)

        completionInfo?.monthsLater shouldBe 3
        completionInfo?.completionDate shouldBe LocalDate.of(2025, 3, 15)
    }

    "월납부액 반환 - 고정 월납은 입력값 그대로" {
        val params =
            ScheduleCalculationParams(
                startDate = LocalDate.of(2025, 1, 1),
                remainingAmount = BigDecimal("500000"),
                paymentDay = 15,
                monthlyAmount = BigDecimal("80000"),
            )

        val monthlyAmount = policy.calculateMonthlyAmount(params)

        monthlyAmount shouldBe BigDecimal("80000")
    }

    "소액 완료 - 5,000원을 3,000원씩 갚으면 2회" {
        val params =
            ScheduleCalculationParams(
                startDate = LocalDate.of(2025, 1, 1),
                remainingAmount = BigDecimal("5000"),
                paymentDay = 15,
                monthlyAmount = BigDecimal("3000"),
            )

        val schedules = policy.calculateSchedule(params)

        schedules.size shouldBe 2
        schedules[0].scheduledAmount shouldBe BigDecimal("3000")
        schedules[1].scheduledAmount shouldBe BigDecimal("2000")

        // 합계 검증
        val total = schedules.sumOf { it.scheduledAmount }
        total shouldBe BigDecimal("5000")
    }
})
