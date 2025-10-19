package com.bos.backend.infrastructure.util

object PasswordPolicy {
    private val PASSWORD_REGEX = Regex("^(?=.*[a-zA-Z])(?=.*\\d)(?=.*[@\$!%*#?&])[A-Za-z\\d@\$!%*#?&]{8,}$")

    fun isValidPassword(password: String): Boolean {
        return PASSWORD_REGEX.matches(password)
    }
}
