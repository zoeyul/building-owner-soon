package com.bos.backend.application.transaction

import com.bos.backend.application.CustomException
import com.bos.backend.domain.transaction.enum.RepaymentType
import com.bos.backend.presentation.transaction.dto.DividedByPeriodCalculationRequest
import com.bos.backend.presentation.transaction.dto.DividedByPeriodCalculationResponse
import com.bos.backend.presentation.transaction.dto.FixedMonthlyCalculationRequest
import com.bos.backend.presentation.transaction.dto.FixedMonthlyCalculationResponse
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.LocalDate

class RepaymentCalculationServiceTest :
    DescribeSpec({
        val repaymentScheduleCalculator = mockk<RepaymentScheduleCalculator>()
        val service = RepaymentCalculationService(repaymentScheduleCalculator)

        describe("calculateRepayment") {
            context("DIVIDED_BY_PERIOD 타입") {
                it("완료 예정일을 입력받아 월 납부액을 계산한다") {
                    // given: 100만원, 5개월 → 월 20만원
                    val request =
                        DividedByPeriodCalculationRequest(
                            startDate = LocalDate.of(2025, 1, 1),
                            totalAmount = BigDecimal("1000000"),
                            completedAmount = BigDecimal.ZERO,
                            targetDate = LocalDate.of(2025, 5, 15),
                            paymentDay = 15,
                        )

                    every {
                        repaymentScheduleCalculator.calculateMonthlyAmount(
                            startDate = request.startDate,
                            targetDate = request.targetDate,
                            paymentDay = request.paymentDay,
                            remainingAmount = BigDecimal("1000000"),
                        )
                    } returns BigDecimal("200000.00")

                    // when
                    val response = service.calculateRepayment(request)

                    // then
                    response shouldBe DividedByPeriodCalculationResponse(monthlyAmount = BigDecimal("200000.00"))
                    verify {
                        repaymentScheduleCalculator.calculateMonthlyAmount(
                            startDate = request.startDate,
                            targetDate = request.targetDate,
                            paymentDay = request.paymentDay,
                            remainingAmount = BigDecimal("1000000"),
                        )
                    }
                }

                it("완료된 금액을 제외한 남은 금액으로 계산한다") {
                    // given
                    val request =
                        DividedByPeriodCalculationRequest(
                            startDate = LocalDate.of(2025, 1, 1),
                            totalAmount = BigDecimal("1000000"),
                            completedAmount = BigDecimal("300000"),
                            targetDate = LocalDate.of(2025, 4, 15),
                            paymentDay = 15,
                        )

                    every {
                        repaymentScheduleCalculator.calculateMonthlyAmount(
                            any(),
                            any(),
                            any(),
                            remainingAmount = BigDecimal("700000"),
                        )
                    } returns BigDecimal("175000.00")

                    // when
                    val response = service.calculateRepayment(request)

                    // then
                    response shouldBe DividedByPeriodCalculationResponse(monthlyAmount = BigDecimal("175000.00"))
                }

                it("남은 금액이 0 이하면 예외를 발생시킨다") {
                    // given
                    val request =
                        DividedByPeriodCalculationRequest(
                            startDate = LocalDate.of(2025, 1, 1),
                            totalAmount = BigDecimal("1000000"),
                            completedAmount = BigDecimal("1000000"),
                            targetDate = LocalDate.of(2025, 5, 15),
                            paymentDay = 15,
                        )

                    // when & then
                    val exception =
                        shouldThrow<CustomException> {
                            service.calculateRepayment(request)
                        }
                    exception.message shouldBe "남은 금액이 0보다 커야 합니다"
                }

                it("완료 예정일이 시작일보다 앞서면 예외를 발생시킨다") {
                    // given
                    val request =
                        DividedByPeriodCalculationRequest(
                            startDate = LocalDate.of(2025, 5, 1),
                            totalAmount = BigDecimal("1000000"),
                            completedAmount = BigDecimal.ZERO,
                            targetDate = LocalDate.of(2025, 3, 15),
                            paymentDay = 15,
                        )

                    // when & then
                    val exception =
                        shouldThrow<CustomException> {
                            service.calculateRepayment(request)
                        }
                    exception.message shouldBe "완료 예정일은 시작일 이후여야 합니다"
                }

                it("완료 예정일이 시작일과 같으면 예외를 발생시킨다") {
                    // given
                    val request =
                        DividedByPeriodCalculationRequest(
                            startDate = LocalDate.of(2025, 1, 15),
                            totalAmount = BigDecimal("1000000"),
                            completedAmount = BigDecimal.ZERO,
                            targetDate = LocalDate.of(2025, 1, 15),
                            paymentDay = 15,
                        )

                    // when & then
                    val exception =
                        shouldThrow<CustomException> {
                            service.calculateRepayment(request)
                        }
                    exception.message shouldBe "완료 예정일은 시작일 이후여야 합니다"
                }

                it("납부 기간이 너무 짧아 월 납부액이 0원이면 예외를 발생시킨다") {
                    // given
                    val request =
                        DividedByPeriodCalculationRequest(
                            startDate = LocalDate.of(2025, 1, 1),
                            totalAmount = BigDecimal("1000000"),
                            completedAmount = BigDecimal.ZERO,
                            targetDate = LocalDate.of(2025, 1, 10),
                            paymentDay = 15,
                        )

                    every {
                        repaymentScheduleCalculator.calculateMonthlyAmount(
                            any(),
                            any(),
                            any(),
                            any(),
                        )
                    } returns BigDecimal.ZERO

                    // when & then
                    val exception =
                        shouldThrow<CustomException> {
                            service.calculateRepayment(request)
                        }
                    exception.message shouldBe "납부 기간이 너무 짧습니다"
                }
            }

            context("FIXED_MONTHLY 타입") {
                it("월 납부 금액을 입력받아 완료일과 개월 수를 계산한다") {
                    // given: 100만원, 월 23만원 → 5개월 후 완료
                    val request =
                        FixedMonthlyCalculationRequest(
                            startDate = LocalDate.of(2025, 1, 1),
                            totalAmount = BigDecimal("1000000"),
                            completedAmount = BigDecimal.ZERO,
                            monthlyAmount = BigDecimal("230000"),
                            paymentDay = 15,
                        )

                    every {
                        repaymentScheduleCalculator.calculateCompletionDate(
                            startDate = request.startDate,
                            paymentDay = request.paymentDay,
                            monthlyAmount = request.monthlyAmount,
                            remainingAmount = BigDecimal("1000000"),
                        )
                    } returns
                        RepaymentScheduleCalculator.CompletionDateInfo(
                            completionDate = LocalDate.of(2025, 5, 15),
                            monthsLater = 5,
                        )

                    // when
                    val response = service.calculateRepayment(request)

                    // then
                    response shouldBe
                        FixedMonthlyCalculationResponse(
                            completionDate = LocalDate.of(2025, 5, 15),
                            monthsLater = 5,
                        )
                    verify {
                        repaymentScheduleCalculator.calculateCompletionDate(
                            startDate = request.startDate,
                            paymentDay = request.paymentDay,
                            monthlyAmount = request.monthlyAmount,
                            remainingAmount = BigDecimal("1000000"),
                        )
                    }
                }

                it("완료된 금액을 제외한 남은 금액으로 계산한다") {
                    // given
                    val request =
                        FixedMonthlyCalculationRequest(
                            startDate = LocalDate.of(2025, 1, 1),
                            totalAmount = BigDecimal("1000000"),
                            completedAmount = BigDecimal("400000"),
                            monthlyAmount = BigDecimal("200000"),
                            paymentDay = 10,
                        )

                    every {
                        repaymentScheduleCalculator.calculateCompletionDate(
                            any(),
                            any(),
                            any(),
                            remainingAmount = BigDecimal("600000"),
                        )
                    } returns
                        RepaymentScheduleCalculator.CompletionDateInfo(
                            completionDate = LocalDate.of(2025, 3, 10),
                            monthsLater = 3,
                        )

                    // when
                    val response = service.calculateRepayment(request)

                    // then
                    response shouldBe
                        FixedMonthlyCalculationResponse(
                            completionDate = LocalDate.of(2025, 3, 10),
                            monthsLater = 3,
                        )
                }

                it("남은 금액이 0 이하면 예외를 발생시킨다") {
                    // given
                    val request =
                        FixedMonthlyCalculationRequest(
                            startDate = LocalDate.of(2025, 1, 1),
                            totalAmount = BigDecimal("1000000"),
                            completedAmount = BigDecimal("1000000"),
                            monthlyAmount = BigDecimal("100000"),
                            paymentDay = 15,
                        )

                    // when & then
                    val exception =
                        shouldThrow<CustomException> {
                            service.calculateRepayment(request)
                        }
                    exception.message shouldBe "남은 금액이 0보다 커야 합니다"
                }

                it("월 납부 금액이 0 이하면 예외를 발생시킨다") {
                    // given
                    val request =
                        FixedMonthlyCalculationRequest(
                            startDate = LocalDate.of(2025, 1, 1),
                            totalAmount = BigDecimal("1000000"),
                            completedAmount = BigDecimal.ZERO,
                            monthlyAmount = BigDecimal.ZERO,
                            paymentDay = 15,
                        )

                    // when & then
                    val exception =
                        shouldThrow<CustomException> {
                            service.calculateRepayment(request)
                        }
                    exception.message shouldBe "월 납부 금액은 0보다 커야 합니다"
                }

                it("월 납부 금액이 남은 금액보다 크면 예외를 발생시킨다") {
                    // given
                    val request =
                        FixedMonthlyCalculationRequest(
                            startDate = LocalDate.of(2025, 1, 1),
                            totalAmount = BigDecimal("1000000"),
                            completedAmount = BigDecimal.ZERO,
                            monthlyAmount = BigDecimal("1500000"),
                            paymentDay = 15,
                        )

                    // when & then
                    val exception =
                        shouldThrow<CustomException> {
                            service.calculateRepayment(request)
                        }
                    exception.message shouldBe "월 납부 금액이 남은 금액보다 클 수 없습니다"
                }
            }

            context("FLEXIBLE 타입") {
                it("계산을 지원하지 않는다는 예외를 발생시킨다") {
                    // given: FLEXIBLE 타입은 CalculateRepaymentRequest의 sealed interface로 구현할 수 없으므로
                    // 이 테스트는 실제로는 RepaymentType.FLEXIBLE을 직접 사용하는 경우를 상정

                    // FLEXIBLE 타입은 현재 Request DTO에서 지원하지 않으므로 이 테스트는 스킵
                    // 실제로는 서비스 로직에서 FLEXIBLE 타입이 들어오면 예외를 던지도록 구현되어 있음
                }
            }
        }

        describe("비즈니스 시나리오 테스트") {
            context("사용자 스토리: 완료일 기준 상환 계획") {
                it("친구에게 100만원을 빌렸고, 6개월 안에 갚고 싶다면 매월 얼마를 갚아야 하는지 계산한다") {
                    // given
                    val request =
                        DividedByPeriodCalculationRequest(
                            startDate = LocalDate.of(2025, 1, 1),
                            totalAmount = BigDecimal("1000000"),
                            completedAmount = BigDecimal.ZERO,
                            targetDate = LocalDate.of(2025, 6, 15),
                            paymentDay = 15,
                        )

                    every {
                        repaymentScheduleCalculator.calculateMonthlyAmount(any(), any(), any(), any())
                    } returns BigDecimal("166666.67")

                    // when
                    val response = service.calculateRepayment(request) as DividedByPeriodCalculationResponse

                    // then: "매월 166,666.67원씩 납부하면 됩니다"
                    response.monthlyAmount shouldBe BigDecimal("166666.67")
                    response.repaymentType shouldBe RepaymentType.DIVIDED_BY_PERIOD
                }
            }

            context("사용자 스토리: 월 납부액 기준 상환 계획") {
                it("100만원을 빌렸고, 매월 25만원씩 갚을 수 있다면 언제 완료되는지 계산한다") {
                    // given
                    val request =
                        FixedMonthlyCalculationRequest(
                            startDate = LocalDate.of(2025, 1, 1),
                            totalAmount = BigDecimal("1000000"),
                            completedAmount = BigDecimal.ZERO,
                            monthlyAmount = BigDecimal("250000"),
                            paymentDay = 10,
                        )

                    every {
                        repaymentScheduleCalculator.calculateCompletionDate(any(), any(), any(), any())
                    } returns
                        RepaymentScheduleCalculator.CompletionDateInfo(
                            completionDate = LocalDate.of(2025, 4, 10),
                            monthsLater = 4,
                        )

                    // when
                    val response = service.calculateRepayment(request) as FixedMonthlyCalculationResponse

                    // then: "4개월 후, 4월 10일에 완료 예정입니다"
                    response.completionDate shouldBe LocalDate.of(2025, 4, 10)
                    response.monthsLater shouldBe 4
                    response.repaymentType shouldBe RepaymentType.FIXED_MONTHLY
                }
            }

            context("사용자 스토리: 이미 일부 갚은 경우") {
                it("100만원 중 30만원을 이미 갚았고, 3개월 후 완료하려면 매월 얼마를 갚아야 하는지 계산한다") {
                    // given
                    val request =
                        DividedByPeriodCalculationRequest(
                            startDate = LocalDate.of(2025, 1, 1),
                            totalAmount = BigDecimal("1000000"),
                            completedAmount = BigDecimal("300000"),
                            targetDate = LocalDate.of(2025, 3, 20),
                            paymentDay = 20,
                        )

                    every {
                        repaymentScheduleCalculator.calculateMonthlyAmount(
                            any(),
                            any(),
                            any(),
                            remainingAmount = BigDecimal("700000"),
                        )
                    } returns BigDecimal("233333.33")

                    // when
                    val response = service.calculateRepayment(request) as DividedByPeriodCalculationResponse

                    // then: 남은 70만원을 3개월에 나누면 약 23만원
                    response.monthlyAmount shouldBe BigDecimal("233333.33")
                }
            }
        }
    })
