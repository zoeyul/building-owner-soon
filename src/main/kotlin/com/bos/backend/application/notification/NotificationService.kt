package com.bos.backend.application.notification

import com.bos.backend.application.mapper.NotificationMapper
import com.bos.backend.application.push.ExpoPushService
import com.bos.backend.domain.notification.entity.Notification
import com.bos.backend.domain.notification.enums.NotificationCategory
import com.bos.backend.domain.notification.repository.NotificationRepository
import com.bos.backend.domain.push.ExpoPushMessage
import com.bos.backend.domain.user.repository.UserDeviceRepository
import com.bos.backend.presentation.notification.dto.MarkAsReadResponseDTO
import com.bos.backend.presentation.notification.dto.NotificationResponseDTO
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val notificationMapper: NotificationMapper,
    private val expoPushService: ExpoPushService,
    private val userDeviceRepository: UserDeviceRepository,
) {
    private val logger = LoggerFactory.getLogger(NotificationService::class.java)

    suspend fun getNotifications(
        userId: Long,
        unreadOnly: Boolean,
    ): List<NotificationResponseDTO> {
        val notifications =
            if (unreadOnly) {
                notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId)
            } else {
                notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
            }

        return notifications
            .map { notificationMapper.toNotificationResponseDTO(it) }
            .toList()
    }

    suspend fun markAsRead(
        userId: Long,
        notificationIds: List<Long>,
    ): MarkAsReadResponseDTO {
        val notifications = notificationRepository.findByUserIdAndIdIn(userId, notificationIds)

        val updatedNotifications =
            notifications.map { notification ->
                notification.markAsRead()
            }

        updatedNotifications.forEach { notification ->
            notificationRepository.save(notification)
        }

        return MarkAsReadResponseDTO(
            processedIds = notificationIds,
        )
    }

    suspend fun getUnreadCount(userId: Long): Long {
        return notificationRepository.countByUserIdAndIsReadFalse(userId)
    }

    suspend fun createNotification(
        userId: Long,
        title: String,
        content: String,
        category: NotificationCategory,
        deepLink: String?,
    ): Notification {
        val notification =
            Notification(
                userId = userId,
                title = title,
                content = content,
                category = category,
                deepLink = deepLink,
            )

        val savedNotification = notificationRepository.save(notification)
        sendPushNotification(userId, title, content, deepLink)

        return savedNotification
    }

    private suspend fun sendPushNotification(
        userId: Long,
        title: String,
        content: String,
        @Suppress("UnusedParameter") deepLink: String?,
    ) {
        try {
            val devices = userDeviceRepository.findByUserId(userId).toList()

            if (devices.isEmpty()) {
                logger.info("푸시 알림 전송 건너뜀: userId=$userId, 등록된 디바이스 없음")
                return
            }

            // Expo 푸시 메시지 생성
            val messages =
                devices.mapNotNull { device ->
                    device.expoToken?.let { token ->
                        ExpoPushMessage.createSimple(
                            token = token,
                            title = title,
                            body = content,
                        )
                    }
                }

            if (messages.isEmpty()) {
                logger.info("푸시 알림 전송 건너뜀: userId=$userId, Expo 토큰이 없음")
                return
            }

            val result = expoPushService.sendToMultipleDevices(devices, messages)

            logger.info(
                "푸시 알림 전송 완료: userId=$userId, " +
                    "성공=${result.successCount}, 실패=${result.failureCount}, " +
                    "삭제된 토큰=${result.deletedTokens.size}",
            )
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception,
        ) {
            logger.error("푸시 알림 전송 중 예외 발생: userId=$userId", e)
        }
    }

    suspend fun deleteExpiredNotifications(): Long {
        return notificationRepository.deleteByExpiresAtBefore(Instant.now())
    }
}
