package com.bos.backend.presentation.user.controller

import com.bos.backend.application.auth.AuthService
import com.bos.backend.application.user.UserDeviceService
import com.bos.backend.application.user.UserService
import com.bos.backend.presentation.auth.dto.PasswordChangeRequestDTO
import com.bos.backend.presentation.user.dto.FcmTokenUpdateRequestDTO
import com.bos.backend.presentation.user.dto.UpdateUserRequestDTO
import com.bos.backend.presentation.user.dto.UserProfileResponseDTO
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class UserController(
    private val userService: UserService,
    private val authService: AuthService,
    private val userDeviceService: UserDeviceService,
) {
    @GetMapping("/users/me")
    suspend fun getMe(
        @AuthenticationPrincipal userId: String,
    ): UserProfileResponseDTO {
        return userService.getUserProfile(userId.toLong())
    }

    @PatchMapping("/users/me")
    suspend fun patchMe(
        @AuthenticationPrincipal userId: String,
        @RequestBody updateUserRequestDTO: UpdateUserRequestDTO,
    ): UserProfileResponseDTO {
        return userService.updateUserProfile(
            userId = userId.toLong(),
            updateUserRequestDTO = updateUserRequestDTO,
        )
    }

    @PostMapping("/users/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun changePassword(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: PasswordChangeRequestDTO,
    ) {
        authService.changePassword(userId.toLong(), request)
    }

    @PutMapping("/users/fcm-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun updateFcmToken(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: FcmTokenUpdateRequestDTO,
    ) {
        userDeviceService.updateFcmToken(
            userId = userId.toLong(),
            deviceId = request.deviceId,
            fcmToken = request.fcmToken,
            platform = request.platform,
            deviceName = request.deviceName,
        )
    }
}
