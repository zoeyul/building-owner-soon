package com.bos.backend.application.auth

import com.bos.backend.application.CustomException
import com.bos.backend.application.service.JwtService
import com.bos.backend.domain.auth.entity.RefreshToken
import com.bos.backend.domain.auth.repository.RefreshTokenRepository
import com.bos.backend.presentation.auth.dto.TokenRefreshRequestDTO
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant

class AuthServiceRefreshTokenTest : StringSpec({
    val jwtService = mockk<JwtService>()
    val refreshTokenRepository = mockk<RefreshTokenRepository>()
    val accessTokenExpiration = 3600L
    val refreshTokenExpiration = 2592000L // 30일

    val sut =
        AuthService(
            authStrategyResolver = mockk(),
            jwtService = jwtService,
            userAuthRepository = mockk(),
            userTermsAgreementRepository = mockk(),
            userRepository = mockk(),
            emailVerificationService = mockk(),
            refreshTokenRepository = refreshTokenRepository,
            accessTokenExpiration = accessTokenExpiration,
            refreshTokenExpiration = refreshTokenExpiration,
            userDeviceRepository = mockk(),
        )

    "refreshToken은 유효한 토큰으로 새로운 토큰을 발급한다" {
        val userId = 1L
        val refreshTokenValue = "valid.refresh.token"
        val tokenHash = "hashedRefreshToken"
        val newAccessToken = "new.access.token"
        val newRefreshToken = "new.refresh.token"

        val storedRefreshToken =
            RefreshToken(
                id = 1L,
                userId = userId,
                tokenHash = tokenHash,
                expiresAt = Instant.now().plusSeconds(refreshTokenExpiration),
            )

        coEvery { jwtService.validateTokenFormat(refreshTokenValue) } returns true
        coEvery { jwtService.hashToken(refreshTokenValue) } returns tokenHash
        coEvery { refreshTokenRepository.findByTokenHash(tokenHash) } returns storedRefreshToken
        coEvery { refreshTokenRepository.revokeByTokenHash(tokenHash) } returns Unit
        coEvery { jwtService.generateToken(userId.toString(), accessTokenExpiration) } returns newAccessToken
        coEvery { jwtService.generateToken(userId.toString(), refreshTokenExpiration) } returns newRefreshToken
        coEvery { jwtService.hashToken(newRefreshToken) } returns "newTokenHash"
        coEvery { refreshTokenRepository.save(any()) } returns mockk()

        val request = TokenRefreshRequestDTO(refreshTokenValue)
        val result = sut.refreshToken(request)

        result.accessToken shouldBe newAccessToken
        result.refreshToken shouldBe newRefreshToken

        coVerify { refreshTokenRepository.revokeByTokenHash(tokenHash) }
        coVerify { refreshTokenRepository.save(any()) }
    }

    "refreshToken은 유효하지 않은 refresh token 형식에 대해 예외를 발생시킨다" {
        val refreshTokenValue = "invalid.refresh.token"

        coEvery { jwtService.validateTokenFormat(refreshTokenValue) } returns false

        val request = TokenRefreshRequestDTO(refreshTokenValue)

        shouldThrow<CustomException> {
            sut.refreshToken(request)
        }.errorCode shouldBe AuthErrorCode.INVALID_TOKEN.toString()
    }

    "refreshToken은 존재하지 않는 refresh token에 대해 예외를 발생시킨다" {
        val refreshTokenValue = "nonexistent.refresh.token"
        val tokenHash = "nonexistentHash"

        coEvery { jwtService.validateTokenFormat(refreshTokenValue) } returns true
        coEvery { jwtService.hashToken(refreshTokenValue) } returns tokenHash
        coEvery { refreshTokenRepository.findByTokenHash(tokenHash) } returns null

        val request = TokenRefreshRequestDTO(refreshTokenValue)

        shouldThrow<CustomException> {
            sut.refreshToken(request)
        }.errorCode shouldBe AuthErrorCode.REFRESH_TOKEN_NOT_FOUND.toString()
    }

    "refreshToken은 만료된 refresh token에 대해 예외를 발생시킨다" {
        val userId = 1L
        val refreshTokenValue = "expired.refresh.token"
        val tokenHash = "expiredTokenHash"

        // 1시간 전 만료
        val expiredRefreshToken =
            RefreshToken(
                id = 1L,
                userId = userId,
                tokenHash = tokenHash,
                expiresAt = Instant.now().minusSeconds(3600),
            )

        coEvery { jwtService.validateTokenFormat(refreshTokenValue) } returns true
        coEvery { jwtService.hashToken(refreshTokenValue) } returns tokenHash
        coEvery { refreshTokenRepository.findByTokenHash(tokenHash) } returns expiredRefreshToken

        val request = TokenRefreshRequestDTO(refreshTokenValue)

        shouldThrow<CustomException> {
            sut.refreshToken(request)
        }.errorCode shouldBe AuthErrorCode.REFRESH_TOKEN_EXPIRED.toString()
    }

    "refreshToken은 폐기된 refresh token에 대해 예외를 발생시킨다" {
        val userId = 1L
        val refreshTokenValue = "revoked.refresh.token"
        val tokenHash = "revokedTokenHash"

        // 1시간 전 폐기
        val revokedRefreshToken =
            RefreshToken(
                id = 1L,
                userId = userId,
                tokenHash = tokenHash,
                expiresAt = Instant.now().plusSeconds(refreshTokenExpiration),
                revokedAt = Instant.now().minusSeconds(3600),
            )

        coEvery { jwtService.validateTokenFormat(refreshTokenValue) } returns true
        coEvery { jwtService.hashToken(refreshTokenValue) } returns tokenHash
        coEvery { refreshTokenRepository.findByTokenHash(tokenHash) } returns revokedRefreshToken

        val request = TokenRefreshRequestDTO(refreshTokenValue)

        shouldThrow<CustomException> {
            sut.refreshToken(request)
        }.errorCode shouldBe AuthErrorCode.REFRESH_TOKEN_REVOKED.toString()
    }
})
