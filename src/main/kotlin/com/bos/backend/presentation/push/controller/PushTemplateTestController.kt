package com.bos.backend.presentation.push.controller

import com.bos.backend.application.push.ExpoPushService
import com.bos.backend.application.push.PushTemplateService
import com.bos.backend.domain.transaction.enum.RepaymentStatus
import com.bos.backend.domain.transaction.enum.TransactionType
import com.bos.backend.domain.transaction.repository.RepaymentScheduleRepository
import com.bos.backend.domain.transaction.repository.TransactionRepository
import com.bos.backend.domain.user.repository.UserDeviceRepository
import com.bos.backend.domain.user.repository.UserRepository
import com.bos.backend.presentation.push.dto.ExpoPushTestResponseDTO
import com.bos.backend.presentation.push.dto.PartialRepaymentCompletePushTestRequestDTO
import com.bos.backend.presentation.push.dto.PushTestLookupResponseDTO
import com.bos.backend.presentation.push.dto.RepaymentOverduePushTestRequestDTO
import com.bos.backend.presentation.push.dto.RepaymentPushTestRequestDTO
import com.bos.backend.presentation.push.dto.ScheduleForPushTestDTO
import com.bos.backend.presentation.push.dto.SimplePushTestRequestDTO
import com.bos.backend.presentation.push.dto.SimplePushTestResponseDTO
import com.bos.backend.presentation.push.dto.TransactionCompletePushTestRequestDTO
import com.bos.backend.presentation.push.dto.TransactionForPushTestDTO
import com.bos.backend.presentation.push.dto.UsedTestData
import kotlinx.coroutines.flow.toList
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

/**
 * 푸시 템플릿 테스트 API 컨트롤러
 * 각 푸시 템플릿별로 테스트 발송할 수 있는 엔드포인트를 제공합니다
 */
@RestController
@RequestMapping("/push/test")
class PushTemplateTestController(
    private val pushTemplateService: PushTemplateService,
    private val expoPushService: ExpoPushService,
    private val userDeviceRepository: UserDeviceRepository,
    private val transactionRepository: TransactionRepository,
    private val repaymentScheduleRepository: RepaymentScheduleRepository,
    private val userRepository: UserRepository,
) {
    companion object {
        private const val DECIMAL_PRECISION = 4 // BigDecimal 계산 시 사용할 소수점 자릿수
        private const val PERCENTAGE_MULTIPLIER = 100 // 퍼센트 변환을 위한 승수
    }

    /**
     * 간편 푸시 테스트 - userId만으로 자동 테스트
     * 최신 거래 내역과 다음 상환 스케줄을 자동으로 조회하여 푸시 전송
     */
    @PostMapping("/simple")
    @Suppress("LongMethod", "ReturnCount")
    suspend fun testSimplePush(
        @RequestBody request: SimplePushTestRequestDTO,
    ): SimplePushTestResponseDTO {
        // 1. 사용자 조회
        val user =
            userRepository.findById(request.userId)
                ?: return SimplePushTestResponseDTO(
                    success = false,
                    message = "사용자를 찾을 수 없습니다",
                    sentCount = 0,
                    usedData = null,
                    error = "User not found: userId=${request.userId}",
                )

        // 2. 최신 거래 내역 조회
        val transactions = transactionRepository.findByUserId(request.userId)
        val latestTransaction =
            transactions.maxByOrNull { it.createdAt }
                ?: return SimplePushTestResponseDTO(
                    success = false,
                    message = "거래 내역이 없습니다",
                    sentCount = 0,
                    usedData = null,
                    error = "No transactions found for userId=${request.userId}",
                )

        // 3. 다음 상환 스케줄 조회
        val pendingSchedules =
            repaymentScheduleRepository.findPendingSchedulesByTransactionId(latestTransaction.id!!)
        val nextSchedule =
            pendingSchedules.firstOrNull()
                ?: return SimplePushTestResponseDTO(
                    success = false,
                    message = "상환 스케줄이 없습니다 (이미 모두 완료되었거나 FLEXIBLE 타입)",
                    sentCount = 0,
                    usedData = null,
                    error = "No pending schedules for transactionId=${latestTransaction.id}",
                )

        // 4. 디바이스 조회
        val devices = userDeviceRepository.findByUserId(request.userId).toList()
        if (devices.isEmpty()) {
            return SimplePushTestResponseDTO(
                success = false,
                message = "등록된 디바이스가 없습니다",
                sentCount = 0,
                usedData = null,
                error = "No devices registered for userId=${request.userId}",
            )
        }

        // 5. 거래 타입에 따라 적절한 템플릿 선택 및 푸시 메시지 생성
        val templateName =
            when (latestTransaction.transactionType) {
                TransactionType.LEND -> "REPAYMENT_REMINDER_PAYEE"
                TransactionType.BORROW -> "REPAYMENT_REMINDER_PAYER"
            }

        val messages =
            devices.mapNotNull { device ->
                device.expoToken?.let { token ->
                    when (latestTransaction.transactionType) {
                        TransactionType.LEND -> {
                            // 내가 빌려준 경우 -> 받을 사람 (payee)
                            pushTemplateService.createRepaymentReminderPayee(
                                expoToken = token,
                                nickname = user.nickname,
                                payerName = latestTransaction.counterpartName,
                                amount = nextSchedule.scheduledAmount.toLong(),
                                transactionId = latestTransaction.id!!,
                                scheduleId = nextSchedule.id!!,
                            )
                        }
                        TransactionType.BORROW -> {
                            // 내가 빌린 경우 -> 갚을 사람 (payer)
                            pushTemplateService.createRepaymentReminderPayer(
                                expoToken = token,
                                nickname = user.nickname,
                                payeeName = latestTransaction.counterpartName,
                                amount = nextSchedule.scheduledAmount.toLong(),
                                transactionId = latestTransaction.id!!,
                                scheduleId = nextSchedule.id!!,
                            )
                        }
                    }
                }
            }

        // 6. 푸시 전송
        val result = expoPushService.sendToMultipleDevices(devices, messages)

        // 7. 사용된 데이터 정보와 함께 응답
        val usedData =
            UsedTestData(
                userId = request.userId,
                nickname = user.nickname,
                transactionId = latestTransaction.id!!,
                transactionType = latestTransaction.transactionType.name,
                counterpartName = latestTransaction.counterpartName,
                totalAmount = latestTransaction.totalAmount,
                scheduleId = nextSchedule.id!!,
                scheduledDate = nextSchedule.scheduledDate,
                scheduledAmount = nextSchedule.scheduledAmount,
                templateUsed = templateName,
            )

        return SimplePushTestResponseDTO(
            success = result.successCount > 0,
            message = "푸시 전송 완료: 성공 ${result.successCount}건, 실패 ${result.failureCount}건",
            sentCount = result.successCount,
            usedData = usedData,
            error = null,
        )
    }

    /**
     * 상환 예정 안내 (갚을 사람) 테스트
     * D-2 오전 9시 발송
     */
    @PostMapping("/repayment-reminder-payer")
    suspend fun testRepaymentReminderPayer(
        @RequestBody request: RepaymentPushTestRequestDTO,
    ): ExpoPushTestResponseDTO {
        val devices = userDeviceRepository.findByUserId(request.userId).toList()

        val messages =
            devices.mapNotNull { device ->
                device.expoToken?.let { token ->
                    pushTemplateService.createRepaymentReminderPayer(
                        expoToken = token,
                        nickname = request.nickname,
                        payeeName = request.partnerName,
                        amount = request.amount,
                        transactionId = request.transactionId,
                        scheduleId = request.scheduleId,
                    )
                }
            }

        val result = expoPushService.sendToMultipleDevices(devices, messages)

        return ExpoPushTestResponseDTO(
            success = result.successCount > 0,
            message = "푸시 전송 완료: 성공 ${result.successCount}건, 실패 ${result.failureCount}건",
            sentCount = result.successCount,
            devices = null,
            error = null,
        )
    }

    /**
     * 상환 예정 안내 (받을 사람) 테스트
     * D-2 오전 9시 발송
     */
    @PostMapping("/repayment-reminder-payee")
    suspend fun testRepaymentReminderPayee(
        @RequestBody request: RepaymentPushTestRequestDTO,
    ): ExpoPushTestResponseDTO {
        val devices = userDeviceRepository.findByUserId(request.userId).toList()

        val messages =
            devices.mapNotNull { device ->
                device.expoToken?.let { token ->
                    pushTemplateService.createRepaymentReminderPayee(
                        expoToken = token,
                        nickname = request.nickname,
                        payerName = request.partnerName,
                        amount = request.amount,
                        transactionId = request.transactionId,
                        scheduleId = request.scheduleId,
                    )
                }
            }

        val result = expoPushService.sendToMultipleDevices(devices, messages)

        return ExpoPushTestResponseDTO(
            success = result.successCount > 0,
            message = "푸시 전송 완료: 성공 ${result.successCount}건, 실패 ${result.failureCount}건",
            sentCount = result.successCount,
            devices = null,
            error = null,
        )
    }

    /**
     * 상환일 당일 안내 (갚을 사람) 테스트
     * 당일 오전 9시 발송
     */
    @PostMapping("/repayment-today-payer")
    suspend fun testRepaymentTodayPayer(
        @RequestBody request: RepaymentPushTestRequestDTO,
    ): ExpoPushTestResponseDTO {
        val devices = userDeviceRepository.findByUserId(request.userId).toList()

        val messages =
            devices.mapNotNull { device ->
                device.expoToken?.let { token ->
                    pushTemplateService.createRepaymentTodayPayer(
                        expoToken = token,
                        nickname = request.nickname,
                        payeeName = request.partnerName,
                        amount = request.amount,
                        transactionId = request.transactionId,
                        scheduleId = request.scheduleId,
                    )
                }
            }

        val result = expoPushService.sendToMultipleDevices(devices, messages)

        return ExpoPushTestResponseDTO(
            success = result.successCount > 0,
            message = "푸시 전송 완료: 성공 ${result.successCount}건, 실패 ${result.failureCount}건",
            sentCount = result.successCount,
            devices = null,
            error = null,
        )
    }

    /**
     * 상환일 당일 안내 (받을 사람) 테스트
     * 당일 오전 9시 발송
     */
    @PostMapping("/repayment-today-payee")
    suspend fun testRepaymentTodayPayee(
        @RequestBody request: RepaymentPushTestRequestDTO,
    ): ExpoPushTestResponseDTO {
        val devices = userDeviceRepository.findByUserId(request.userId).toList()

        val messages =
            devices.mapNotNull { device ->
                device.expoToken?.let { token ->
                    pushTemplateService.createRepaymentTodayPayee(
                        expoToken = token,
                        nickname = request.nickname,
                        payerName = request.partnerName,
                        amount = request.amount,
                        transactionId = request.transactionId,
                        scheduleId = request.scheduleId,
                    )
                }
            }

        val result = expoPushService.sendToMultipleDevices(devices, messages)

        return ExpoPushTestResponseDTO(
            success = result.successCount > 0,
            message = "푸시 전송 완료: 성공 ${result.successCount}건, 실패 ${result.failureCount}건",
            sentCount = result.successCount,
            devices = null,
            error = null,
        )
    }

    /**
     * 상환 지연 경고 (갚을 사람) 테스트
     * 익일 오전 9시 발송
     */
    @PostMapping("/repayment-overdue-payer")
    suspend fun testRepaymentOverduePayer(
        @RequestBody request: RepaymentOverduePushTestRequestDTO,
    ): ExpoPushTestResponseDTO {
        val devices = userDeviceRepository.findByUserId(request.userId).toList()

        val messages =
            devices.mapNotNull { device ->
                device.expoToken?.let { token ->
                    pushTemplateService.createRepaymentOverduePayer(
                        expoToken = token,
                        nickname = request.nickname,
                        amount = request.amount,
                        transactionId = request.transactionId,
                        scheduleId = request.scheduleId,
                    )
                }
            }

        val result = expoPushService.sendToMultipleDevices(devices, messages)

        return ExpoPushTestResponseDTO(
            success = result.successCount > 0,
            message = "푸시 전송 완료: 성공 ${result.successCount}건, 실패 ${result.failureCount}건",
            sentCount = result.successCount,
            devices = null,
            error = null,
        )
    }

    /**
     * 상환 지연 경고 (받을 사람) 테스트
     * 익일 오전 9시 발송
     */
    @PostMapping("/repayment-overdue-payee")
    suspend fun testRepaymentOverduePayee(
        @RequestBody request: RepaymentOverduePushTestRequestDTO,
    ): ExpoPushTestResponseDTO {
        val devices = userDeviceRepository.findByUserId(request.userId).toList()

        val messages =
            devices.mapNotNull { device ->
                device.expoToken?.let { token ->
                    pushTemplateService.createRepaymentOverduePayee(
                        expoToken = token,
                        nickname = request.nickname,
                        amount = request.amount,
                        transactionId = request.transactionId,
                        scheduleId = request.scheduleId,
                    )
                }
            }

        val result = expoPushService.sendToMultipleDevices(devices, messages)

        return ExpoPushTestResponseDTO(
            success = result.successCount > 0,
            message = "푸시 전송 완료: 성공 ${result.successCount}건, 실패 ${result.failureCount}건",
            sentCount = result.successCount,
            devices = null,
            error = null,
        )
    }

    /**
     * 부분 상환 완료 안내 테스트
     * 상환 1회 완료 시 발송
     */
    @PostMapping("/partial-repayment-complete")
    suspend fun testPartialRepaymentComplete(
        @RequestBody request: PartialRepaymentCompletePushTestRequestDTO,
    ): ExpoPushTestResponseDTO {
        val devices = userDeviceRepository.findByUserId(request.userId).toList()

        val messages =
            devices.mapNotNull { device ->
                device.expoToken?.let { token ->
                    pushTemplateService.createPartialRepaymentComplete(
                        expoToken = token,
                        nickname = request.nickname,
                        partnerName = request.partnerName,
                        amount = request.amount,
                        progress = request.progress,
                        transactionId = request.transactionId,
                    )
                }
            }

        val result = expoPushService.sendToMultipleDevices(devices, messages)

        return ExpoPushTestResponseDTO(
            success = result.successCount > 0,
            message = "푸시 전송 완료: 성공 ${result.successCount}건, 실패 ${result.failureCount}건",
            sentCount = result.successCount,
            devices = null,
            error = null,
        )
    }

    /**
     * 거래 내역 최종 완료 안내 테스트
     * 해당 거래 내역 100% 완료 시 발송
     */
    @PostMapping("/transaction-complete")
    suspend fun testTransactionComplete(
        @RequestBody request: TransactionCompletePushTestRequestDTO,
    ): ExpoPushTestResponseDTO {
        val devices = userDeviceRepository.findByUserId(request.userId).toList()

        val messages =
            devices.mapNotNull { device ->
                device.expoToken?.let { token ->
                    pushTemplateService.createTransactionComplete(
                        expoToken = token,
                        nickname = request.nickname,
                        partnerName = request.partnerName,
                        transactionId = request.transactionId,
                    )
                }
            }

        val result = expoPushService.sendToMultipleDevices(devices, messages)

        return ExpoPushTestResponseDTO(
            success = result.successCount > 0,
            message = "푸시 전송 완료: 성공 ${result.successCount}건, 실패 ${result.failureCount}건",
            sentCount = result.successCount,
            devices = null,
            error = null,
        )
    }

    /**
     * 푸시 테스트용 사용자 데이터 조회
     * userId로 사용 가능한 거래와 스케줄 정보를 반환
     */
    @GetMapping("/lookup/{userId}")
    @Suppress("LongMethod")
    suspend fun lookupPushTestData(
        @PathVariable userId: Long,
    ): PushTestLookupResponseDTO {
        // 1. 사용자 조회
        val user =
            userRepository.findById(userId)
                ?: throw IllegalArgumentException("사용자를 찾을 수 없습니다: userId=$userId")

        // 2. 거래 목록 조회 (삭제되지 않은 것만)
        val transactions = transactionRepository.findByUserId(userId)

        // 3. 각 거래의 스케줄 조회
        val transactionIds = transactions.mapNotNull { it.id }
        val allSchedules =
            if (transactionIds.isNotEmpty()) {
                repaymentScheduleRepository.findByTransactionIdIn(transactionIds)
            } else {
                emptyList()
            }

        // 4. DTO 변환
        val transactionDTOs =
            transactions.mapNotNull { transaction ->
                val transactionId = transaction.id ?: return@mapNotNull null

                // 해당 거래의 모든 스케줄
                val transactionSchedules = allSchedules.filter { it.transactionId == transactionId }

                // 완료되지 않은 스케줄만 포함
                val schedules =
                    transactionSchedules
                        .filter { it.status != RepaymentStatus.COMPLETED }
                        .mapNotNull { schedule ->
                            val scheduleId = schedule.id ?: return@mapNotNull null
                            ScheduleForPushTestDTO(
                                scheduleId = scheduleId,
                                scheduledDate = schedule.scheduledDate,
                                scheduledAmount = schedule.scheduledAmount,
                                status = schedule.status,
                            )
                        }

                // 완료된 스케줄 금액 합산하여 진행률 계산
                val completedSchedules = transactionSchedules.filter { it.status == RepaymentStatus.COMPLETED }
                val completedAmount = completedSchedules.sumOf { it.scheduledAmount }
                val completedPercentage =
                    if (transaction.totalAmount > BigDecimal.ZERO) {
                        (
                            (
                                completedAmount.divide(
                                    transaction.totalAmount,
                                    DECIMAL_PRECISION,
                                    BigDecimal.ROUND_HALF_UP,
                                )
                            ).multiply(BigDecimal(PERCENTAGE_MULTIPLIER))
                        ).toInt()
                    } else {
                        0
                    }

                // 마지막 완료된 스케줄 금액 (부분 상환 완료 알림용)
                val lastCompletedAmount = completedSchedules.maxByOrNull { it.scheduledDate }?.scheduledAmount

                TransactionForPushTestDTO(
                    transactionId = transactionId,
                    counterpartName = transaction.counterpartName,
                    transactionType = transaction.transactionType,
                    totalAmount = transaction.totalAmount,
                    schedules = schedules,
                    completedPercentage = completedPercentage,
                    lastCompletedAmount = lastCompletedAmount,
                )
            }

        return PushTestLookupResponseDTO(
            userId = userId,
            nickname = user.nickname,
            transactions = transactionDTOs,
        )
    }
}
