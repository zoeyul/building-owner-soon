package com.bos.backend.presentation.auth.dto.validation

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import jakarta.validation.ConstraintValidatorContext

class ValidPasswordConstraintValidatorTest :
    StringSpec({
        lateinit var validator: ValidPasswordConstraintValidator
        lateinit var annotation: ValidPassword
        lateinit var context: ConstraintValidatorContext
        lateinit var builder: ConstraintValidatorContext.ConstraintViolationBuilder

        beforeTest {
            validator = ValidPasswordConstraintValidator()
            annotation = mockk()
            context = mockk(relaxed = true)
            builder = mockk(relaxed = true)

            every { annotation.message } returns "비밀번호는 영문 대소문자, 숫자, 특수문자를 포함한 8자 이상이어야 합니다."
            every { context.disableDefaultConstraintViolation() } returns Unit
            every { context.buildConstraintViolationWithTemplate(any()) } returns builder
            every { builder.addConstraintViolation() } returns context

            validator.initialize(annotation)
        }

        "null 값은 검증을 통과해야 한다" {
            validator.isValid(null, context) shouldBe true
        }

        "빈 문자열은 검증을 통과해야 한다" {
            validator.isValid("", context) shouldBe true
        }

        "공백만 있는 문자열은 검증을 통과해야 한다" {
            validator.isValid("   ", context) shouldBe true
        }

        "유효한 비밀번호는 검증을 통과해야 한다" {
            validator.isValid("Password123!", context) shouldBe true
            validator.isValid("password123!", context) shouldBe true // 소문자만 있어도 통과
            validator.isValid("PASSWORD123!", context) shouldBe true // 대문자만 있어도 통과
        }

        "유효하지 않은 비밀번호는 검증을 실패해야 한다" {
            val result = validator.isValid("invalid", context)
            result shouldBe false
        }

        "영문자가 없는 비밀번호는 검증을 실패해야 한다" {
            validator.isValid("12345678!@#", context) shouldBe false
        }

        "숫자가 없는 비밀번호는 검증을 실패해야 한다" {
            validator.isValid("Password!@#", context) shouldBe false
        }

        "특수문자가 없는 비밀번호는 검증을 실패해야 한다" {
            validator.isValid("Password1234", context) shouldBe false
        }

        "8자 미만 비밀번호는 검증을 실패해야 한다" {
            validator.isValid("Abc123!", context) shouldBe false
        }
    })
