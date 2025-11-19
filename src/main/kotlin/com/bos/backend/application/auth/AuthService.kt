package com.bos.backend.application.auth

import com.bos.backend.application.CommonErrorCode
import com.bos.backend.application.CustomException
import com.bos.backend.application.auth.strategy.AuthStrategyResolver
import com.bos.backend.application.service.EmailVerificationService
import com.bos.backend.application.service.JwtService
import com.bos.backend.domain.auth.entity.RefreshToken
import com.bos.backend.domain.auth.enums.EmailVerificationType
import com.bos.backend.domain.auth.repository.RefreshTokenRepository
import com.bos.backend.domain.term.entity.UserTermAgreement
import com.bos.backend.domain.term.repository.UserTermAgreementRepository
import com.bos.backend.domain.user.enum.ProviderType
import com.bos.backend.domain.user.repository.UserAuthRepository
import com.bos.backend.domain.user.repository.UserRepository
import com.bos.backend.infrastructure.util.PasswordPolicy
import com.bos.backend.presentation.auth.dto.CheckEmailResponse
import com.bos.backend.presentation.auth.dto.CommonSignResponseDTO
import com.bos.backend.presentation.auth.dto.EmailVerificationCheckDTO
import com.bos.backend.presentation.auth.dto.EmailVerificationRequestDTO
import com.bos.backend.presentation.auth.dto.PasswordChangeRequestDTO
import com.bos.backend.presentation.auth.dto.PasswordResetRequestDTO
import com.bos.backend.presentation.auth.dto.SignInRequestDTO
import com.bos.backend.presentation.auth.dto.SignUpRequestDTO
import com.bos.backend.presentation.auth.dto.TokenRefreshRequestDTO
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
@Suppress("LongParameterList", "TooManyFunctions")
class AuthService(
    private val authStrategyResolver: AuthStrategyResolver,
    private val jwtService: JwtService,
    private val userAuthRepository: UserAuthRepository,
    private val userTermsAgreementRepository: UserTermAgreementRepository,
    private val userRepository: UserRepository,
    private val emailVerificationService: EmailVerificationService,
    private val refreshTokenRepository: RefreshTokenRepository,
    @Value("\${application.jwt.access-token-expiration}") private val accessTokenExpiration: Long,
    @Value("\${application.jwt.refresh-token-expiration}") private val refreshTokenExpiration: Long,
) {
    suspend fun signUp(request: SignUpRequestDTO): CommonSignResponseDTO {
        val strategy = authStrategyResolver.resolve(request.provider)
        val authResult = strategy.signUp(request)

        // 약관 동의 저장
        // TODO: 약관 validation
        request.termsAgreements
            .filter { it.isAgree }
            .map { term ->
                UserTermAgreement(
                    userId = authResult.user.id!!,
                    termsId = term.id,
                )
            }.let { agreements ->
                userTermsAgreementRepository.saveAll(agreements)
            }

        val accessToken = jwtService.generateToken(authResult.user.id.toString(), accessTokenExpiration)
        val refreshToken = jwtService.generateToken(authResult.user.id.toString(), refreshTokenExpiration)

        // RefreshToken 저장
        saveRefreshToken(authResult.user.id!!, refreshToken)

        return CommonSignResponseDTO(accessToken, refreshToken)
    }

    suspend fun signIn(request: SignInRequestDTO): CommonSignResponseDTO {
        val strategy = authStrategyResolver.resolve(request.provider)
        val authResult = strategy.signIn(request)

        if (authResult.user.isDeleted()) {
            throw CustomException(AuthErrorCode.USER_NOT_FOUND)
        }

        val accessToken = jwtService.generateToken(authResult.user.id.toString(), accessTokenExpiration)
        val refreshToken = jwtService.generateToken(authResult.user.id.toString(), refreshTokenExpiration)

        // RefreshToken 저장
        saveRefreshToken(authResult.user.id!!, refreshToken)

        return CommonSignResponseDTO(accessToken, refreshToken)
    }

    @Suppress("ThrowsCount")
    suspend fun sendVerificationEmail(request: EmailVerificationRequestDTO) {
        if (request.type !in EmailVerificationType.entries) {
            throw CustomException(CommonErrorCode.INVALID_PARAMETER)
        }

        when (request.type) {
            EmailVerificationType.SIGNUP -> {
                if (emailVerificationService.isEmailDuplicated(request.email)) {
                    throw CustomException(AuthErrorCode.EMAIL_DUPLICATE)
                }
            }
            EmailVerificationType.PASSWORD_RESET -> {
                if (!userAuthRepository.existsByEmail(request.email)) {
                    throw CustomException(AuthErrorCode.USER_NOT_FOUND)
                }
            }
        }
        emailVerificationService.sendVerificationEmail(request.email, request.type)
    }

    suspend fun verifyCode(request: EmailVerificationCheckDTO) {
        if (emailVerificationService.isVerificationCodeExpired(request.email, request.type)) {
            throw CustomException(AuthErrorCode.EMAIL_VERIFICATION_CODE_EXPIRED)
        }
        if (!emailVerificationService.isVerificationCodeMatched(request.email, request.code, request.type)) {
            throw CustomException(AuthErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH)
        }
        emailVerificationService.verifyCode(request.email, request.code, request.type)
    }

    suspend fun resendVerificationEmail(request: EmailVerificationRequestDTO) {
        if (!emailVerificationService.isResendAllowed(request.email, request.type)) {
            throw CustomException(CommonErrorCode.TOO_MANY_REQUESTS)
        }
        emailVerificationService.sendVerificationEmail(request.email, request.type)
    }

    suspend fun isBosEmailUserAbsent(email: String): CheckEmailResponse {
        val userAuth =
            userAuthRepository.findByEmailAndProviderType(email, providerType = ProviderType.BOS.value)

        val user = userAuth?.let { userRepository.findById(it.userId) }

        if (userAuth == null || user == null || user.isDeleted()) {
            throw CustomException(AuthErrorCode.USER_NOT_FOUND)
        }

        return CheckEmailResponse(
            email = userAuth.email!!,
            isExist = true,
            provider = userAuth.providerType,
        )
    }

    suspend fun resetPassword(request: PasswordResetRequestDTO) {
        if (!userAuthRepository.existsByEmail(request.email)) {
            throw CustomException(AuthErrorCode.USER_NOT_FOUND)
        }

        if (!PasswordPolicy.isValidPassword(request.newPassword)) {
            throw CustomException(AuthErrorCode.PASSWORD_POLICY_VIOLATION)
        }

        userAuthRepository.resetPassword(request.email, request.newPassword)
    }

    @Suppress("ThrowsCount")
    suspend fun changePassword(
        userId: Long,
        request: PasswordChangeRequestDTO,
    ) {
        val userAuth =
            userAuthRepository.findByUserId(userId)
                ?: throw CustomException(AuthErrorCode.USER_NOT_FOUND)

        if (!userAuthRepository.verifyPassword(userAuth.email!!, request.currentPassword)) {
            throw CustomException(AuthErrorCode.INVALID_PASSWORD)
        }

        if (!PasswordPolicy.isValidPassword(request.newPassword)) {
            throw CustomException(AuthErrorCode.PASSWORD_POLICY_VIOLATION)
        }

        if (request.currentPassword == request.newPassword) {
            throw CustomException(AuthErrorCode.SAME_PASSWORD)
        }

        userAuthRepository.updatePassword(userId, request.newPassword)
    }

    suspend fun deleteById(userId: Long) {
        val user = userRepository.findById(userId)

        if (user == null || user.isDeleted()) {
            throw CustomException(AuthErrorCode.USER_NOT_FOUND)
        }

        // user_auth 테이블에서 하드 삭제
        userAuthRepository.deleteByUserId(userId)

        // user 테이블에서 소프트 삭제 (deleted_at 설정)
        userRepository.deleteById(userId)
    }

    @Suppress("ThrowsCount")
    suspend fun refreshToken(request: TokenRefreshRequestDTO): CommonSignResponseDTO {
        // Refresh Token 유효성 검증
        if (!jwtService.validateTokenFormat(request.refreshToken)) {
            throw CustomException(AuthErrorCode.INVALID_TOKEN)
        }

        val tokenHash = jwtService.hashToken(request.refreshToken)
        val storedRefreshToken =
            refreshTokenRepository.findByTokenHash(tokenHash)
                ?: throw CustomException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND)

        if (storedRefreshToken.isExpired()) {
            throw CustomException(AuthErrorCode.REFRESH_TOKEN_EXPIRED)
        }

        if (storedRefreshToken.isRevoked()) {
            throw CustomException(AuthErrorCode.REFRESH_TOKEN_REVOKED)
        }

        val userId = storedRefreshToken.userId

        refreshTokenRepository.revokeByTokenHash(tokenHash)

        val newAccessToken = jwtService.generateToken(userId.toString(), accessTokenExpiration)
        val newRefreshToken = jwtService.generateToken(userId.toString(), refreshTokenExpiration)

        saveRefreshToken(userId, newRefreshToken)

        return CommonSignResponseDTO(newAccessToken, newRefreshToken)
    }

    private suspend fun saveRefreshToken(
        userId: Long,
        refreshToken: String,
    ) {
        val tokenHash = jwtService.hashToken(refreshToken)
        val expiresAt = Instant.now().plusSeconds(refreshTokenExpiration)

        refreshTokenRepository.save(
            RefreshToken(
                userId = userId,
                tokenHash = tokenHash,
                expiresAt = expiresAt,
            ),
        )
    }
}
