package com.bos.backend.presentation.auth.dto

import com.bos.backend.domain.user.enum.ProviderType
import com.bos.backend.presentation.auth.dto.validation.ValidPassword
import jakarta.validation.constraints.NotNull

data class SignUpRequestDTO(
    @field:NotNull
    val provider: ProviderType,
    val email: String? = null,
    val providerId: String? = null,
    @field:ValidPassword
    val password: String? = null,
    val providerAccessToken: String? = null,
    val termsAgreements: List<TermAgreementItemDTO>,
)
