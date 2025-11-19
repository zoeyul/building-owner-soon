package com.bos.backend.application.transaction

import com.bos.backend.domain.transaction.enum.RepaymentStatus
import com.bos.backend.domain.transaction.repository.RepaymentScheduleRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId

@Service
class RepaymentScheduleBatchService(
    private val repaymentScheduleRepository: RepaymentScheduleRepository,
) {
    private val logger = LoggerFactory.getLogger(RepaymentScheduleBatchService::class.java)

    @Scheduled(cron = "1 0 0 * * *", zone = "Asia/Seoul")
    fun updateRepaymentStatuses() {
        runBlocking {
            executeUpdateRepaymentStatuses()
        }
    }

    suspend fun executeUpdateRepaymentStatuses(): BatchExecutionResult {
        return try {
            logger.info("Starting repayment schedule status update batch job")

            val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
            val twoDaysLater = today.plusDays(2)

            // IN_PROGRESS: 상환일이 오늘부터 2일 후까지 (D-Day ~ D-2)
            // OVERDUE보다 먼저 실행하여 D-Day ~ D-2 범위가 IN_PROGRESS로 설정되도록 함
            val inProgressCount =
                repaymentScheduleRepository.updateInProgressStatuses(
                    today,
                    twoDaysLater,
                    RepaymentStatus.IN_PROGRESS,
                    RepaymentStatus.COMPLETED,
                )
            logger.info("Updated {} schedules to IN_PROGRESS status", inProgressCount)

            // OVERDUE: 상환일이 오늘 이전 (D+1부터 연체)
            // COMPLETED 상태는 제외
            val overdueCount =
                repaymentScheduleRepository.updateOverdueStatuses(
                    today,
                    RepaymentStatus.OVERDUE,
                    RepaymentStatus.COMPLETED,
                )
            logger.info("Updated {} schedules to OVERDUE status", overdueCount)

            logger.info(
                "Completed repayment schedule status update batch job. " +
                    "OVERDUE: {}, IN_PROGRESS: {}",
                overdueCount,
                inProgressCount,
            )

            BatchExecutionResult(
                success = true,
                overdueCount = overdueCount,
                inProgressCount = inProgressCount,
            )
        } catch (e: RuntimeException) {
            logger.error("Error occurred during repayment schedule status update batch job", e)
            BatchExecutionResult(
                success = false,
                overdueCount = 0,
                inProgressCount = 0,
                errorMessage = e.message,
            )
        }
    }
}

data class BatchExecutionResult(
    val success: Boolean,
    val overdueCount: Int,
    val inProgressCount: Int,
    val errorMessage: String? = null,
)
