package com.bos.backend.infrastructure.external

import io.jsonwebtoken.Jwts
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

@Service
class AppleApiService(
    private val webClient: WebClient,
    @Value("\${apple.bundle-id}") private val bundleId: String,
) {
    private val logger = LoggerFactory.getLogger(AppleApiService::class.java)
    private val appleKeysUrl = "https://appleid.apple.com/auth/keys"
    private val appleIssuer = "https://appleid.apple.com"

    private val cachedPublicKeys = ConcurrentHashMap<String, PublicKey>()

    suspend fun verifyIdentityToken(identityToken: String): AppleUserInfo {
        val headerJson = decodeJwtHeader(identityToken)
        val kid = extractKid(headerJson)

        val publicKey =
            cachedPublicKeys[kid] ?: fetchAndCachePublicKey(kid)
                ?: throw IllegalArgumentException("Apple public key not found for kid: $kid")

        val claims =
            Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(identityToken)
                .payload

        require(claims.issuer == appleIssuer) { "Invalid issuer: ${claims.issuer}" }
        require(claims.audience?.contains(bundleId) == true) { "Invalid audience: ${claims.audience}" }

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
}

data class AppleUserInfo(
    val sub: String,
    val email: String?,
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
