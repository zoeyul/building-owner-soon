package com.bos.backend.application.user

import com.bos.backend.domain.user.entity.UserDevice
import com.bos.backend.domain.user.enum.Platform
import com.bos.backend.domain.user.repository.UserDeviceRepository
import com.bos.backend.presentation.user.dto.FcmTokenUpdateRequestDTO
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.time.Instant

class UserDeviceServiceTest :
    DescribeSpec({
        val userDeviceRepository = mockk<UserDeviceRepository>()
        val userDeviceService = UserDeviceService(userDeviceRepository)

        beforeEach {
            clearMocks(userDeviceRepository)
        }

        describe("updateFcmToken") {
            context("기존 디바이스가 있고 expoToken이 null일 때") {
                it("디바이스를 비활성화해야 한다") {
                    // given
                    val userId = 1L
                    val deviceId = "test-device-123"
                    val existingDevice =
                        UserDevice(
                            id = 100L,
                            userId = userId,
                            deviceId = deviceId,
                            fcmToken = "old-fcm-token",
                            expoToken = "old-expo-token",
                            platform = Platform.IOS,
                            deviceName = "iPhone 13",
                            isActive = true,
                            createdAt = Instant.now().minusSeconds(3600),
                            updatedAt = Instant.now().minusSeconds(3600),
                        )

                    // null로 비활성화 요청
                    val request =
                        FcmTokenUpdateRequestDTO(
                            deviceId = deviceId,
                            fcmToken = "new-fcm-token",
                            expoToken = null,
                            platform = null,
                            deviceName = null,
                        )

                    val savedDeviceSlot = slot<UserDevice>()

                    coEvery { userDeviceRepository.findByUserIdAndDeviceId(userId, deviceId) } returns existingDevice
                    coEvery { userDeviceRepository.save(capture(savedDeviceSlot)) } answers { savedDeviceSlot.captured }

                    // when
                    userDeviceService.updateFcmToken(userId, request)

                    // then
                    coVerify(exactly = 1) { userDeviceRepository.findByUserIdAndDeviceId(userId, deviceId) }
                    coVerify(exactly = 1) { userDeviceRepository.save(any()) }

                    val savedDevice = savedDeviceSlot.captured
                    savedDevice.isActive shouldBe false // 비활성화되어야 함
                    savedDevice.fcmToken shouldBe "new-fcm-token"
                    savedDevice.expoToken shouldBe null
                    savedDevice.platform shouldBe Platform.IOS // 기존 플랫폼 유지
                    savedDevice.deviceName shouldBe "iPhone 13" // 기존 디바이스명 유지
                }
            }

            context("기존 디바이스가 있고 expoToken이 있을 때") {
                it("디바이스를 활성화하고 토큰을 업데이트해야 한다") {
                    // given
                    val userId = 1L
                    val deviceId = "test-device-123"
                    // 기존에는 비활성화 상태
                    val existingDevice =
                        UserDevice(
                            id = 100L,
                            userId = userId,
                            deviceId = deviceId,
                            fcmToken = "old-fcm-token",
                            expoToken = null,
                            platform = Platform.ANDROID,
                            deviceName = "Galaxy S21",
                            isActive = false,
                            createdAt = Instant.now().minusSeconds(3600),
                            updatedAt = Instant.now().minusSeconds(3600),
                        )

                    // 새 토큰으로 활성화
                    val request =
                        FcmTokenUpdateRequestDTO(
                            deviceId = deviceId,
                            fcmToken = "new-fcm-token",
                            expoToken = "new-expo-token",
                            platform = null,
                            deviceName = null,
                        )

                    val savedDeviceSlot = slot<UserDevice>()

                    coEvery { userDeviceRepository.findByUserIdAndDeviceId(userId, deviceId) } returns existingDevice
                    coEvery { userDeviceRepository.save(capture(savedDeviceSlot)) } answers { savedDeviceSlot.captured }

                    // when
                    userDeviceService.updateFcmToken(userId, request)

                    // then
                    coVerify(exactly = 1) { userDeviceRepository.findByUserIdAndDeviceId(userId, deviceId) }
                    coVerify(exactly = 1) { userDeviceRepository.save(any()) }

                    val savedDevice = savedDeviceSlot.captured
                    savedDevice.isActive shouldBe true // 활성화되어야 함
                    savedDevice.fcmToken shouldBe "new-fcm-token"
                    savedDevice.expoToken shouldBe "new-expo-token"
                    savedDevice.platform shouldBe Platform.ANDROID
                    savedDevice.deviceName shouldBe "Galaxy S21"
                }
            }

            context("신규 디바이스이고 expoToken이 null일 때") {
                it("비활성 상태로 디바이스를 등록해야 한다") {
                    // given
                    val userId = 1L
                    val deviceId = "new-device-456"
                    // null로 비활성 상태 등록
                    val request =
                        FcmTokenUpdateRequestDTO(
                            deviceId = deviceId,
                            fcmToken = "fcm-token",
                            expoToken = null,
                            platform = Platform.IOS,
                            deviceName = "iPad Pro",
                        )

                    val savedDeviceSlot = slot<UserDevice>()

                    coEvery { userDeviceRepository.findByUserIdAndDeviceId(userId, deviceId) } returns null
                    coEvery { userDeviceRepository.save(capture(savedDeviceSlot)) } answers { savedDeviceSlot.captured }

                    // when
                    userDeviceService.updateFcmToken(userId, request)

                    // then
                    coVerify(exactly = 1) { userDeviceRepository.findByUserIdAndDeviceId(userId, deviceId) }
                    coVerify(exactly = 1) { userDeviceRepository.save(any()) }

                    val savedDevice = savedDeviceSlot.captured
                    savedDevice.userId shouldBe userId
                    savedDevice.deviceId shouldBe deviceId
                    savedDevice.fcmToken shouldBe "fcm-token"
                    savedDevice.expoToken shouldBe null
                    savedDevice.platform shouldBe Platform.IOS
                    savedDevice.deviceName shouldBe "iPad Pro"
                    savedDevice.isActive shouldBe false // 비활성 상태로 등록
                }
            }

            context("신규 디바이스이고 expoToken이 있을 때") {
                it("활성 상태로 디바이스를 등록해야 한다") {
                    // given
                    val userId = 1L
                    val deviceId = "new-device-789"
                    // 정상적으로 활성 상태 등록
                    val request =
                        FcmTokenUpdateRequestDTO(
                            deviceId = deviceId,
                            fcmToken = "fcm-token",
                            expoToken = "expo-token",
                            platform = Platform.ANDROID,
                            deviceName = "Pixel 6",
                        )

                    val savedDeviceSlot = slot<UserDevice>()

                    coEvery { userDeviceRepository.findByUserIdAndDeviceId(userId, deviceId) } returns null
                    coEvery { userDeviceRepository.save(capture(savedDeviceSlot)) } answers { savedDeviceSlot.captured }

                    // when
                    userDeviceService.updateFcmToken(userId, request)

                    // then
                    coVerify(exactly = 1) { userDeviceRepository.findByUserIdAndDeviceId(userId, deviceId) }
                    coVerify(exactly = 1) { userDeviceRepository.save(any()) }

                    val savedDevice = savedDeviceSlot.captured
                    savedDevice.userId shouldBe userId
                    savedDevice.deviceId shouldBe deviceId
                    savedDevice.fcmToken shouldBe "fcm-token"
                    savedDevice.expoToken shouldBe "expo-token"
                    savedDevice.platform shouldBe Platform.ANDROID
                    savedDevice.deviceName shouldBe "Pixel 6"
                    savedDevice.isActive shouldBe true // 활성 상태로 등록
                }
            }
        }
    })
