package com.bos.backend.presentation.transaction.controller

import com.bos.backend.application.transaction.RepaymentCalculationService
import com.bos.backend.application.transaction.RepaymentScheduleService
import com.bos.backend.application.transaction.TransactionService
import com.bos.backend.domain.transaction.enum.TransactionType
import com.bos.backend.presentation.transaction.dto.CalculateRepaymentRequest
import com.bos.backend.presentation.transaction.dto.CalculateRepaymentResponse
import com.bos.backend.presentation.transaction.dto.CompletedTransactionListItemDTO
import com.bos.backend.presentation.transaction.dto.CreateRepaymentRequestDTO
import com.bos.backend.presentation.transaction.dto.CreateTransactionRequestDTO
import com.bos.backend.presentation.transaction.dto.DebtSummaryResponseDTO
import com.bos.backend.presentation.transaction.dto.RepaymentManagementResponseDTO
import com.bos.backend.presentation.transaction.dto.RepaymentScheduleItemDTO
import com.bos.backend.presentation.transaction.dto.TransactionDetailResponseDTO
import com.bos.backend.presentation.transaction.dto.TransactionResponseDTO
import com.bos.backend.presentation.transaction.dto.UpdateTransactionRequestDTO
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/transactions")
@Suppress("TooManyFunctions")
class TransactionController(
    private val transactionService: TransactionService,
    private val repaymentScheduleService: RepaymentScheduleService,
    private val repaymentCalculationService: RepaymentCalculationService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun createTransaction(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody createTransactionRequestDTO: CreateTransactionRequestDTO,
    ) {
        transactionService.createTransaction(userId.toLong(), createTransactionRequestDTO)
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    suspend fun getTransactions(
        @AuthenticationPrincipal userId: String,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) type: TransactionType?,
    ): List<CompletedTransactionListItemDTO> {
        if (status != null && status != "COMPLETED") {
            throw com.bos.backend.application.CustomException(
                com.bos.backend.application.CommonErrorCode.INVALID_PARAMETER,
            )
        }
        return transactionService.getCompletedTransactions(userId.toLong(), type)
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    suspend fun getTransaction(
        @AuthenticationPrincipal userId: String,
        @PathVariable id: Long,
    ): TransactionResponseDTO = transactionService.getTransactionDetail(userId.toLong(), id)

    @GetMapping("/{uuid}/share")
    @ResponseStatus(HttpStatus.OK)
    suspend fun getTransactionForShare(
        @PathVariable uuid: String,
    ): TransactionDetailResponseDTO = transactionService.getTransactionForShare(uuid)

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    suspend fun updateTransaction(
        @AuthenticationPrincipal userId: String,
        @PathVariable id: Long,
        @Valid @RequestBody updateTransactionRequestDTO: UpdateTransactionRequestDTO,
    ): TransactionResponseDTO = transactionService.updateTransaction(userId.toLong(), id, updateTransactionRequestDTO)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun deleteTransaction(
        @AuthenticationPrincipal userId: String,
        @PathVariable id: Long,
    ): Unit = transactionService.deleteTransaction(userId.toLong(), id)

    @PostMapping("/{id}/celebration-complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun completeCelebration(
        @AuthenticationPrincipal userId: String,
        @PathVariable id: Long,
    ): Unit = transactionService.completeCelebration(userId.toLong(), id)

    @GetMapping("/{id}/repayment-schedules")
    @ResponseStatus(HttpStatus.OK)
    suspend fun getRepaymentManagement(
        @AuthenticationPrincipal userId: String,
        @PathVariable id: Long,
    ): RepaymentManagementResponseDTO = repaymentScheduleService.getRepaymentManagement(userId.toLong(), id)

    @PostMapping("/{transactionId}/repayments")
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun processRepayment(
        @AuthenticationPrincipal userId: String,
        @PathVariable transactionId: Long,
        @Valid @RequestBody createRepaymentRequestDTO: CreateRepaymentRequestDTO,
    ): RepaymentScheduleItemDTO =
        repaymentScheduleService.processRepayment(
            userId.toLong(),
            transactionId,
            createRepaymentRequestDTO,
        )

    @GetMapping("/summary")
    @ResponseStatus(HttpStatus.OK)
    suspend fun getTransactionSummary(
        @AuthenticationPrincipal userId: String,
    ): DebtSummaryResponseDTO = transactionService.getTransactionSummary(userId.toLong())

    @PostMapping("/calculate-repayment")
    @ResponseStatus(HttpStatus.OK)
    fun calculateRepayment(
        @Suppress("UNUSED_PARAMETER")
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: CalculateRepaymentRequest,
    ): CalculateRepaymentResponse = repaymentCalculationService.calculateRepayment(request)
}
