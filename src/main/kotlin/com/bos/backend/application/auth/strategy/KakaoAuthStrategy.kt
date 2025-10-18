package com.bos.backend.application.auth.strategy

import com.bos.backend.application.CustomException
import com.bos.backend.application.auth.AuthErrorCode
import com.bos.backend.application.service.CharacterAssetService
import com.bos.backend.domain.user.entity.User
import com.bos.backend.domain.user.entity.UserAuth
import com.bos.backend.domain.user.enum.ProviderType
import com.bos.backend.domain.user.factory.CharacterFactory
import com.bos.backend.domain.user.repository.UserAuthRepository
import com.bos.backend.domain.user.repository.UserRepository
import com.bos.backend.infrastructure.external.KakaoApiService
import com.bos.backend.presentation.auth.dto.SignInRequestDTO
import com.bos.backend.presentation.auth.dto.SignUpRequestDTO
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class KakaoAuthStrategy(
    private val userRepository: UserRepository,
    private val userAuthRepository: UserAuthRepository,
    private val kakaoApiService: KakaoApiService,
    private val characterAssetService: CharacterAssetService,
) : AuthStrategy {
    private val logger = LoggerFactory.getLogger(KakaoAuthStrategy::class.java)
    override val providerType: ProviderType = ProviderType.KAKAO

    override suspend fun signUp(request: SignUpRequestDTO): AuthResult {
        requireNotNull(request.providerId) { "Provider ID is required for Kakao signup" }
        requireNotNull(request.providerAccessToken) { "Provider access token is required for Kakao signup" }

        // 카카오 토큰 검증
        require(validateProviderToken(request.providerAccessToken, request.providerId)) {
            "Invalid Kakao token or user info mismatch"
        }

        // 이미 가입된 사용자인지 확인
        require(userAuthRepository.findByProviderIdAndProviderType(request.providerId, providerType.value) == null) {
            "User already exists with email: ${request.email}"
        }

        // 사용자 생성
        val user =
            userRepository.save(
                User(
                    nickname = "임시 닉네임",
                    character = CharacterFactory.createDefaultCharacter(characterAssetService),
                    isNotificationAllowed = false,
                ),
            )

        // 인증 정보 저장
        val userAuth =
            userAuthRepository.save(
                // TODO: providerType에 따른 생성 제어 방법 고민
                UserAuth(
                    userId = user.id!!,
                    _providerType = providerType.value,
                    providerId = request.providerId,
                    email = request.email,
                ),
            )

        return AuthResult(user, userAuth)
    }

    override suspend fun signIn(request: SignInRequestDTO): AuthResult {
        requireNotNull(request.providerId) { "Provider ID is required for Kakao signin" }
        requireNotNull(request.providerAccessToken) { "Provider access token is required for Kakao signin" }

        // 카카오 토큰 검증
        require(validateProviderToken(request.providerAccessToken, request.providerId)) {
            "Invalid Kakao token or user info mismatch"
        }

        val userAuth =
            userAuthRepository.findByProviderIdAndProviderType(request.providerId, providerType.value)
                ?: throw CustomException(AuthErrorCode.USER_NOT_REGISTERED)

        val user = checkNotNull(userRepository.findById(userAuth.userId)) { "User not found" }

        // 로그인 시간 업데이트
        userAuthRepository.updateLastLoginAt(userAuth.id!!)

        return AuthResult(user, userAuth.copy(lastLoginAt = Instant.now()))
    }

    private suspend fun validateProviderToken(
        token: String,
        providerId: String?,
    ): Boolean =
        try {
            val kakaoUserInfo = kakaoApiService.getUserInfo(token)
            kakaoUserInfo.id == providerId
        } catch (e: RuntimeException) {
            logger.error("Kakao token validation failed: ${e.message}", e)
            false
        }
}
