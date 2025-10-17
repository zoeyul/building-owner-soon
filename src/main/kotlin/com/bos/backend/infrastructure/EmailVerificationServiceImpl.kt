package com.bos.backend.infrastructure

import com.bos.backend.application.service.EmailVerificationService
import com.bos.backend.domain.auth.enums.EmailVerificationType
import com.bos.backend.domain.user.enum.ProviderType
import com.bos.backend.domain.user.repository.UserAuthRepository
import com.bos.backend.infrastructure.event.EmailVerificationEvent
import com.bos.backend.infrastructure.external.EmailVerificationCodeStore
import com.bos.backend.infrastructure.template.EmailTemplate
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.util.Random

@Service
class EmailVerificationServiceImpl(
    private val emailVerificationCodeStore: EmailVerificationCodeStore,
    private val userAuthRepository: UserAuthRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
) : EmailVerificationService {
    companion object {
        private const val VERIFICATION_CODE_LENGTH = 6
        private const val VERIFICATION_CODE_DIGIT_RANGE = 10
        private const val RESEND_ALLOWED_TTL_THRESHOLD = 540
    }

    override suspend fun sendVerificationEmail(
        email: String,
        type: EmailVerificationType,
    ) {
        emailVerificationCodeStore.deleteVerificationCode(email, type.value)
        val verificationCode = generateVerificationCode()

        emailVerificationCodeStore.saveVerificationCode(email, verificationCode, type.value)
        val content = EmailTemplate.Verification.CONTENT.replace("{code}", verificationCode)

        applicationEventPublisher.publishEvent(
            EmailVerificationEvent(
                email = email,
                subject = EmailTemplate.Verification.SUBJECT,
                content = content,
            ),
        )
    }

    override suspend fun verifyCode(
        email: String,
        verificationCode: String,
        type: EmailVerificationType,
    ): Boolean {
        val savedCode = emailVerificationCodeStore.getVerificationCode(email, type.value)
        val isValid = savedCode != null && savedCode == verificationCode

        if (isValid) {
            emailVerificationCodeStore.deleteVerificationCode(email, type.value)
        }
        return isValid
    }

    override suspend fun isEmailDuplicated(email: String): Boolean =
        userAuthRepository.findByEmailAndProviderType(email, ProviderType.BOS.value) != null

    override suspend fun isVerificationCodeExpired(
        email: String,
        type: EmailVerificationType,
    ): Boolean = emailVerificationCodeStore.getVerificationCode(email, type.value) == null

    override suspend fun isVerificationCodeMatched(
        email: String,
        code: String,
        type: EmailVerificationType,
    ): Boolean {
        val savedCode = emailVerificationCodeStore.getVerificationCode(email, type.value)
        return savedCode == code
    }

    override suspend fun isResendAllowed(
        email: String,
        type: EmailVerificationType,
    ): Boolean {
        val ttl = emailVerificationCodeStore.getVerificationCodeTtl(email, type.value)

        if (ttl == null || ttl <= -1) {
            return false
        }

        return ttl <= RESEND_ALLOWED_TTL_THRESHOLD
    }

    private fun generateVerificationCode(): String {
        val random = Random()
        return (1..VERIFICATION_CODE_LENGTH).map { random.nextInt(VERIFICATION_CODE_DIGIT_RANGE) }.joinToString("")
    }
}
