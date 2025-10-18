package com.bos.backend.infrastructure.external

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Service
class KakaoApiService(
    private val webClient: WebClient,
) {
    suspend fun getUserInfo(accessToken: String): KakaoUserInfo {
        val response =
            webClient
                .get()
                .uri("https://kapi.kakao.com/v2/user/me")
                .header("Authorization", "Bearer $accessToken")
                .retrieve()
                .awaitBody<Map<String, Any>>()

        val id = response["id"].toString()
        val kakaoAccount = response["kakao_account"] as Map<String, Any>
        val email = kakaoAccount["email"] as String?

        return KakaoUserInfo(id = id, email = email)
    }
}

data class KakaoUserInfo(
    val id: String,
    val email: String?,
)
