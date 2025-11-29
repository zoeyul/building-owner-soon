package com.bos.backend.infrastructure.external

import io.jsonwebtoken.Jwts
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.Date

/**
 * Apple 웹 OAuth 플로우를 위한 서비스 (테스트용)
 * 웹에서 Apple 로그인 후 Identity Token을 얻기 위해 사용합니다.
 */
@Service
class AppleWebAuthService(
    private val webClient: WebClient,
    @Value("\${apple.team-id:}") private val teamId: String,
    @Value("\${apple.client-id:}") private val clientId: String,
    @Value("\${apple.key-id:}") private val keyId: String,
    @Value("\${apple.private-key:}") private val privateKeyString: String,
) {
    private val appleTokenUrl = "https://appleid.apple.com/auth/token"
    private val appleIssuer = "https://appleid.apple.com"

    private val privateKey: PrivateKey? by lazy {
        if (privateKeyString.isNotEmpty()) loadPrivateKey() else null
    }

    /**
     * Authorization Code를 Apple Token Endpoint에서 id_token으로 교환
     */
    suspend fun verifyAuthorizationCode(authorizationCode: String): AppleTokenResponse {
        require(isConfigured()) { "Apple Web Auth is not configured" }

        val clientSecret = generateClientSecret()

        val formData =
            LinkedMultiValueMap<String, String>().apply {
                add("client_id", clientId)
                add("client_secret", clientSecret)
                add("code", authorizationCode)
                add("grant_type", "authorization_code")
            }

        return webClient
            .post()
            .uri(appleTokenUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData(formData))
            .retrieve()
            .awaitBody<AppleTokenResponse>()
    }

    /**
     * Apple에서 요구하는 Client Secret (JWT) 생성
     */
    private fun generateClientSecret(): String {
        val now = Instant.now()
        val expiration = now.plusSeconds(CLIENT_SECRET_EXPIRATION_SECONDS)

        return Jwts.builder()
            .header()
            .keyId(keyId)
            .and()
            .issuer(teamId)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .audience()
            .add(appleIssuer)
            .and()
            .subject(clientId)
            .signWith(privateKey, Jwts.SIG.ES256)
            .compact()
    }

    /**
     * PEM 형식의 Private Key를 PrivateKey 객체로 변환
     */
    private fun loadPrivateKey(): PrivateKey {
        val cleanedKey =
            privateKeyString
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\s".toRegex(), "")

        val keyBytes = Base64.getDecoder().decode(cleanedKey)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("EC")

        return keyFactory.generatePrivate(keySpec)
    }

    /**
     * 웹 OAuth 설정이 완료되었는지 확인
     */
    fun isConfigured(): Boolean =
        teamId.isNotEmpty() &&
            clientId.isNotEmpty() &&
            keyId.isNotEmpty() &&
            privateKeyString.isNotEmpty()

    companion object {
        private const val CLIENT_SECRET_EXPIRATION_SECONDS = 15777000L // 약 6개월
    }
}

data class AppleTokenResponse(
    val accessToken: String? = null,
    val tokenType: String? = null,
    val expiresIn: Long? = null,
    val refreshToken: String? = null,
    val idToken: String,
)
