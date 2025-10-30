package com.bos.backend.domain.user.util

object NicknameGenerator {
    private val adjectives =
        listOf(
            "알뜰한",
            "부지런한",
            "성실한",
            "끈질긴",
            "빈틈없는",
            "야무진",
            "똘똘한",
            "씩씩한",
            "다부진",
            "집요한",
            "든든한",
            "해내는",
            "눈치 빠른",
            "유연한",
            "배짱 있는",
            "성공할",
            "계획적인",
            "조용한",
            "뿌듯한",
            "거침없는",
        )

    private val nouns =
        listOf(
            "건물주",
            "일개미",
            "투자자",
            "사장님",
            "CEO",
            "세입자",
            "청약 재수생",
            "집주인",
            "백수",
            "부자",
        )

    fun generateRandomNickname(): String {
        val adjective = adjectives.random()
        val noun = nouns.random()
        return "$adjective $noun"
    }
}
