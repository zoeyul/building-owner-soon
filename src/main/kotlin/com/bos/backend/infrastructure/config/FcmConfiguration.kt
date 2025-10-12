package com.bos.backend.infrastructure.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.nio.charset.StandardCharsets

@Configuration
class FcmConfiguration {
    @Value("\${firebase.credentials-path:}")
    private val credentialsPath: String = ""

    @Value("\${firebase.credentials-json:}")
    private val credentialsJson: String = ""

    @Bean
    fun initializeFirebaseApp(): FirebaseApp {
        if (FirebaseApp.getApps().isEmpty()) {
            val credentials = createGoogleCredentials()
            val options =
                FirebaseOptions
                    .builder()
                    .setCredentials(credentials)
                    .build()

            return FirebaseApp.initializeApp(options)
        }
        return FirebaseApp.getInstance()
    }

    private fun createGoogleCredentials(): GoogleCredentials {
        return when {
            credentialsJson.isNotBlank() -> {
                // JSON 문자열로부터 credentials 생성 (프로덕션 환경)
                val stream = ByteArrayInputStream(credentialsJson.toByteArray(StandardCharsets.UTF_8))
                GoogleCredentials.fromStream(stream)
            }
            credentialsPath.isNotBlank() -> {
                // 파일 경로로부터 credentials 생성 (로컬 환경)
                FileInputStream(credentialsPath).use { stream ->
                    GoogleCredentials.fromStream(stream)
                }
            }
            else -> {
                throw IllegalStateException(
                    "Firebase credentials not configured. Please set either " +
                        "FIREBASE_CREDENTIALS_PATH or FIREBASE_CREDENTIALS_JSON environment variable.",
                )
            }
        }
    }
}
