package com.bos.backend.application.transaction.strategy

import com.bos.backend.domain.transaction.enum.RepaymentType
import org.springframework.stereotype.Component

@Component
class RepaymentStrategyFactory(
    private val flexibleRepaymentStrategy: FlexibleRepaymentStrategy,
    private val scheduledRepaymentStrategy: ScheduledRepaymentStrategy,
) {
    fun getStrategy(repaymentType: RepaymentType): RepaymentStrategy =
        when (repaymentType) {
            RepaymentType.FLEXIBLE,
            -> flexibleRepaymentStrategy
            RepaymentType.DIVIDED_BY_PERIOD,
            RepaymentType.FIXED_MONTHLY,
            -> scheduledRepaymentStrategy
        }
}
