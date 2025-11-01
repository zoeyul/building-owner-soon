package com.bos.backend.application.transaction

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.LocalDate

class RepaymentScheduleCalculatorTest :
    DescribeSpec({
        val calculator = RepaymentScheduleCalculator()

        describe("calculateMonthlyAmount - DIVIDED_BY_PERIOD 정책") {
            context("완료 예정일이 주어지면") {
                it("남은 금액을 남은 개월 수로 나눈 월 납부액을 계산한다") {
                    // given: 100만원을 5개월에 걸쳐 상환 (매월 15일 납부)
                    val startDate = LocalDate.of(2025, 1, 1)
                    val targetDate = LocalDate.of(2025, 6, 15)
                    val paymentDay = 15
                    val remainingAmount = BigDecimal("1000000")

                    // when
                    val monthlyAmount =
                        calculator.calculateMonthlyAmount(
                            startDate = startDate,
                            targetDate = targetDate,
                            paymentDay = paymentDay,
                            remainingAmount = remainingAmount,
                        )

                    // then: 1월 15일, 2월 15일, 3월 15일, 4월 15일, 5월 15일, 6월 15일 = 6회
                    monthlyAmount shouldBe BigDecimal("166666.67")
                }

                it("예: 100만원, 5개월 → 월 20만원을 계산한다") {
                    // given
                    val startDate = LocalDate.of(2025, 1, 1)
                    val targetDate = LocalDate.of(2025, 5, 15)
                    val paymentDay = 15
                    val remainingAmount = BigDecimal("1000000")

                    // when
                    val monthlyAmount =
                        calculator.calculateMonthlyAmount(
                            startDate = startDate,
                            targetDate = targetDate,
                            paymentDay = paymentDay,
                            remainingAmount = remainingAmount,
                        )

                    // then: 1월 15일, 2월 15일, 3월 15일, 4월 15일, 5월 15일 = 5회
                    monthlyAmount shouldBe BigDecimal("200000.00")
                }

                it("납부 기간이 없으면 0원을 반환한다") {
                    // given: 완료일이 시작일보다 앞선 경우
                    val startDate = LocalDate.of(2025, 1, 20)
                    val targetDate = LocalDate.of(2025, 1, 15)
                    val paymentDay = 15
                    val remainingAmount = BigDecimal("1000000")

                    // when
                    val monthlyAmount =
                        calculator.calculateMonthlyAmount(
                            startDate = startDate,
                            targetDate = targetDate,
                            paymentDay = paymentDay,
                            remainingAmount = remainingAmount,
                        )

                    // then
                    monthlyAmount shouldBe BigDecimal.ZERO
                }
            }

            context("반올림 처리") {
                it("소수점 둘째 자리에서 반올림한다") {
                    // given
                    val startDate = LocalDate.of(2025, 1, 1)
                    val targetDate = LocalDate.of(2025, 3, 15)
                    val paymentDay = 15
                    val remainingAmount = BigDecimal("100000")

                    // when: 100000 ÷ 3 = 33333.333...
                    val monthlyAmount =
                        calculator.calculateMonthlyAmount(
                            startDate = startDate,
                            targetDate = targetDate,
                            paymentDay = paymentDay,
                            remainingAmount = remainingAmount,
                        )

                    // then: 반올림하여 33333.33
                    monthlyAmount shouldBe BigDecimal("33333.33")
                }
            }
        }

        describe("calculateCompletionDate - FIXED_MONTHLY 정책") {
            context("월 납부액이 주어지면") {
                it("남은 개월 수를 올림 처리하여 완료일을 계산한다") {
                    // given: 100만원, 월 23만원 → ceil(100 ÷ 23) = 5개월
                    val startDate = LocalDate.of(2025, 1, 1)
                    val paymentDay = 15
                    val monthlyAmount = BigDecimal("230000")
                    val remainingAmount = BigDecimal("1000000")

                    // when
                    val result =
                        calculator.calculateCompletionDate(
                            startDate = startDate,
                            paymentDay = paymentDay,
                            monthlyAmount = monthlyAmount,
                            remainingAmount = remainingAmount,
                        )

                    // then: 1월 15일 → 2월 15일 → 3월 15일 → 4월 15일 → 5월 15일
                    result.completionDate shouldBe LocalDate.of(2025, 5, 15)
                    result.monthsLater shouldBe 5
                }

                it("예: 100만원, 월 25만원 → 4개월 후 완료") {
                    // given: ceil(100 ÷ 25) = 4개월
                    val startDate = LocalDate.of(2025, 1, 1)
                    val paymentDay = 10
                    val monthlyAmount = BigDecimal("250000")
                    val remainingAmount = BigDecimal("1000000")

                    // when
                    val result =
                        calculator.calculateCompletionDate(
                            startDate = startDate,
                            paymentDay = paymentDay,
                            monthlyAmount = monthlyAmount,
                            remainingAmount = remainingAmount,
                        )

                    // then
                    result.completionDate shouldBe LocalDate.of(2025, 4, 10)
                    result.monthsLater shouldBe 4
                }

                it("올림 처리: 4.2개월 → 5개월") {
                    // given: 100만원, 월 24만원 → ceil(100 ÷ 24) = ceil(4.166...) = 5개월
                    val startDate = LocalDate.of(2025, 1, 1)
                    val paymentDay = 20
                    val monthlyAmount = BigDecimal("240000")
                    val remainingAmount = BigDecimal("1000000")

                    // when
                    val result =
                        calculator.calculateCompletionDate(
                            startDate = startDate,
                            paymentDay = paymentDay,
                            monthlyAmount = monthlyAmount,
                            remainingAmount = remainingAmount,
                        )

                    // then
                    result.monthsLater shouldBe 5
                }

                it("월 납부액이 남은 금액보다 크면 1개월") {
                    // given
                    val startDate = LocalDate.of(2025, 1, 1)
                    val paymentDay = 5
                    val monthlyAmount = BigDecimal("1500000")
                    val remainingAmount = BigDecimal("1000000")

                    // when
                    val result =
                        calculator.calculateCompletionDate(
                            startDate = startDate,
                            paymentDay = paymentDay,
                            monthlyAmount = monthlyAmount,
                            remainingAmount = remainingAmount,
                        )

                    // then
                    result.completionDate shouldBe LocalDate.of(2025, 1, 5)
                    result.monthsLater shouldBe 1
                }
            }
        }

        describe("calculateNextPaymentDate - 납부일 계산 정책") {
            context("기본 동작") {
                it("baseDate의 일자가 paymentDay보다 작으면 이번 달 paymentDay를 반환한다") {
                    // given
                    val baseDate = LocalDate.of(2025, 1, 10)
                    val paymentDay = 15

                    // when
                    val nextDate = calculator.calculateNextPaymentDate(baseDate, paymentDay)

                    // then
                    nextDate shouldBe LocalDate.of(2025, 1, 15)
                }

                it("baseDate의 일자가 paymentDay보다 크거나 같으면 다음 달 paymentDay를 반환한다") {
                    // given
                    val baseDate = LocalDate.of(2025, 1, 15)
                    val paymentDay = 15

                    // when
                    val nextDate = calculator.calculateNextPaymentDate(baseDate, paymentDay)

                    // then
                    nextDate shouldBe LocalDate.of(2025, 2, 15)
                }
            }

            context("월말 조정 정책: 29~31일 입력 시") {
                it("2월에 31일이 없으면 2월 28일로 조정한다 (평년)") {
                    // given: 1월 31일 이후, 다음 납부일은 2월
                    val baseDate = LocalDate.of(2025, 1, 31)
                    val paymentDay = 31

                    // when
                    val nextDate = calculator.calculateNextPaymentDate(baseDate, paymentDay)

                    // then: 2025년은 평년
                    nextDate shouldBe LocalDate.of(2025, 2, 28)
                }

                it("2월에 31일이 없으면 2월 29일로 조정한다 (윤년)") {
                    // given: 1월 31일 이후, 다음 납부일은 2월
                    val baseDate = LocalDate.of(2024, 1, 31)
                    val paymentDay = 31

                    // when
                    val nextDate = calculator.calculateNextPaymentDate(baseDate, paymentDay)

                    // then: 2024년은 윤년
                    nextDate shouldBe LocalDate.of(2024, 2, 29)
                }

                it("4월에 31일이 없으면 4월 30일로 조정한다") {
                    // given: 3월 31일 이후, 다음 납부일은 4월
                    val baseDate = LocalDate.of(2025, 3, 31)
                    val paymentDay = 31

                    // when
                    val nextDate = calculator.calculateNextPaymentDate(baseDate, paymentDay)

                    // then
                    nextDate shouldBe LocalDate.of(2025, 4, 30)
                }

                it("정상적인 달에는 31일을 그대로 사용한다") {
                    // given: 12월 31일 이후, 다음 납부일은 1월 (1월은 31일까지 있음)
                    val baseDate = LocalDate.of(2024, 12, 31)
                    val paymentDay = 31

                    // when
                    val nextDate = calculator.calculateNextPaymentDate(baseDate, paymentDay)

                    // then: 1월은 31일까지 있음
                    nextDate shouldBe LocalDate.of(2025, 1, 31)
                }
            }
        }

        describe("calculateDividedByPeriodSchedule - 균등 분할 스케줄") {
            context("목표일까지 균등 분할하여 스케줄을 생성한다") {
                it("남은 금액을 납부 횟수로 균등 분할한다") {
                    // given
                    val startDate = LocalDate.of(2025, 1, 1)
                    val targetDate = LocalDate.of(2025, 3, 15)
                    val paymentDay = 15
                    val remainingAmount = BigDecimal("300000")

                    // when
                    val schedules =
                        calculator.calculateDividedByPeriodSchedule(
                            startDate = startDate,
                            targetDate = targetDate,
                            paymentDay = paymentDay,
                            remainingAmount = remainingAmount,
                        )

                    // then: 1/15, 2/15, 3/15 = 3회
                    schedules.size shouldBe 3
                    schedules[0].scheduledAmount shouldBe BigDecimal("100000.00")
                    schedules[1].scheduledAmount shouldBe BigDecimal("100000.00")
                    schedules[2].scheduledAmount shouldBe BigDecimal("100000.00")
                }

                it("마지막 납부는 반올림 오차를 보정한다") {
                    // given
                    val startDate = LocalDate.of(2025, 1, 1)
                    val targetDate = LocalDate.of(2025, 3, 15)
                    val paymentDay = 15
                    val remainingAmount = BigDecimal("100000")

                    // when
                    val schedules =
                        calculator.calculateDividedByPeriodSchedule(
                            startDate = startDate,
                            targetDate = targetDate,
                            paymentDay = paymentDay,
                            remainingAmount = remainingAmount,
                        )

                    // then: 33333.33 + 33333.33 + 33333.34 = 100000
                    schedules.size shouldBe 3
                    schedules[0].scheduledAmount shouldBe BigDecimal("33333.33")
                    schedules[1].scheduledAmount shouldBe BigDecimal("33333.33")
                    schedules[2].scheduledAmount shouldBe BigDecimal("33333.34")
                }
            }
        }

        describe("calculateFixedMonthlySchedule - 고정 월납 스케줄") {
            context("월 납부액이 정해져 있고, 잔액이 0이 될 때까지 반복한다") {
                it("매월 동일한 금액을 납부한다") {
                    // given
                    val startDate = LocalDate.of(2025, 1, 1)
                    val paymentDay = 10
                    val monthlyAmount = BigDecimal("100000")
                    val remainingAmount = BigDecimal("300000")

                    // when
                    val schedules =
                        calculator.calculateFixedMonthlySchedule(
                            startDate = startDate,
                            paymentDay = paymentDay,
                            monthlyAmount = monthlyAmount,
                            remainingAmount = remainingAmount,
                        )

                    // then
                    schedules.size shouldBe 3
                    schedules.forEach { it.scheduledAmount shouldBe BigDecimal("100000") }
                }

                it("마지막 회차는 남은 금액만 납부한다") {
                    // given
                    val startDate = LocalDate.of(2025, 1, 1)
                    val paymentDay = 20
                    val monthlyAmount = BigDecimal("100000")
                    val remainingAmount = BigDecimal("250000")

                    // when
                    val schedules =
                        calculator.calculateFixedMonthlySchedule(
                            startDate = startDate,
                            paymentDay = paymentDay,
                            monthlyAmount = monthlyAmount,
                            remainingAmount = remainingAmount,
                        )

                    // then: 10만원 + 10만원 + 5만원
                    schedules.size shouldBe 3
                    schedules[0].scheduledAmount shouldBe BigDecimal("100000")
                    schedules[1].scheduledAmount shouldBe BigDecimal("100000")
                    schedules[2].scheduledAmount shouldBe BigDecimal("50000")
                }
            }
        }
    })
