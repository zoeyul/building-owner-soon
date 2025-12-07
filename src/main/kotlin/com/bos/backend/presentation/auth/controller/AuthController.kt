package com.bos.backend.presentation.auth.controller

import com.bos.backend.application.auth.AuthService
import com.bos.backend.presentation.auth.dto.CheckEmailResponse
import com.bos.backend.presentation.auth.dto.CommonSignResponseDTO
import com.bos.backend.presentation.auth.dto.EmailVerificationCheckDTO
import com.bos.backend.presentation.auth.dto.EmailVerificationRequestDTO
import com.bos.backend.presentation.auth.dto.LogoutRequestDTO
import com.bos.backend.presentation.auth.dto.PasswordResetRequestDTO
import com.bos.backend.presentation.auth.dto.SignInRequestDTO
import com.bos.backend.presentation.auth.dto.SignUpRequestDTO
import com.bos.backend.presentation.auth.dto.TokenRefreshRequestDTO
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/auth/sign-up")
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun signUp(
        @Valid @RequestBody signUpRequestDTO: SignUpRequestDTO,
    ): CommonSignResponseDTO = authService.signUp(signUpRequestDTO)

    @PostMapping("/auth/sign-in")
    @ResponseStatus(HttpStatus.OK)
    suspend fun signIn(
        @Valid @RequestBody signInRequestDTO: SignInRequestDTO,
        @RequestHeader("Authorization", required = false) authHeader: String?,
    ): CommonSignResponseDTO {
        val basicAuthHeader = authHeader?.takeIf { it.startsWith("Basic ") }
        return authService.signIn(signInRequestDTO, basicAuthHeader)
    }

    @GetMapping("/auth/check-email")
    @ResponseStatus(HttpStatus.OK)
    suspend fun checkEmail(
        @RequestParam email: String,
    ): CheckEmailResponse = authService.isBosEmailUserAbsent(email)

    @PostMapping("/auth/email-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun sendVerificationEmail(
        @Valid @RequestBody emailVerificationRequestDTO: EmailVerificationRequestDTO,
    ) = authService.sendVerificationEmail(emailVerificationRequestDTO)

    @PostMapping("/auth/email-verification/verify-code")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun verifyCode(
        @Valid @RequestBody emailVerificationCheckDTO: EmailVerificationCheckDTO,
    ) = authService.verifyCode(emailVerificationCheckDTO)

    @PostMapping("/auth/email-verification/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun resendVerificationEmail(
        @Valid @RequestBody emailVerificationRequestDTO: EmailVerificationRequestDTO,
    ) = authService.resendVerificationEmail(emailVerificationRequestDTO)

    @PostMapping("/auth/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun resetPassword(
        @Valid @RequestBody request: PasswordResetRequestDTO,
    ) = authService.resetPassword(request)

    @DeleteMapping("/auth/withdrawal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun withdraw(
        @AuthenticationPrincipal userId: String,
    ) = authService.deleteById(userId.toLong())

    @PostMapping("/auth/token/refresh")
    @ResponseStatus(HttpStatus.OK)
    suspend fun refreshToken(
        @Valid @RequestBody request: TokenRefreshRequestDTO,
    ): CommonSignResponseDTO = authService.refreshToken(request)

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun logout(
        @AuthenticationPrincipal userId: String,
        @RequestBody request: LogoutRequestDTO,
    ) = authService.logout(userId.toLong(), request.deviceId)
}
