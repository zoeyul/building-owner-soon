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
@Suppress("TooManyFunctions", "LongParameterList")
class TransactionService(
    private val transactionRepository: TransactionRepository,
    private val repaymentScheduleRepository: RepaymentScheduleRepository,
    private val userService: UserService,
    private val counterpartService: com.bos.backend.application.counterpart.CounterpartService,
    private val transactionalOperator: TransactionalOperator,
    private val characterBuilder: com.bos.backend.application.builder.CharacterBuilder,
    private val repaymentScheduleCalculator: RepaymentScheduleCalculator,
) {
    companion object {
        private const val OVERDUE_PRIORITY = 1
        private const val IN_PROGRESS_PRIORITY = 2
        private const val SCHEDULED_PRIORITY = 3
        private const val COMPLETED_PRIORITY = 4
        private const val DEFAULT_PRIORITY = 5
    }

    @Suppress("MagicNumber")
    suspend fun createTransaction(
        userId: Long,
        createTransactionRequestDTO: CreateTransactionRequestDTO,
    ) {
        createTransactionRequestDTO.completedAmount?.let { amount ->
            if (amount > BigDecimal.ZERO && amount < BigDecimal(10000)) {
                throw CustomException(CommonErrorCode.INVALID_PARAMETER)
            }
        }

        transactionalOperator.executeAndAwait {
            val counterpartCharacter =
                characterBuilder.buildCounterpartCharacter(createTransactionRequestDTO.counterpartCharacter)

            // Create counterpart (stored in separate table)
            val counterpart =
                counterpartService.createCounterpart(
                    userId = userId,
                    name = createTransactionRequestDTO.counterpartName,
                    character = counterpartCharacter,
                )

            val initialAmount = createTransactionRequestDTO.completedAmount ?: BigDecimal.ZERO
            val transaction =
                Transaction(
                    userId = userId,
                    counterpartId = counterpart.id,
                    transactionType = createTransactionRequestDTO.transactionType,
                    counterpartName = createTransactionRequestDTO.counterpartName,
                    counterpartCharacter = counterpartCharacter,
                    relationship = createTransactionRequestDTO.relationship,
                    customRelationship = createTransactionRequestDTO.customRelationship,
                    transactionDate = createTransactionRequestDTO.transactionDate,
                    totalAmount = createTransactionRequestDTO.totalAmount,
                    completedAmount = initialAmount,
                    initialCompletedAmount = initialAmount,
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
                ?: run {
                    // 삭제된 거래인지 확인
                    val deletedTransaction = transactionRepository.findByIdIncludingDeleted(transactionId)
                    if (deletedTransaction != null && deletedTransaction.isDeleted()) {
                        throw CustomException(CommonErrorCode.RESOURCE_DELETED)
                    }
                    throw CustomException(CommonErrorCode.RESOURCE_NOT_FOUND)
                }

        if (transaction.userId != userId) {
            throw CustomException(CommonErrorCode.RESOURCE_NOT_FOUND)
        }

        return toTransactionResponseDTO(transaction, transaction.id)
    }

    suspend fun getTransactionForShare(uuid: String): TransactionDetailResponseDTO {
        val transaction = getTransactionByUuid(uuid)
        val userProfile = userService.getUserProfile(transaction.userId)
        val transactionId = transaction.id!!
        val repaymentSchedules = repaymentScheduleRepository.findByTransactionId(transactionId)
        val sortedSchedules = sortRepaymentSchedules(repaymentSchedules)
        val (borrower, lender) = determineBorrowerAndLender(transaction, userProfile)
        val calculatedMonthlyAmount = calculateMonthlyAmount(transaction, transactionId)

        val profileCharacter = userProfile.character ?: throw CustomException(CommonErrorCode.RESOURCE_NOT_FOUND)
        val character =
            com.bos.backend.domain.user.entity.Character(
                face = profileCharacter.face,
                hand = profileCharacter.hand,
                skinColor = profileCharacter.skinColor,
                bang = profileCharacter.bang,
                backHair = profileCharacter.backHair,
                eyes = profileCharacter.eyes,
                mouth = profileCharacter.mouth,
            )

        return TransactionDetailResponseDTO(
            userProfileImage = character,
            transactionType = transaction.transactionType,
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

//    private suspend fun getTransactionById(transactionId: Long): Transaction =
//        transactionRepository.findById(transactionId)
//            ?: throw CustomException(CommonErrorCode.RESOURCE_NOT_FOUND)

    private suspend fun getTransactionByUuid(uuid: String): Transaction =
        transactionRepository.findByUuid(uuid)
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
            // 삭제된 거래를 포함하여 조회
            val transaction =
                transactionRepository.findByIdIncludingDeleted(transactionId)
                    ?: throw CustomException(CommonErrorCode.RESOURCE_NOT_FOUND)

            // 이미 삭제된 거래인지 확인
            if (transaction.isDeleted()) {
                throw CustomException(CommonErrorCode.RESOURCE_DELETED)
            }

            // 소유권 검증
            if (transaction.userId != userId) {
                throw CustomException(CommonErrorCode.RESOURCE_NOT_FOUND)
            }

            // Soft delete 수행
            transactionRepository.softDeleteById(transactionId)
        }

    suspend fun updateTransaction(
        userId: Long,
        transactionId: Long,
        updateTransactionRequestDTO: UpdateTransactionRequestDTO,
    ): TransactionResponseDTO =
        transactionalOperator.executeAndAwait {
            val existingTransaction =
                transactionRepository.findById(transactionId)
                    ?: run {
                        // 삭제된 거래인지 확인
                        val deletedTransaction = transactionRepository.findByIdIncludingDeleted(transactionId)
                        if (deletedTransaction != null && deletedTransaction.isDeleted()) {
                            throw CustomException(CommonErrorCode.RESOURCE_DELETED)
                        }
                        throw CustomException(CommonErrorCode.RESOURCE_NOT_FOUND)
                    }

            if (existingTransaction.userId != userId) {
                throw CustomException(CommonErrorCode.RESOURCE_NOT_FOUND)
            }

            val counterpartCharacter =
                characterBuilder.buildCounterpartCharacter(updateTransactionRequestDTO.counterpartCharacter)

            // Check if counterpart name or character has changed
            val counterpartId =
                if (updateTransactionRequestDTO.counterpartName != existingTransaction.counterpartName ||
                    counterpartCharacter != existingTransaction.counterpartCharacter
                ) {
                    // Create new counterpart with the new name and/or character
                    val newCounterpart =
                        counterpartService.createCounterpart(
                            userId = userId,
                            name = updateTransactionRequestDTO.counterpartName,
                            character = counterpartCharacter,
                        )
                    newCounterpart.id
                } else {
                    // Keep existing counterpart
                    existingTransaction.counterpartId
                }

            val updatedTransaction =
                existingTransaction.copy(
                    counterpartId = counterpartId,
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
        val startDate = transaction.createdAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate()

        // RepaymentScheduleCalculator를 사용하여 계산
        val paymentSchedules =
            repaymentScheduleCalculator.calculateDividedByPeriodSchedule(
                startDate = startDate,
                targetDate = targetDate,
                paymentDay = paymentDay,
                remainingAmount = remainingAmount,
            )

        // Calculator의 PaymentSchedule을 Domain의 RepaymentSchedule로 변환
        return paymentSchedules.map { schedule ->
            RepaymentSchedule(
                transactionId = transaction.id!!,
                scheduledDate = schedule.scheduledDate,
                scheduledAmount = schedule.scheduledAmount,
            )
        }
    }

    private fun generateFixedMonthlySchedules(transaction: Transaction): List<RepaymentSchedule> {
        val monthlyAmount = transaction.monthlyAmount ?: throw CustomException(CommonErrorCode.INVALID_PARAMETER)
        val paymentDay = transaction.paymentDay ?: throw CustomException(CommonErrorCode.INVALID_PARAMETER)
        val remainingAmount = transaction.remainingAmount()
        val startDate = transaction.createdAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate()

        // RepaymentScheduleCalculator를 사용하여 계산
        val paymentSchedules =
            repaymentScheduleCalculator.calculateFixedMonthlySchedule(
                startDate = startDate,
                paymentDay = paymentDay,
                monthlyAmount = monthlyAmount,
                remainingAmount = remainingAmount,
            )

        // Calculator의 PaymentSchedule을 Domain의 RepaymentSchedule로 변환
        return paymentSchedules.map { schedule ->
            RepaymentSchedule(
                transactionId = transaction.id!!,
                scheduledDate = schedule.scheduledDate,
                scheduledAmount = schedule.scheduledAmount,
            )
        }
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
            val counterpartId: Long?,
            val relationship: String,
            val customRelationship: String?,
        )

        val groupedTransactions =
            transactions.groupBy {
                CounterpartKey(
                    counterpartId = it.counterpartId,
                    relationship = it.relationship.name,
                    customRelationship = it.customRelationship,
                )
            }

        val today = LocalDate.now()
        val twoDaysFromNow = today.plusDays(2)

        return groupedTransactions.map { (_, txList) ->
            val firstTx = txList.first()

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
                counterpartName = firstTx.counterpartName,
                counterpartCharacter = firstTx.counterpartCharacter,
                relationship = firstTx.relationship,
                customRelationship = firstTx.customRelationship,
                transactionType = transactionType,
                totalAmount = totalAmount,
                upcomingTransactionInfo = upcomingInfo,
                transactionId = firstTx.id!!,
                transactionUuid = firstTx.uuid,
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
                scheduleId = earliestSchedule.id!!,
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
            transactionUuid = transaction.uuid,
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
