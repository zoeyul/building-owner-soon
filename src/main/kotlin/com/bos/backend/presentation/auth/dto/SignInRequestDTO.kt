package com.bos.backend.presentation.auth.dto

import com.bos.backend.domain.user.enum.ProviderType
import jakarta.validation.constraints.NotNull

data class SignInRequestDTO(
    @field:NotNull
    val provider: ProviderType,
    val email: String?,
    val providerId: String? = null,
    val password: String? = null,
    val providerAccessToken: String? = null,
    val deviceId: String? = null,
)
