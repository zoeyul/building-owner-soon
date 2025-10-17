package com.bos.backend.presentation.auth.dto

import com.bos.backend.domain.user.enum.ProviderType
import com.bos.backend.presentation.auth.dto.validation.ValidPassword
import jakarta.validation.constraints.NotBlank

data class SignUpRequestDTO(
    @field:NotBlank
    val provider: ProviderType,
    val email: String?,
    val providerId: String? = null,
    @field:ValidPassword
    val password: String? = null,
    val providerAccessToken: String? = null,
    val termsAgreements: List<TermAgreementItemDTO>,
)
