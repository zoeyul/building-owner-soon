package com.bos.backend.application.auth.strategy

import com.bos.backend.domain.user.entity.User
import com.bos.backend.domain.user.entity.UserAuth
import com.bos.backend.domain.user.enum.ProviderType
import com.bos.backend.presentation.auth.dto.SignInRequestDTO
import com.bos.backend.presentation.auth.dto.SignUpRequestDTO

interface AuthStrategy {
    val providerType: ProviderType

    suspend fun signUp(request: SignUpRequestDTO): AuthResult

    suspend fun signIn(
        request: SignInRequestDTO,
        skipTokenValidation: Boolean = false,
    ): AuthResult
}

data class AuthResult(
    val user: User,
    val userAuth: UserAuth,
)
