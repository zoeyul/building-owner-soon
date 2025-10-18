package com.bos.backend.application.transaction

import com.bos.backend.application.CommonErrorCode
import com.bos.backend.application.CustomException
import com.bos.backend.application.user.UserService
import com.bos.backend.domain.transaction.entity.RepaymentSchedule
import com.bos.backend.domain.transaction.entity.Transaction
import com.bos.backend.domain.transaction.enum.RepaymentStatus
import com.bos.backend.domain.transaction.enum.RepaymentType
import com.bos.backend.domain.transaction.enum.TransactionType
import com.bos.backend.domain.transaction.repository.RepaymentScheduleRepository
import com.bos.backend.domain.transaction.repository.TransactionRepository
import com.bos.backend.presentation.transaction.dto.CreateTransactionRequestDTO
import com.bos.backend.presentation.transaction.dto.DebtSummaryResponseDTO
import com.bos.backend.presentation.transaction.dto.RelationshipSummaryDTO
import com.bos.backend.presentation.transaction.dto.RepaymentScheduleDetailDTO
import com.bos.backend.presentation.transaction.dto.TransactionDetailResponseDTO
import com.bos.backend.presentation.transaction.dto.TransactionResponseDTO
import com.bos.backend.presentation.transaction.dto.TransactionSummaryDTO
import com.bos.backend.presentation.transaction.dto.UpcomingTransactionInfoDTO
import com.bos.backend.presentation.transaction.dto.UpdateTransactionRequestDTO
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
@Suppress("TooManyFunctions")
class TransactionService(
    private val transactionRepository: TransactionRepository,
    private val repaymentScheduleRepository: RepaymentScheduleRepository,
    private val userService: UserService,
    private val transactionalOperator: TransactionalOperator,
    private val characterBuilder: com.bos.backend.application.builder.CharacterBuilder,
) {
    companion object {
        private const val OVERDUE_PRIORITY = 1
        private const val SCHEDULED_PRIORITY = 2
        private const val IN_PROGRESS_PRIORITY = 3
        private const val COMPLETED_PRIORITY = 4
        private const val DEFAULT_PRIORITY = 5
    }

    suspend fun createTransaction(
        userId: Long,
        createTransactionRequestDTO: CreateTransactionRequestDTO,
    ) {
        transactionalOperator.executeAndAwait {
            val counterpartCharacter =
                characterBuilder.buildCounterpartCharacter(createTransactionRequestDTO.counterpartCharacter)
            val transaction =
                Transaction(
                    userId = userId,
                    transactionType = createTransactionRequestDTO.transactionType,
                    counterpartName = createTransactionRequestDTO.counterpartName,
                    counterpartCharacter = counterpartCharacter,
                    relationship = createTransactionRequestDTO.relationship,
                    customRelationship = createTransactionRequestDTO.customRelationship,
                    transactionDate = createTransactionRequestDTO.transactionDate,
                    totalAmount = createTransactionRequestDTO.totalAmount,
                    completedAmount = createTransactionRequestDTO.completedAmount ?: BigDecimal.ZERO,
                    memo = createTransactionRequestDTO.memo,
                    repaymentType = createTransactionRequestDTO.repaymentType,
                    targetDate = createTransactionRequestDTO.targetDate,
                    monthlyAmount = createTransactionRequestDTO.monthlyAmount,
                    paymentDay = createTransactionRequestDTO.paymentDay,
                )

            val savedTransaction = transactionRepository.save(transaction)
            generateRepaymentSchedules(savedTransaction)
        }
    }

    suspend fun getTransactionDetail(
        userId: Long,
        transactionId: Long,
    ): TransactionResponseDTO {
        val transaction =
            transactionRepository.findById(transactionId)
                ?: throw CustomException(CommonErrorCode.RESOURCE_NOT_FOUND)

        if (transaction.userId != userId) {
            throw CustomException(CommonErrorCode.RESOURCE_NOT_FOUND)
        }

        return toTransactionResponseDTO(transaction, transaction.id)
    }

    suspend fun getTransactionForShare(transactionId: Long): TransactionDetailResponseDTO {
        val transaction = getTransactionById(transactionId)
        val userProfile = userService.getUserProfile(transaction.userId)
        val repaymentSchedules = repaymentScheduleRepository.findByTransactionId(transactionId)
        val sortedSchedules = sortRepaymentSchedules(repaymentSchedules)
        val (borrower, lender) = determineBorrowerAndLender(transaction, userProfile)
        val calculatedMonthlyAmount = calculateMonthlyAmount(transaction, transactionId)

        return TransactionDetailResponseDTO(
            userProfileImage = userProfile.character ?: throw CustomException(CommonErrorCode.RESOURCE_NOT_FOUND),
            totalAmount = transaction.totalAmount,
            remainingAmount = transaction.remainingAmount(),
            repaymentType = transaction.repaymentType,
            monthlyAmount = calculatedMonthlyAmount,
            paymentDay = transaction.paymentDay,
            borrower = borrower,
            lender = lender,
            repaymentSchedules = sortedSchedules.map { mapToRepaymentScheduleDetailDTO(it) },
        )
    }

    private suspend fun getTransactionById(transactionId: Long): Transaction =
        transactionRepository.findById(transactionId)
            ?: throw CustomException(CommonErrorCode.RESOURCE_NOT_FOUND)

    private fun sortRepaymentSchedules(schedules: List<RepaymentSchedule>) =
        schedules
            .sortedWith(
                compareBy<RepaymentSchedule> { schedule ->
                    when (schedule.status.name) {
                        "OVERDUE" -> OVERDUE_PRIORITY
                        "IN_PROGRESS" -> IN_PROGRESS_PRIORITY
                        "SCHEDULED" -> SCHEDULED_PRIORITY
                        "COMPLETED" -> COMPLETED_PRIORITY
                        else -> DEFAULT_PRIORITY
                    }
                }.thenByDescending { it.scheduledDate },
            )

    private fun determineBorrowerAndLender(
        transaction: Transaction,
        userProfile: com.bos.backend.presentation.user.dto.UserProfileResponseDTO,
    ): Pair<String, String> =
        when (transaction.transactionType.name) {
            "LEND" -> Pair(transaction.counterpartName, userProfile.nickname)
            "BORROW" -> Pair(userProfile.nickname, transaction.counterpartName)
            else -> Pair(transaction.counterpartName, userProfile.nickname)
        }

    private fun mapToRepaymentScheduleDetailDTO(schedule: RepaymentSchedule) =
        RepaymentScheduleDetailDTO(
            id = schedule.id!!,
            status = schedule.status.name,
            displayDate =
                if (schedule.status.name == "COMPLETED") {
                    schedule.actualDate?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        ?: schedule.scheduledDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                } else {
                    schedule.scheduledDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                },
            displayAmount =
                if (schedule.status.name == "COMPLETED") {
                    schedule.actualAmount ?: schedule.scheduledAmount
                } else {
                    schedule.scheduledAmount
                },
        )

    suspend fun deleteTransaction(
        userId: Long,
        transactionId: Long,
    ): Unit =
        transactionalOperator.executeAndAwait {
            val transaction =
                transactionRepository.findById(transactionId)
                    ?: throw CustomException(CommonErrorCode.RESOURCE_NOT_FOUND)

            if (transaction.userId != userId) {
                throw CustomException(CommonErrorCode.RESOURCE_NOT_FOUND)
            }

            transactionRepository.deleteById(transactionId)
        }

    suspend fun updateTransaction(
        userId: Long,
        transactionId: Long,
        updateTransactionRequestDTO: UpdateTransactionRequestDTO,
    ): TransactionResponseDTO =
        transactionalOperator.executeAndAwait {
            val existingTransaction =
                transactionRepository.findById(transactionId)
                    ?: throw CustomException(CommonErrorCode.RESOURCE_NOT_FOUND)

            if (existingTransaction.userId != userId) {
                throw CustomException(CommonErrorCode.RESOURCE_NOT_FOUND)
            }

            val counterpartCharacter =
                characterBuilder.buildCounterpartCharacter(updateTransactionRequestDTO.counterpartCharacter)
            val updatedTransaction =
                existingTransaction.copy(
                    counterpartName = updateTransactionRequestDTO.counterpartName,
                    counterpartCharacter = counterpartCharacter,
                    relationship = updateTransactionRequestDTO.relationship,
                    customRelationship = updateTransactionRequestDTO.customRelationship,
                    memo = updateTransactionRequestDTO.memo,
                )

            val savedTransaction = transactionRepository.save(updatedTransaction)
            toTransactionResponseDTO(savedTransaction)
        }

    private suspend fun generateRepaymentSchedules(transaction: Transaction) {
        if (transaction.repaymentType == RepaymentType.FLEXIBLE) {
            return
        }

        val schedules =
            when (transaction.repaymentType) {
                RepaymentType.DIVIDED_BY_PERIOD -> generateDividedByPeriodSchedules(transaction)
                RepaymentType.FIXED_MONTHLY -> generateFixedMonthlySchedules(transaction)
                else -> emptyList()
            }

        repaymentScheduleRepository.saveAll(schedules)
    }

    private fun generateDividedByPeriodSchedules(transaction: Transaction): List<RepaymentSchedule> {
        val targetDate = transaction.targetDate ?: throw CustomException(CommonErrorCode.INVALID_PARAMETER)
        val paymentDay = transaction.paymentDay ?: throw CustomException(CommonErrorCode.INVALID_PARAMETER)
        val remainingAmount = transaction.remainingAmount()

        val schedules = mutableListOf<RepaymentSchedule>()
        var currentDate =
            calculateNextPaymentDate(
                transaction.createdAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                paymentDay,
            )

        val monthsList = mutableListOf<LocalDate>()
        while (currentDate.isBefore(targetDate) || currentDate.isEqual(targetDate)) {
            monthsList.add(currentDate)
            currentDate = calculateNextPaymentDate(currentDate, paymentDay)
        }

        if (monthsList.isNotEmpty()) {
            val amountPerPeriod = remainingAmount.divide(BigDecimal(monthsList.size), 2, RoundingMode.HALF_UP)

            monthsList.forEachIndexed { index, paymentDate ->
                val amount =
                    if (index == monthsList.size - 1) {
                        remainingAmount - amountPerPeriod.multiply(BigDecimal(monthsList.size - 1))
                    } else {
                        amountPerPeriod
                    }

                schedules.add(
                    RepaymentSchedule(
                        transactionId = transaction.id!!,
                        scheduledDate = paymentDate,
                        scheduledAmount = amount,
                    ),
                )
            }
        }

        return schedules
    }

    private fun generateFixedMonthlySchedules(transaction: Transaction): List<RepaymentSchedule> {
        val monthlyAmount = transaction.monthlyAmount ?: throw CustomException(CommonErrorCode.INVALID_PARAMETER)
        val paymentDay = transaction.paymentDay ?: throw CustomException(CommonErrorCode.INVALID_PARAMETER)
        val totalAmount = transaction.totalAmount

        val schedules = mutableListOf<RepaymentSchedule>()
        var currentDate =
            calculateNextPaymentDate(
                transaction.createdAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                paymentDay,
            )
        var remainingAmount = totalAmount

        while (remainingAmount > BigDecimal.ZERO) {
            val paymentAmount = if (remainingAmount < monthlyAmount) remainingAmount else monthlyAmount

            schedules.add(
                RepaymentSchedule(
                    transactionId = transaction.id!!,
                    scheduledDate = currentDate,
                    scheduledAmount = paymentAmount,
                ),
            )

            remainingAmount -= paymentAmount
            currentDate = calculateNextPaymentDate(currentDate, paymentDay)
        }

        return schedules
    }

    private fun calculateNextPaymentDate(
        baseDate: LocalDate,
        paymentDay: Int,
    ): LocalDate {
        val targetMonth =
            if (baseDate.dayOfMonth >= paymentDay) {
                baseDate.plusMonths(1)
            } else {
                baseDate
            }

        val lastDayOfMonth = targetMonth.lengthOfMonth()

        val adjustedPaymentDay = if (paymentDay > lastDayOfMonth) lastDayOfMonth else paymentDay

        return targetMonth.withDayOfMonth(adjustedPaymentDay)
    }

    private suspend fun calculateMonthlyAmount(
        transaction: Transaction,
        transactionId: Long,
    ): BigDecimal? =
        when (transaction.repaymentType) {
            RepaymentType.DIVIDED_BY_PERIOD -> {
                val repaymentSchedules =
                    repaymentScheduleRepository.findByTransactionId(transactionId)
                        .filter { it.status != RepaymentStatus.COMPLETED }
                if (repaymentSchedules.isNotEmpty()) {
                    transaction.remainingAmount().divide(
                        BigDecimal(repaymentSchedules.size),
                        2,
                        RoundingMode.HALF_UP,
                    )
                } else {
                    null
                }
            }
            RepaymentType.FIXED_MONTHLY -> transaction.monthlyAmount
            RepaymentType.FLEXIBLE -> null
        }

    suspend fun getTransactionSummary(userId: Long): DebtSummaryResponseDTO {
        val transactions = transactionRepository.findByUserId(userId)

        val lendTransactions = transactions.filter { it.transactionType == TransactionType.LEND }
        val borrowTransactions = transactions.filter { it.transactionType == TransactionType.BORROW }

        val lendSummary =
            TransactionSummaryDTO(
                totalAmount = lendTransactions.sumOf { it.totalAmount }.toLong(),
                completedAmount = lendTransactions.sumOf { it.completedAmount }.toLong(),
                remainingAmount = lendTransactions.sumOf { it.remainingAmount() }.toLong(),
            )

        val borrowSummary =
            TransactionSummaryDTO(
                totalAmount = borrowTransactions.sumOf { it.totalAmount }.toLong(),
                completedAmount = borrowTransactions.sumOf { it.completedAmount }.toLong(),
                remainingAmount = borrowTransactions.sumOf { it.remainingAmount() }.toLong(),
            )

        return DebtSummaryResponseDTO(
            lendSummary = lendSummary,
            borrowSummary = borrowSummary,
        )
    }

    suspend fun getRelationships(userId: Long): List<RelationshipSummaryDTO> {
        val transactions = transactionRepository.findByUserId(userId)

        if (transactions.isEmpty()) {
            return emptyList()
        }

        val transactionIds = transactions.mapNotNull { it.id }
        val allSchedules = repaymentScheduleRepository.findByTransactionIdIn(transactionIds)

        data class CounterpartKey(
            val name: String,
            val relationship: String,
            val customRelationship: String?,
        )

        val groupedTransactions =
            transactions.groupBy {
                CounterpartKey(
                    name = it.counterpartName,
                    relationship = it.relationship.name,
                    customRelationship = it.customRelationship,
                )
            }

        val today = LocalDate.now()
        val twoDaysFromNow = today.plusDays(2)

        return groupedTransactions.map { (key, txList) ->
            val lendAmount =
                txList
                    .filter { it.transactionType == TransactionType.LEND }
                    .sumOf { it.remainingAmount() }
                    .toLong()
            val borrowAmount =
                txList
                    .filter { it.transactionType == TransactionType.BORROW }
                    .sumOf { it.remainingAmount() }
                    .toLong()

            val (transactionType, totalAmount) =
                if (lendAmount >= borrowAmount) {
                    Pair(TransactionType.LEND, lendAmount)
                } else {
                    Pair(TransactionType.BORROW, borrowAmount)
                }

            val upcomingInfo = findUpcomingTransactionInfo(txList, allSchedules, today, twoDaysFromNow)

            RelationshipSummaryDTO(
                counterpartName = key.name,
                counterpartCharacter = txList.first().counterpartCharacter,
                relationship = txList.first().relationship,
                customRelationship = key.customRelationship,
                transactionType = transactionType,
                totalAmount = totalAmount,
                upcomingTransactionInfo = upcomingInfo,
                transactionId = txList.first().id!!,
            )
        }
    }

    private fun findUpcomingTransactionInfo(
        transactions: List<Transaction>,
        allSchedules: List<RepaymentSchedule>,
        today: LocalDate,
        twoDaysFromNow: LocalDate,
    ): UpcomingTransactionInfoDTO? {
        val transactionIds = transactions.mapNotNull { it.id }
        val schedules =
            allSchedules
                .filter { it.transactionId in transactionIds }
                .filter { it.status != RepaymentStatus.COMPLETED }

        val upcomingSchedules =
            schedules
                .filter {
                    it.status == RepaymentStatus.OVERDUE ||
                        it.status == RepaymentStatus.IN_PROGRESS ||
                        (it.scheduledDate in today..twoDaysFromNow)
                }
                .sortedBy { it.scheduledDate }

        return upcomingSchedules.firstOrNull()?.let { earliestSchedule ->
            UpcomingTransactionInfoDTO(
                dueDate = earliestSchedule.scheduledDate,
                amount = earliestSchedule.scheduledAmount.toLong(),
            )
        }
    }

    private suspend fun toTransactionResponseDTO(
        transaction: Transaction,
        transactionId: Long? = null,
    ): TransactionResponseDTO {
        val calculatedMonthlyAmount =
            if (transactionId != null) {
                calculateMonthlyAmount(transaction, transactionId)
            } else {
                transaction.monthlyAmount
            }

        return TransactionResponseDTO(
            id = transaction.id!!,
            transactionType = transaction.transactionType,
            counterpartName = transaction.counterpartName,
            counterpartCharacter = transaction.counterpartCharacter,
            relationship = transaction.relationship,
            customRelationship = transaction.customRelationship,
            transactionDate = transaction.transactionDate,
            totalAmount = transaction.totalAmount,
            completedAmount = transaction.completedAmount,
            remainingAmount = transaction.remainingAmount(),
            memo = transaction.memo,
            repaymentType = transaction.repaymentType,
            targetDate = transaction.targetDate,
            monthlyAmount = calculatedMonthlyAmount,
            paymentDay = transaction.paymentDay,
            createdAt = transaction.createdAt,
            updatedAt = transaction.updatedAt,
        )
    }
}
