package com.bos.backend.presentation.transaction.controller

import com.bos.backend.application.transaction.RepaymentScheduleBatchService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/repayment-schedules/batch")
class AdminRepaymentScheduleBatchController(
    private val repaymentScheduleBatchService: RepaymentScheduleBatchService,
) {
    @PostMapping("/run")
    suspend fun runBatch(): ResponseEntity<Map<String, Any>> {
        val result = repaymentScheduleBatchService.executeUpdateRepaymentStatuses()

        return if (result.success) {
            ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "message" to "Repayment schedule batch job completed successfully",
                    "overdueCount" to result.overdueCount,
                    "inProgressCount" to result.inProgressCount,
                ),
            )
        } else {
            ResponseEntity.internalServerError().body(
                mapOf(
                    "success" to false,
                    "message" to "Repayment schedule batch job failed",
                    "error" to (result.errorMessage ?: "Unknown error"),
                ),
            )
        }
    }
}
