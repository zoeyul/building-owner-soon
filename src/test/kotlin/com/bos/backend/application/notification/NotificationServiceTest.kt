package com.bos.backend.application.notification

import com.bos.backend.application.mapper.NotificationMapper
import com.bos.backend.application.push.FcmPushService
import com.bos.backend.domain.notification.entity.Notification
import com.bos.backend.domain.notification.enums.NotificationCategory
import com.bos.backend.domain.notification.repository.NotificationRepository
import com.bos.backend.domain.user.repository.UserDeviceRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf

class NotificationServiceTest : StringSpec({
    val notificationRepository = mockk<NotificationRepository>()
    val fcmPushService = mockk<FcmPushService>()
    val userDeviceRepository = mockk<UserDeviceRepository>()
    val notificationMapper = NotificationMapper.INSTANCE
    val notificationService =
        NotificationService(
            notificationRepository,
            notificationMapper,
            fcmPushService,
            userDeviceRepository,
        )

    "알림 목록을 조회할 수 있다" {
        // Given
        val userId = 1L
        val notification =
            Notification(
                id = 1L,
                userId = userId,
                title = "테스트 알림",
                content = "테스트 내용",
                category = NotificationCategory.GENERAL,
            )

        coEvery { notificationRepository.findByUserIdOrderByCreatedAtDesc(userId) } returns flowOf(notification)

        // When
        val result = notificationService.getNotifications(userId, false)

        // Then
        result shouldHaveSize 1
        result[0].id shouldBe 1L
        result[0].title shouldBe "테스트 알림"
        result[0].content shouldBe "테스트 내용"
        result[0].category shouldBe NotificationCategory.GENERAL
    }

    "읽지 않은 알림만 조회할 수 있다" {
        // Given
        val userId = 1L
        val unreadNotification =
            Notification(
                id = 1L,
                userId = userId,
                title = "읽지 않은 알림",
                content = "읽지 않은 내용",
                category = NotificationCategory.REPAYMENT_DUE,
            )

        coEvery {
            notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId)
        } returns flowOf(unreadNotification)

        // When
        val result = notificationService.getNotifications(userId, true)

        // Then
        result shouldHaveSize 1
        result[0].title shouldBe "읽지 않은 알림"
    }

    "알림을 읽음 처리할 수 있다" {
        // Given
        val userId = 1L
        val notificationIds = listOf(1L, 2L)
        val notifications =
            listOf(
                Notification(
                    id = 1L,
                    userId = userId,
                    title = "알림 1",
                    content = "내용 1",
                    category = NotificationCategory.GENERAL,
                ),
                Notification(
                    id = 2L,
                    userId = userId,
                    title = "알림 2",
                    content = "내용 2",
                    category = NotificationCategory.REPAYMENT_DUE,
                ),
            )

        coEvery { notificationRepository.findByUserIdAndIdIn(userId, notificationIds) } returns notifications
        coEvery { notificationRepository.save(any()) } returnsArgument 0

        // When
        val result = notificationService.markAsRead(userId, notificationIds)

        // Then
        result.processedIds shouldBe notificationIds
        coVerify(exactly = 2) { notificationRepository.save(any()) }
    }

    "읽지 않은 알림 수를 조회할 수 있다" {
        // Given
        val userId = 1L
        val expectedCount = 5L

        coEvery { notificationRepository.countByUserIdAndIsReadFalse(userId) } returns expectedCount

        // When
        val result = notificationService.getUnreadCount(userId)

        // Then
        result shouldBe expectedCount
    }

    "새 알림을 생성할 수 있다" {
        // Given
        val userId = 1L
        val title = "새 알림"
        val content = "새 알림 내용"
        val category = NotificationCategory.RECEIVABLE_DUE
        val deepLink = "/test-link"

        val expectedNotification =
            Notification(
                id = 1L,
                userId = userId,
                title = title,
                content = content,
                category = category,
                deepLink = deepLink,
            )

        coEvery { notificationRepository.save(any()) } returns expectedNotification

        // When
        val result = notificationService.createNotification(userId, title, content, category, deepLink)

        // Then
        result.id shouldBe 1L
        result.title shouldBe title
        result.content shouldBe content
        result.category shouldBe category
        result.deepLink shouldBe deepLink
    }

    "만료된 알림을 삭제할 수 있다" {
        // Given
        val deletedCount = 10L

        coEvery { notificationRepository.deleteByExpiresAtBefore(any()) } returns deletedCount

        // When
        val result = notificationService.deleteExpiredNotifications()

        // Then
        result shouldBe deletedCount
        coVerify { notificationRepository.deleteByExpiresAtBefore(any()) }
    }
})
