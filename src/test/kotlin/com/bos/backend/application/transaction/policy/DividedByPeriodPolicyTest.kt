package com.bos.backend.application.transaction.policy

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.LocalDate

class DividedByPeriodPolicyTest : StringSpec({
    val policy = DividedByPeriodPolicy()

    "10,000원을 3개월로 나누면 3,300, 3,300, 3,400이 된다" {
        val params =
            ScheduleCalculationParams(
                startDate = LocalDate.of(2025, 1, 1),
                remainingAmount = BigDecimal("10000"),
                paymentDay = 15,
                targetDate = LocalDate.of(2025, 4, 1),
            )

        val schedules = policy.calculateSchedule(params)

        schedules.size shouldBe 3
        schedules[0].scheduledAmount shouldBe BigDecimal("3300")
        schedules[1].scheduledAmount shouldBe BigDecimal("3300")
        schedules[2].scheduledAmount shouldBe BigDecimal("3400")

        // 합계 검증
        val total = schedules.sumOf { it.scheduledAmount }
        total shouldBe BigDecimal("10000")
    }

    "300,000원을 3개월로 나누면 100,000씩 딱 떨어진다" {
        val params =
            ScheduleCalculationParams(
                startDate = LocalDate.of(2025, 1, 1),
                remainingAmount = BigDecimal("300000"),
                paymentDay = 15,
                targetDate = LocalDate.of(2025, 4, 1),
            )

        val schedules = policy.calculateSchedule(params)

        schedules.size shouldBe 3
        schedules[0].scheduledAmount shouldBe BigDecimal("100000")
        schedules[1].scheduledAmount shouldBe BigDecimal("100000")
        schedules[2].scheduledAmount shouldBe BigDecimal("100000")
    }

    "100,000원을 7개월로 나누면 14,200 × 6회, 마지막 14,800" {
        val params =
            ScheduleCalculationParams(
                startDate = LocalDate.of(2025, 1, 1),
                remainingAmount = BigDecimal("100000"),
                paymentDay = 15,
                targetDate = LocalDate.of(2025, 8, 1),
            )

        val schedules = policy.calculateSchedule(params)

        schedules.size shouldBe 7
        schedules[0].scheduledAmount shouldBe BigDecimal("14200")
        schedules[1].scheduledAmount shouldBe BigDecimal("14200")
        schedules[2].scheduledAmount shouldBe BigDecimal("14200")
        schedules[3].scheduledAmount shouldBe BigDecimal("14200")
        schedules[4].scheduledAmount shouldBe BigDecimal("14200")
        schedules[5].scheduledAmount shouldBe BigDecimal("14200")
        schedules[6].scheduledAmount shouldBe BigDecimal("14800")

        // 합계 검증
        val total = schedules.sumOf { it.scheduledAmount }
        total shouldBe BigDecimal("100000")
    }

    "1,500원을 4개월로 나누면 300, 300, 300, 600" {
        val params =
            ScheduleCalculationParams(
                startDate = LocalDate.of(2025, 1, 1),
                remainingAmount = BigDecimal("1500"),
                paymentDay = 15,
                targetDate = LocalDate.of(2025, 5, 1),
            )

        val schedules = policy.calculateSchedule(params)

        schedules.size shouldBe 4
        schedules[0].scheduledAmount shouldBe BigDecimal("300")
        schedules[1].scheduledAmount shouldBe BigDecimal("300")
        schedules[2].scheduledAmount shouldBe BigDecimal("300")
        schedules[3].scheduledAmount shouldBe BigDecimal("600")

        // 합계 검증
        val total = schedules.sumOf { it.scheduledAmount }
        total shouldBe BigDecimal("1500")
    }

    "250원을 3개월로 나누면 0, 0, 250 (100원 미만은 전부 마지막에)" {
        val params =
            ScheduleCalculationParams(
                startDate = LocalDate.of(2025, 1, 1),
                remainingAmount = BigDecimal("250"),
                paymentDay = 15,
                targetDate = LocalDate.of(2025, 4, 1),
            )

        val schedules = policy.calculateSchedule(params)

        schedules.size shouldBe 3
        schedules[0].scheduledAmount shouldBe BigDecimal("0")
        schedules[1].scheduledAmount shouldBe BigDecimal("0")
        schedules[2].scheduledAmount shouldBe BigDecimal("250")

        // 합계 검증
        val total = schedules.sumOf { it.scheduledAmount }
        total shouldBe BigDecimal("250")
    }

    "50,000원을 1개월로 나누면 50,000원 한 번에" {
        val params =
            ScheduleCalculationParams(
                startDate = LocalDate.of(2025, 1, 1),
                remainingAmount = BigDecimal("50000"),
                paymentDay = 15,
                targetDate = LocalDate.of(2025, 2, 1),
            )

        val schedules = policy.calculateSchedule(params)

        schedules.size shouldBe 1
        schedules[0].scheduledAmount shouldBe BigDecimal("50000")
    }

    "월 납부액 계산 - 10,000원을 3개월로 나누면 월 3,300원" {
        val params =
            ScheduleCalculationParams(
                startDate = LocalDate.of(2025, 1, 1),
                remainingAmount = BigDecimal("10000"),
                paymentDay = 15,
                targetDate = LocalDate.of(2025, 4, 1),
            )

        val monthlyAmount = policy.calculateMonthlyAmount(params)

        monthlyAmount shouldBe BigDecimal("3300")
    }

    "납부일이 월말인 경우 (2월 31일 → 2월 28일)" {
        val params =
            ScheduleCalculationParams(
                startDate = LocalDate.of(2025, 1, 1),
                remainingAmount = BigDecimal("30000"),
                // 2월에는 31일이 없음
                paymentDay = 31,
                targetDate = LocalDate.of(2025, 3, 31),
            )

        val schedules = policy.calculateSchedule(params)

        schedules.size shouldBe 3
        schedules[0].scheduledDate shouldBe LocalDate.of(2025, 1, 31)
        schedules[1].scheduledDate shouldBe LocalDate.of(2025, 2, 28) // 2월은 28일까지
        schedules[2].scheduledDate shouldBe LocalDate.of(2025, 3, 31)
    }

    "큰 금액 - 1,000,000원을 12개월로 나누면 83,300 × 11회, 마지막 83,700" {
        val params =
            ScheduleCalculationParams(
                startDate = LocalDate.of(2025, 1, 1),
                remainingAmount = BigDecimal("1000000"),
                paymentDay = 15,
                targetDate = LocalDate.of(2026, 1, 1),
            )

        val schedules = policy.calculateSchedule(params)

        schedules.size shouldBe 12
        // 처음 11회
        for (i in 0..10) {
            schedules[i].scheduledAmount shouldBe BigDecimal("83300")
        }
        // 마지막 회
        schedules[11].scheduledAmount shouldBe BigDecimal("83700")

        // 합계 검증
        val total = schedules.sumOf { it.scheduledAmount }
        total shouldBe BigDecimal("1000000")
    }

    "소액 - 500원을 2개월로 나누면 200, 300" {
        val params =
            ScheduleCalculationParams(
                startDate = LocalDate.of(2025, 1, 1),
                remainingAmount = BigDecimal("500"),
                paymentDay = 15,
                targetDate = LocalDate.of(2025, 3, 1),
            )

        val schedules = policy.calculateSchedule(params)

        schedules.size shouldBe 2
        schedules[0].scheduledAmount shouldBe BigDecimal("200")
        schedules[1].scheduledAmount shouldBe BigDecimal("300")

        // 합계 검증
        val total = schedules.sumOf { it.scheduledAmount }
        total shouldBe BigDecimal("500")
    }
})
