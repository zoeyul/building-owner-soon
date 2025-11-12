package com.bos.backend.application.push

import com.bos.backend.domain.push.ExpoPushMessage
import com.bos.backend.domain.push.ExpoPushResponse
import com.bos.backend.domain.push.ExpoPushTicket
import com.bos.backend.domain.user.entity.UserDevice
import com.bos.backend.domain.user.enum.Platform
import com.bos.backend.domain.user.repository.UserDeviceRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

class ExpoPushServiceTest : StringSpec({
    val webClient = mockk<WebClient>()
    val userDeviceRepository = mockk<UserDeviceRepository>()
    val expoPushService = ExpoPushService(webClient, userDeviceRepository)

    val requestBodyUriSpec = mockk<WebClient.RequestBodyUriSpec>()
    val requestBodySpec = mockk<WebClient.RequestBodySpec>()
    val requestHeadersSpec = mockk<WebClient.RequestHeadersSpec<*>>()
    val responseSpec = mockk<WebClient.ResponseSpec>()

    beforeEach {
        clearMocks(
            webClient,
            requestBodyUriSpec,
            requestBodySpec,
            requestHeadersSpec,
            responseSpec,
            userDeviceRepository,
        )
        coEvery { webClient.post() } returns requestBodyUriSpec
        coEvery { requestBodyUriSpec.uri(any<String>()) } returns requestBodySpec
        coEvery { requestBodySpec.contentType(any()) } returns requestBodySpec
        coEvery { requestBodySpec.bodyValue(any()) } returns requestHeadersSpec
        coEvery { requestHeadersSpec.retrieve() } returns responseSpec
    }

    "유효한 Expo 토큰으로 단일 디바이스에 푸시를 전송할 수 있다" {
        // Given
        val token = "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]"
        val message =
            ExpoPushMessage(
                to = token,
                title = "테스트 알림",
                body = "테스트 내용",
            )

        val successResponse =
            ExpoPushResponse(
                data =
                    listOf(
                        ExpoPushTicket(
                            status = "ok",
                            id = "test-ticket-id",
                        ),
                    ),
            )

        coEvery { responseSpec.bodyToMono(ExpoPushResponse::class.java) } returns Mono.just(successResponse)

        // When
        val result = expoPushService.sendToDevice(token, message)

        // Then
        result shouldBe true
        coVerify { webClient.post() }
    }

    "유효하지 않은 Expo 토큰 형식은 전송하지 않는다" {
        // Given
        val invalidToken = "InvalidToken123"
        val message =
            ExpoPushMessage(
                to = invalidToken,
                title = "테스트 알림",
                body = "테스트 내용",
            )

        // When
        val result = expoPushService.sendToDevice(invalidToken, message)

        // Then
        result shouldBe false
        coVerify(exactly = 0) { webClient.post() }
    }

    "Expo API에서 에러 응답을 받으면 실패로 처리한다" {
        // Given
        val token = "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]"
        val message =
            ExpoPushMessage(
                to = token,
                title = "테스트 알림",
                body = "테스트 내용",
            )

        val errorResponse =
            ExpoPushResponse(
                data =
                    listOf(
                        ExpoPushTicket(
                            status = "error",
                            message = "DeviceNotRegistered",
                        ),
                    ),
            )

        coEvery { responseSpec.bodyToMono(ExpoPushResponse::class.java) } returns Mono.just(errorResponse)

        // When
        val result = expoPushService.sendToDevice(token, message)

        // Then
        result shouldBe false
    }

    "여러 디바이스에 푸시를 전송할 수 있다" {
        // Given
        val devices =
            listOf(
                UserDevice(
                    id = 1L,
                    userId = 1L,
                    deviceId = "device-1",
                    fcmToken = "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx1]",
                    platform = Platform.IOS,
                ),
                UserDevice(
                    id = 2L,
                    userId = 2L,
                    deviceId = "device-2",
                    fcmToken = "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx2]",
                    platform = Platform.ANDROID,
                ),
            )

        val message =
            ExpoPushMessage(
                to = "",
                title = "테스트 알림",
                body = "테스트 내용",
            )

        val successResponse =
            ExpoPushResponse(
                data =
                    listOf(
                        ExpoPushTicket(
                            status = "ok",
                            id = "test-ticket-id",
                        ),
                    ),
            )

        coEvery { responseSpec.bodyToMono(ExpoPushResponse::class.java) } returns Mono.just(successResponse)

        // When
        val result = expoPushService.sendToMultipleDevices(devices, message)

        // Then
        result.successCount shouldBe 2
        result.failureCount shouldBe 0
        result.deletedTokens shouldBe emptyList()
    }

    "무효한 토큰은 자동으로 삭제한다" {
        // Given
        val invalidToken = "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]"
        val devices =
            listOf(
                UserDevice(
                    id = 1L,
                    userId = 1L,
                    deviceId = "device-1",
                    fcmToken = invalidToken,
                    platform = Platform.IOS,
                ),
            )

        val message =
            ExpoPushMessage(
                to = "",
                title = "테스트 알림",
                body = "테스트 내용",
            )

        val errorResponse =
            ExpoPushResponse(
                data =
                    listOf(
                        ExpoPushTicket(
                            status = "error",
                            message = "DeviceNotRegistered",
                        ),
                    ),
            )

        coEvery { responseSpec.bodyToMono(ExpoPushResponse::class.java) } returns Mono.just(errorResponse)
        coEvery { userDeviceRepository.deleteByFcmToken(invalidToken) } returns 1L

        // When
        val result = expoPushService.sendToMultipleDevices(devices, message)

        // Then
        result.successCount shouldBe 0
        result.failureCount shouldBe 1
        result.deletedTokens shouldBe listOf(invalidToken)
        coVerify { userDeviceRepository.deleteByFcmToken(invalidToken) }
    }

    "WebClient 예외가 발생하면 실패로 처리한다" {
        // Given
        val token = "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]"
        val message =
            ExpoPushMessage(
                to = token,
                title = "테스트 알림",
                body = "테스트 내용",
            )

        coEvery {
            responseSpec.bodyToMono(ExpoPushResponse::class.java)
        } throws WebClientResponseException.create(500, "Internal Server Error", HttpHeaders(), ByteArray(0), null)

        // When
        val result = expoPushService.sendToDevice(token, message)

        // Then
        result shouldBe false
    }

    "빈 디바이스 리스트로 전송하면 결과가 비어있다" {
        // Given
        val devices = emptyList<UserDevice>()
        val message =
            ExpoPushMessage(
                to = "",
                title = "테스트 알림",
                body = "테스트 내용",
            )

        // When
        val result = expoPushService.sendToMultipleDevices(devices, message)

        // Then
        result.successCount shouldBe 0
        result.failureCount shouldBe 0
        result.deletedTokens shouldBe emptyList()
    }
})
