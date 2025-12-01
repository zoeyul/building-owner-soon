package com.bos.backend.application.transaction

import com.bos.backend.application.notification.NotificationService
import com.bos.backend.application.push.ExpoPushService
import com.bos.backend.application.push.PushTemplateService
import com.bos.backend.domain.push.ExpoPushMessage
import com.bos.backend.domain.push.PushTemplateType
import com.bos.backend.domain.transaction.entity.RepaymentSchedule
import com.bos.backend.domain.transaction.enum.TransactionType
import com.bos.backend.domain.transaction.repository.RepaymentScheduleRepository
import com.bos.backend.domain.transaction.repository.TransactionRepository
import com.bos.backend.domain.user.repository.UserDeviceRepository
import com.bos.backend.domain.user.repository.UserRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@Service
@Suppress("LongParameterList")
class RepaymentSchedulePushBatchService(
    private val repaymentScheduleRepository: RepaymentScheduleRepository,
    private val transactionRepository: TransactionRepository,
    private val userRepository: UserRepository,
    private val userDeviceRepository: UserDeviceRepository,
    private val pushTemplateService: PushTemplateService,
    private val expoPushService: ExpoPushService,
    private val notificationService: NotificationService,
) {
    private val logger = LoggerFactory.getLogger(RepaymentSchedulePushBatchService::class.java)

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    fun sendDailyRepaymentPushNotifications() {
        runBlocking {
            executePushNotificationBatch()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun executePushNotificationBatch(): PushBatchResult {
        return try {
            logger.info("Starting repayment push notification batch job")

            val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
            val twoDaysLater = today.plusDays(2)
            val yesterday = today.minusDays(1)

            val reminderCount = sendReminderPushes(twoDaysLater)
            val todayCount = sendTodayPushes(today)
            val overdueCount = sendOverduePushes(yesterday)

            logger.info(
                "Completed repayment push notification batch job. " +
                    "REMINDER: {}, TODAY: {}, OVERDUE: {}",
                reminderCount,
                todayCount,
                overdueCount,
            )

            PushBatchResult(
                success = true,
                reminderCount = reminderCount,
                todayCount = todayCount,
                overdueCount = overdueCount,
            )
        } catch (e: Exception) {
            logger.error("Error occurred during repayment push notification batch job", e)
            PushBatchResult(
                success = false,
                reminderCount = 0,
                todayCount = 0,
                overdueCount = 0,
                errorMessage = e.message,
            )
        }
    }

    suspend fun sendReminderPushes(targetDate: LocalDate): Int {
        val schedules = repaymentScheduleRepository.findSchedulesForReminder(targetDate)
        logger.info("Found {} schedules for reminder push (D-2, target date: {})", schedules.size, targetDate)

        if (schedules.isEmpty()) {
            logger.info("No schedules found for reminder push")
            return 0
        }

        logger.info(
            "Processing schedules: {}",
            schedules.map { "scheduleId=${it.id}, transactionId=${it.transactionId}" },
        )

        return schedules.count { schedule ->
            val result = sendPushForSchedule(schedule, PushType.REMINDER)
            logger.info("Schedule {} push result: {}", schedule.id, if (result) "SUCCESS" else "FAILED")
            result
        }
    }

    suspend fun sendTodayPushes(today: LocalDate): Int {
        val schedules = repaymentScheduleRepository.findSchedulesForToday(today)
        logger.info("Found {} schedules for today push (D-Day, date: {})", schedules.size, today)

        return schedules.count { schedule ->
            sendPushForSchedule(schedule, PushType.TODAY)
        }
    }

    suspend fun sendOverduePushes(yesterday: LocalDate): Int {
        val schedules = repaymentScheduleRepository.findOverdueSchedules(yesterday)
        logger.info("Found {} overdue schedules for overdue push (D+1, scheduled_date: {})", schedules.size, yesterday)

        return schedules.count { schedule ->
            sendPushForSchedule(schedule, PushType.OVERDUE)
        }
    }

    @Suppress("LongMethod", "ReturnCount")
    private suspend fun sendPushForSchedule(
        schedule: RepaymentSchedule,
        pushType: PushType,
    ): Boolean {
        return try {
            logger.info(
                "Processing push for schedule {}: scheduled_date={}, status={}, type={}",
                schedule.id,
                schedule.scheduledDate,
                schedule.status,
                pushType,
            )

            val transaction =
                transactionRepository.findById(schedule.transactionId)
                    ?: return false.also {
                        logger.warn(
                            "Transaction not found for schedule {}: transactionId={}",
                            schedule.id,
                            schedule.transactionId,
                        )
                    }

            logger.info(
                "Found transaction {} for schedule {}: type={}, userId={}",
                transaction.id,
                schedule.id,
                transaction.transactionType,
                transaction.userId,
            )

            val user =
                userRepository.findById(transaction.userId)
                    ?: return false.also {
                        logger.warn(
                            "User not found for transaction {}: userId={}",
                            transaction.id,
                            transaction.userId,
                        )
                    }

            logger.info(
                "Found user {} for schedule {}: nickname={}, notificationAllowed={}",
                user.id,
                schedule.id,
                user.nickname,
                user.isNotificationAllowed,
            )

            if (!user.isNotificationAllowed) {
                logger.warn(
                    "User {} has notifications disabled, skipping push for schedule {}",
                    user.id,
                    schedule.id,
                )
                return false
            }

            val devices = userDeviceRepository.findByUserId(user.id!!).toList()
            logger.info("Found {} devices for user {}", devices.size, user.id)

            if (devices.isEmpty()) {
                logger.warn(
                    "No devices registered for user {}, skipping push for schedule {}",
                    user.id,
                    schedule.id,
                )
                return false
            }

            logger.info(
                "Device details: {}",
                devices.map { "deviceId=${it.deviceId}, hasExpoToken=${it.expoToken != null}" },
            )

            // Notification 레코드를 먼저 생성하여 notificationId 획득
            val notification =
                createNotificationRecord(
                    userId = user.id!!,
                    pushType = pushType,
                    transactionType = transaction.transactionType,
                    nickname = user.nickname,
                    counterpartName = transaction.counterpartName,
                    amount = schedule.scheduledAmount.toLong(),
                    transactionId = transaction.id!!,
                    scheduleId = schedule.id!!,
                )

            // 푸시 메시지에 notificationId 포함
            val messages =
                devices.mapNotNull { device ->
                    device.expoToken?.let { token ->
                        createPushMessage(
                            token = token,
                            pushType = pushType,
                            transactionType = transaction.transactionType,
                            nickname = user.nickname,
                            counterpartName = transaction.counterpartName,
                            amount = schedule.scheduledAmount.toLong(),
                            transactionId = transaction.id!!,
                            scheduleId = schedule.id!!,
                            notificationId = notification?.id,
                        )
                    }
                }

            logger.info("Created {} push messages for {} devices", messages.size, devices.size)

            if (messages.isEmpty()) {
                logger.warn(
                    "No valid expo tokens for user {}, skipping push for schedule {}",
                    user.id,
                    schedule.id,
                )
                return false
            }

            val result = expoPushService.sendToMultipleDevices(devices, messages)

            logger.info(
                "Push sent for schedule {} (type: {}): success={}, failure={}",
                schedule.id,
                pushType,
                result.successCount,
                result.failureCount,
            )

            result.successCount > 0
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception,
        ) {
            logger.error("Failed to send push for schedule {} (type: {})", schedule.id, pushType, e)
            false
        }
    }

    @Suppress("LongParameterList", "LongMethod")
    private fun createPushMessage(
        token: String,
        pushType: PushType,
        transactionType: TransactionType,
        nickname: String,
        counterpartName: String,
        amount: Long,
        transactionId: Long,
        scheduleId: Long,
        notificationId: Long? = null,
    ): ExpoPushMessage {
        return when (pushType) {
            PushType.REMINDER -> {
                if (transactionType == TransactionType.LEND) {
                    pushTemplateService.createRepaymentReminderPayee(
                        expoToken = token,
                        nickname = nickname,
                        payerName = counterpartName,
                        amount = amount,
                        transactionId = transactionId,
                        scheduleId = scheduleId,
                        notificationId = notificationId,
                    )
                } else {
                    pushTemplateService.createRepaymentReminderPayer(
                        expoToken = token,
                        nickname = nickname,
                        payeeName = counterpartName,
                        amount = amount,
                        transactionId = transactionId,
                        scheduleId = scheduleId,
                        notificationId = notificationId,
                    )
                }
            }
            PushType.TODAY -> {
                if (transactionType == TransactionType.LEND) {
                    pushTemplateService.createRepaymentTodayPayee(
                        expoToken = token,
                        nickname = nickname,
                        payerName = counterpartName,
                        amount = amount,
                        transactionId = transactionId,
                        scheduleId = scheduleId,
                        notificationId = notificationId,
                    )
                } else {
                    pushTemplateService.createRepaymentTodayPayer(
                        expoToken = token,
                        nickname = nickname,
                        payeeName = counterpartName,
                        amount = amount,
                        transactionId = transactionId,
                        scheduleId = scheduleId,
                        notificationId = notificationId,
                    )
                }
            }
            PushType.OVERDUE -> {
                if (transactionType == TransactionType.LEND) {
                    pushTemplateService.createRepaymentOverduePayee(
                        expoToken = token,
                        nickname = nickname,
                        amount = amount,
                        transactionId = transactionId,
                        scheduleId = scheduleId,
                        notificationId = notificationId,
                    )
                } else {
                    pushTemplateService.createRepaymentOverduePayer(
                        expoToken = token,
                        nickname = nickname,
                        amount = amount,
                        transactionId = transactionId,
                        scheduleId = scheduleId,
                        notificationId = notificationId,
                    )
                }
            }
        }
    }

    /**
     * Notification 레코드 생성 (푸시 전송 전에 호출)
     * @return 생성된 Notification 객체 (notificationId 포함)
     */
    @Suppress("LongParameterList", "TooGenericExceptionCaught", "UnusedParameter")
    private suspend fun createNotificationRecord(
        userId: Long,
        pushType: PushType,
        transactionType: TransactionType,
        nickname: String,
        counterpartName: String,
        amount: Long,
        transactionId: Long,
        scheduleId: Long,
    ): com.bos.backend.domain.notification.entity.Notification? {
        return try {
            // PushTemplateType 결정
            val templateType = determinePushTemplateType(pushType, transactionType)

            // 템플릿으로 메시지 생성
            val title = templateType.titleTemplate
            val body = formatPushBody(templateType, nickname, counterpartName, amount)

            // NotificationCategory 매핑
            val category = templateType.toNotificationCategory()

            // Notification 레코드 생성
            val notification =
                notificationService.createNotificationRecord(
                    userId = userId,
                    title = title,
                    content = body,
                    category = category,
                    deepLink = null,
                )

            logger.info(
                "Created notification record: id={}, userId={}, scheduleId={}, category={}",
                notification.id,
                userId,
                scheduleId,
                category,
            )

            notification
        } catch (e: Exception) {
            logger.error(
                "Failed to create notification record for userId={}, scheduleId={}",
                userId,
                scheduleId,
                e,
            )
            null
        }
    }

    /**
     * PushType과 TransactionType으로부터 PushTemplateType 결정
     */
    private fun determinePushTemplateType(
        pushType: PushType,
        transactionType: TransactionType,
    ): PushTemplateType {
        return when (pushType) {
            PushType.REMINDER -> {
                if (transactionType == TransactionType.LEND) {
                    PushTemplateType.REPAYMENT_REMINDER_PAYEE
                } else {
                    PushTemplateType.REPAYMENT_REMINDER_PAYER
                }
            }
            PushType.TODAY -> {
                if (transactionType == TransactionType.LEND) {
                    PushTemplateType.REPAYMENT_TODAY_PAYEE
                } else {
                    PushTemplateType.REPAYMENT_TODAY_PAYER
                }
            }
            PushType.OVERDUE -> {
                if (transactionType == TransactionType.LEND) {
                    PushTemplateType.REPAYMENT_OVERDUE_PAYEE
                } else {
                    PushTemplateType.REPAYMENT_OVERDUE_PAYER
                }
            }
        }
    }

    /**
     * 푸시 메시지 본문 포맷팅
     */
    private fun formatPushBody(
        templateType: PushTemplateType,
        nickname: String,
        counterpartName: String,
        amount: Long,
    ): String {
        val formattedAmount = String.format(Locale.KOREA, "%,d", amount)
        return templateType.bodyTemplate
            .replace("{nickname}", nickname)
            .replace("{payeeName}", counterpartName)
            .replace("{payerName}", counterpartName)
            .replace("{amount}", formattedAmount)
    }
}

enum class PushType {
    REMINDER,
    TODAY,
    OVERDUE,
}

data class PushBatchResult(
    val success: Boolean,
    val reminderCount: Int,
    val todayCount: Int,
    val overdueCount: Int,
    val errorMessage: String? = null,
)
