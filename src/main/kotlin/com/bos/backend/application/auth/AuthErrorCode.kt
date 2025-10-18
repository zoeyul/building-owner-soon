package com.bos.backend.application.auth

import com.bos.backend.application.ErrorCode
import org.springframework.http.HttpStatus

enum class AuthErrorCode(
    override val message: String,
    override val status: HttpStatus,
) : ErrorCode {
    EMAIL_DUPLICATE(
        "이미 존재하는 이메일입니다.",
        HttpStatus.CONFLICT,
    ),
    EMAIL_VERIFICATION_CODE_MISMATCH(
        "인증 코드가 올바르지 않습니다.",
        HttpStatus.BAD_REQUEST,
    ),
    EMAIL_VERIFICATION_CODE_EXPIRED(
        "인증 코드가 만료되었습니다.",
        HttpStatus.BAD_REQUEST,
    ),
    PASSWORD_POLICY_VIOLATION(
        "비밀번호는 영문, 숫자, 특수문자를 포함한 8자 이상이어야 합니다.",
        HttpStatus.BAD_REQUEST,
    ),
    USER_NOT_FOUND(
        "사용자를 찾을 수 없습니다.",
        HttpStatus.BAD_REQUEST,
    ),
    USERAUTH_NOT_FOUND(
        "사용자 인증 정보를 찾을 수 없습니다.",
        HttpStatus.BAD_REQUEST,
    ),
    INVALID_TOKEN(
        "유효하지 않은 토큰입니다.",
        HttpStatus.UNAUTHORIZED,
    ),
    REFRESH_TOKEN_NOT_FOUND(
        "리프레시 토큰을 찾을 수 없습니다.",
        HttpStatus.BAD_REQUEST,
    ),
    REFRESH_TOKEN_EXPIRED(
        "리프레시 토큰이 만료되었습니다.",
        HttpStatus.UNAUTHORIZED,
    ),
    REFRESH_TOKEN_REVOKED(
        "리프레시 토큰이 폐기되었습니다.",
        HttpStatus.UNAUTHORIZED,
    ),
    INVALID_PASSWORD(
        "현재 비밀번호가 올바르지 않습니다.",
        HttpStatus.BAD_REQUEST,
    ),
    SAME_PASSWORD(
        "새 비밀번호는 현재 비밀번호와 달라야 합니다.",
        HttpStatus.BAD_REQUEST,
    ),
    USER_NOT_REGISTERED(
        "가입되지 않은 사용자입니다.",
        HttpStatus.NOT_FOUND,
    ),
    PASSWORD_MISMATCH(
        "이메일 또는 비밀번호가 일치하지 않습니다.",
        HttpStatus.BAD_REQUEST,
    ),
}
