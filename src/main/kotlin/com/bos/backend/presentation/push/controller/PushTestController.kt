package com.bos.backend.presentation.push.controller

import com.bos.backend.application.push.PushTestResult
import com.bos.backend.application.push.PushTestService
import com.bos.backend.presentation.push.dto.DeepLinkType
import com.bos.backend.presentation.push.dto.PushTestRequestDTO
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/push")
class PushTestController(
    private val pushTestService: PushTestService,
) {
    @PostMapping("/test")
    suspend fun sendTestPush(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: PushTestRequestDTO,
    ): PushTestResult {
        val deepLinkType = DeepLinkType.fromString(request.deepLinkType)

        return pushTestService.sendTestPush(
            userId = userId.toLong(),
            message = request.message,
            deepLinkType = deepLinkType,
            transactionId = request.transactionId,
        )
    }
}
