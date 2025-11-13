package com.bos.backend.infrastructure.template

object EmailTemplate {
    object Verification {
        const val SUBJECT = "[내꿈은 건물주] 이메일 인증 코드"
        val CONTENT =
            """
            이메일 인증 안내

            안녕하세요, 내꿈은 건물주입니다.

            내꿈은 건물주 서비스를 이용하시려면 아래 인증 코드를 입력해 이메일 인증을 완료해주세요.

            인증 코드: {code}

            ※ 해당 코드는 발급 후 10분간 유효합니다.
            본인이 요청하지 않은 경우, 이 메일은 무시하셔도 됩니다.

            감사합니다.
            """.trimIndent()
    }
}
