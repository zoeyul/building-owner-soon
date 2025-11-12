package com.bos.backend.application.user

import com.bos.backend.application.CustomException
import com.bos.backend.application.auth.AuthErrorCode
import com.bos.backend.application.builder.CharacterBuilder
import com.bos.backend.application.mapper.UserMapper
import com.bos.backend.domain.user.entity.UserAuthFixture
import com.bos.backend.domain.user.entity.UserFixture
import com.bos.backend.domain.user.repository.UserAuthRepository
import com.bos.backend.domain.user.repository.UserRepository
import com.bos.backend.presentation.user.dto.UserProfileResponseDTO
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.transaction.reactive.TransactionalOperator

class UserServiceTest :
    DescribeSpec({

        val userRepository = mockk<UserRepository>()
        val transactionalOperator = mockk<TransactionalOperator>()
        val userMapper = mockk<UserMapper>()
        val characterBuilder = mockk<CharacterBuilder>()
        val userAuthRepository = mockk<UserAuthRepository>()

        val userService =
            UserService(
                userRepository = userRepository,
                transactionalOperator = transactionalOperator,
                userMapper = userMapper,
                characterBuilder = characterBuilder,
                userAuthRepository = userAuthRepository,
            )

        describe("getUserProfile") {
            context("존재하는 유저를 조회할 때") {
                it("유저 프로필을 성공적으로 반환한다") {
                    // given
                    val userId = 1L
                    val testUser = UserFixture.defaultUserFixture()
                    val testUserAuth = UserAuthFixture.defaultUserAuthFixture()
                    val expectedProfileDTO =
                        UserProfileResponseDTO(
                            id = testUser.id!!,
                            email = testUserAuth.email,
                            nickname = testUser.nickname,
                            provider = testUserAuth.providerType.value,
                            character = testUser.character,
                            isNotificationAllowed = testUser.isNotificationAllowed,
                            isMarketingAgreed = testUser.isMarketingAgreed,
                            createdAt = testUser.createdAt,
                            updatedAt = testUser.updatedAt,
                        )

                    coEvery { userRepository.findById(userId) } returns testUser
                    coEvery { userAuthRepository.findByUserId(userId) } returns testUserAuth
                    every { userMapper.toUserProfileDTO(testUser, testUserAuth) } returns expectedProfileDTO

                    // when
                    val result = userService.getUserProfile(userId)

                    // then
                    result shouldBe expectedProfileDTO
                    coVerify { userRepository.findById(userId) }
                    verify { userMapper.toUserProfileDTO(testUser, testUserAuth) }
                }
            }

            context("존재하지 않는 유저를 조회할 때") {
                it("CustomException을 발생시킨다") {
                    // given
                    val userId = 999L
                    coEvery { userRepository.findById(userId) } returns null

                    // when & then
                    shouldThrow<CustomException> {
                        userService.getUserProfile(userId)
                    }.errorCode shouldBe AuthErrorCode.USER_NOT_FOUND.name

                    coVerify { userRepository.findById(userId) }
                }
            }
        }
    })
