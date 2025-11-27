package com.bos.backend.domain.user.enum

enum class ProviderType(
    val value: String,
) {
    KAKAO("KAKAO"),
    BOS("BOS"),
    APPLE("APPLE"),
    ;

    companion object {
        fun fromValue(value: String): ProviderType =
            entries.find { it.value == value }
                ?: throw IllegalArgumentException("Unknown provider: $value")
    }
}
