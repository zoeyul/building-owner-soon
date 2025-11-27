package com.bos.backend.infrastructure.external

import io.jsonwebtoken.Jwts
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

@Service
class AppleApiService(
    private val webClient: WebClient,
    @Value("\${apple.team-id}") private val teamId: String,
    @Value("\${apple.client-id}") private val clientId: String,
    @Value("\${apple.key-id}") private val keyId: String,
    @Value("\${apple.private-key}") private val privateKeyString: String,
) {
    private val logger = LoggerFactory.getLogger(AppleApiService::class.java)
    private val appleTokenUrl = "https://appleid.apple.com/auth/token"
    private val appleKeysUrl = "https://appleid.apple.com/auth/keys"
    private val appleIssuer = "https://appleid.apple.com"

    private val cachedPublicKeys = ConcurrentHashMap<String, PublicKey>()
    private val privateKey: PrivateKey by lazy { loadPrivateKey() }

    suspend fun verifyAuthorizationCode(authorizationCode: String): AppleUserInfo {
        val clientSecret = generateClientSecret()

        val formData =
            LinkedMultiValueMap<String, String>().apply {
                add("client_id", clientId)
                add("client_secret", clientSecret)
                add("code", authorizationCode)
                add("grant_type", "authorization_code")
            }

        val tokenResponse =
            webClient
                .post()
                .uri(appleTokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .awaitBody<AppleTokenResponse>()

        return verifyAndExtractUserInfo(tokenResponse.idToken)
    }

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

    private suspend fun verifyAndExtractUserInfo(idToken: String): AppleUserInfo {
        val headerJson = decodeJwtHeader(idToken)
        val kid = extractKid(headerJson)

        val publicKey =
            cachedPublicKeys[kid] ?: fetchAndCachePublicKey(kid)
                ?: throw IllegalArgumentException("Apple public key not found for kid: $kid")

        val claims =
            Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(idToken)
                .payload

        require(claims.issuer == appleIssuer) { "Invalid issuer: ${claims.issuer}" }
        require(claims.audience?.contains(clientId) == true) { "Invalid audience: ${claims.audience}" }

        val sub =
            claims.subject
                ?: throw IllegalArgumentException("Missing sub claim in Apple id token")

        val email = claims["email"] as? String

        return AppleUserInfo(sub = sub, email = email)
    }

    private fun decodeJwtHeader(token: String): String {
        val headerPart =
            token.split(".").firstOrNull()
                ?: throw IllegalArgumentException("Invalid JWT format")
        return String(Base64.getUrlDecoder().decode(headerPart))
    }

    private fun extractKid(headerJson: String): String {
        val kidPattern = """"kid"\s*:\s*"([^"]+)"""".toRegex()
        return kidPattern.find(headerJson)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("Missing kid in JWT header")
    }

    private suspend fun fetchAndCachePublicKey(kid: String): PublicKey? {
        return try {
            val response =
                webClient
                    .get()
                    .uri(appleKeysUrl)
                    .retrieve()
                    .awaitBody<ApplePublicKeysResponse>()

            response.keys.forEach { key ->
                val publicKey = convertToPublicKey(key)
                cachedPublicKeys[key.kid] = publicKey
            }

            cachedPublicKeys[kid]
        } catch (e: RuntimeException) {
            logger.error("Failed to fetch Apple public keys: ${e.message}", e)
            null
        }
    }

    private fun convertToPublicKey(key: ApplePublicKey): PublicKey {
        val nBytes = Base64.getUrlDecoder().decode(key.n)
        val eBytes = Base64.getUrlDecoder().decode(key.e)

        val n = BigInteger(1, nBytes)
        val e = BigInteger(1, eBytes)

        val spec = RSAPublicKeySpec(n, e)
        val keyFactory = KeyFactory.getInstance("RSA")

        return keyFactory.generatePublic(spec)
    }

    companion object {
        private const val CLIENT_SECRET_EXPIRATION_SECONDS = 15777000L // 약 6개월
    }
}

data class AppleUserInfo(
    val sub: String,
    val email: String?,
)

data class AppleTokenResponse(
    val accessToken: String? = null,
    val tokenType: String? = null,
    val expiresIn: Long? = null,
    val refreshToken: String? = null,
    val idToken: String,
)

data class ApplePublicKeysResponse(
    val keys: List<ApplePublicKey>,
)

data class ApplePublicKey(
    val kty: String,
    val kid: String,
    val use: String,
    val alg: String,
    val n: String,
    val e: String,
)
