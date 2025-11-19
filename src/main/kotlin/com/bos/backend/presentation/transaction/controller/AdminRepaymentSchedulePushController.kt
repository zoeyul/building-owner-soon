package com.bos.backend.presentation.transaction.controller

import com.bos.backend.application.transaction.RepaymentSchedulePushBatchService
import com.bos.backend.domain.transaction.repository.RepaymentScheduleRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.ZoneId

@RestController
@RequestMapping("/admin/repayment-schedules/push")
class AdminRepaymentSchedulePushController(
    private val repaymentSchedulePushBatchService: RepaymentSchedulePushBatchService,
    private val repaymentScheduleRepository: RepaymentScheduleRepository,
) {
    @PostMapping("/run")
    suspend fun runPushBatch(): ResponseEntity<Map<String, Any>> {
        val result = repaymentSchedulePushBatchService.executePushNotificationBatch()

        return if (result.success) {
            ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "message" to "Push notification batch completed successfully",
                    "reminderCount" to result.reminderCount,
                    "todayCount" to result.todayCount,
                    "overdueCount" to result.overdueCount,
                    "totalSent" to (result.reminderCount + result.todayCount + result.overdueCount),
                ),
            )
        } else {
            ResponseEntity.internalServerError().body(
                mapOf(
                    "success" to false,
                    "message" to "Push notification batch failed",
                    "error" to (result.errorMessage ?: "Unknown error"),
                ),
            )
        }
    }

    @PostMapping("/run/{pushType}")
    suspend fun runSpecificPush(
        @PathVariable pushType: String,
    ): ResponseEntity<Map<String, Any>> {
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))

        val (count, type) =
            when (pushType.uppercase()) {
                "REMINDER" -> {
                    val twoDaysLater = today.plusDays(2)
                    repaymentSchedulePushBatchService.sendReminderPushes(twoDaysLater) to "REMINDER (D-2)"
                }
                "TODAY" -> {
                    repaymentSchedulePushBatchService.sendTodayPushes(today) to "TODAY (D-Day)"
                }
                "OVERDUE" -> {
                    repaymentSchedulePushBatchService.sendOverduePushes() to "OVERDUE (D+1)"
                }
                else ->
                    return ResponseEntity.badRequest().body(
                        mapOf(
                            "success" to false,
                            "message" to "Invalid push type. Use: REMINDER, TODAY, or OVERDUE",
                        ),
                    )
            }

        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "message" to "Push type $type completed",
                "sentCount" to count,
                "date" to today.toString(),
            ),
        )
    }

    @GetMapping("/preview")
    suspend fun previewNextBatch(): ResponseEntity<Map<String, Any>> {
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        val twoDaysLater = today.plusDays(2)

        val reminderSchedules = repaymentScheduleRepository.findSchedulesForReminder(twoDaysLater)
        val todaySchedules = repaymentScheduleRepository.findSchedulesForToday(today)
        val overdueSchedules = repaymentScheduleRepository.findOverdueSchedules()

        return ResponseEntity.ok(
            mapOf(
                "currentDate" to today.toString(),
                "reminder" to
                    mapOf(
                        "description" to "Schedules for D-2 reminder (상환 2일 전 알림)",
                        "targetDate" to twoDaysLater.toString(),
                        "count" to reminderSchedules.size,
                        "schedules" to
                            reminderSchedules.map {
                                mapOf(
                                    "scheduleId" to it.id,
                                    "transactionId" to it.transactionId,
                                    "scheduledDate" to it.scheduledDate.toString(),
                                    "scheduledAmount" to it.scheduledAmount,
                                    "status" to it.status.name,
                                )
                            },
                    ),
                "today" to
                    mapOf(
                        "description" to "Schedules for D-Day reminder (상환일 당일 알림)",
                        "targetDate" to today.toString(),
                        "count" to todaySchedules.size,
                        "schedules" to
                            todaySchedules.map {
                                mapOf(
                                    "scheduleId" to it.id,
                                    "transactionId" to it.transactionId,
                                    "scheduledDate" to it.scheduledDate.toString(),
                                    "scheduledAmount" to it.scheduledAmount,
                                    "status" to it.status.name,
                                )
                            },
                    ),
                "overdue" to
                    mapOf(
                        "description" to "Schedules for D+1 overdue reminder (연체 알림)",
                        "count" to overdueSchedules.size,
                        "schedules" to
                            overdueSchedules.map {
                                mapOf(
                                    "scheduleId" to it.id,
                                    "transactionId" to it.transactionId,
                                    "scheduledDate" to it.scheduledDate.toString(),
                                    "scheduledAmount" to it.scheduledAmount,
                                    "status" to it.status.name,
                                )
                            },
                    ),
            ),
        )
    }
}
