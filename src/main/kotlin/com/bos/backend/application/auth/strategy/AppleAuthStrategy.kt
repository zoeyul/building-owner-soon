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
import com.bos.backend.domain.user.util.NicknameGenerator
import com.bos.backend.infrastructure.external.AppleApiService
import com.bos.backend.presentation.auth.dto.SignInRequestDTO
import com.bos.backend.presentation.auth.dto.SignUpRequestDTO
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class AppleAuthStrategy(
    private val userRepository: UserRepository,
    private val userAuthRepository: UserAuthRepository,
    private val appleApiService: AppleApiService,
    private val characterAssetService: CharacterAssetService,
) : AuthStrategy {
    private val logger = LoggerFactory.getLogger(AppleAuthStrategy::class.java)
    override val providerType: ProviderType = ProviderType.APPLE

    override suspend fun signUp(request: SignUpRequestDTO): AuthResult {
        requireNotNull(request.providerAccessToken) { "Identity token is required for Apple signup" }

        val appleUserInfo = validateAndExtractUserInfo(request.providerAccessToken)

        check(
            userAuthRepository.findByProviderIdAndProviderType(
                appleUserInfo.sub,
                providerType.value,
            ) == null,
        ) {
            "User already exists with Apple ID"
        }

        val user =
            userRepository.save(
                User(
                    nickname = NicknameGenerator.generateRandomNickname(),
                    character = CharacterFactory.createDefaultCharacter(characterAssetService),
                    isNotificationAllowed = true,
                ),
            )

        val userAuth =
            userAuthRepository.save(
                UserAuth(
                    userId = user.id!!,
                    _providerType = providerType.value,
                    providerId = appleUserInfo.sub,
                    email = appleUserInfo.email,
                ),
            )

        return AuthResult(user, userAuth)
    }

    override suspend fun signIn(request: SignInRequestDTO): AuthResult {
        requireNotNull(request.providerAccessToken) { "Identity token is required for Apple signin" }

        val appleUserInfo = validateAndExtractUserInfo(request.providerAccessToken)

        val userAuth =
            userAuthRepository.findByProviderIdAndProviderType(appleUserInfo.sub, providerType.value)
                ?: throw CustomException(AuthErrorCode.USER_NOT_REGISTERED)

        val user = checkNotNull(userRepository.findById(userAuth.userId)) { "User not found" }

        userAuthRepository.updateLastLoginAt(userAuth.id!!)

        return AuthResult(user, userAuth.copy(lastLoginAt = Instant.now()))
    }

    private suspend fun validateAndExtractUserInfo(identityToken: String) =
        try {
            appleApiService.verifyIdentityToken(identityToken)
        } catch (e: RuntimeException) {
            logger.error("Apple identity token validation failed: ${e.message}", e)
            throw IllegalArgumentException("Invalid Apple identity token")
        }
}
