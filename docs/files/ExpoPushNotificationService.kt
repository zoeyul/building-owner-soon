package com.chabot.push

import com.google.firebase.ErrorCode
import com.google.firebase.messaging.*
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Expo + Firebase Push Notification Service
 * 
 * This service handles push notifications for Expo apps using Firebase Admin SDK.
 * Key difference: iOS uses APNs tokens directly, not FCM tokens.
 * 
 * @author Chabot Backend Team
 * @version 1.0.0
 */
@Service
class ExpoPushNotificationService(
    private val firebaseMessaging: FirebaseMessaging,
    private val tokenRepository: PushTokenRepository
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    
    companion object {
        const val TTL_ONE_HOUR = 3600 * 1000L
        const val APNS_PRIORITY_HIGH = "10"
        const val APNS_PRIORITY_LOW = "5"
        const val APNS_PUSH_TYPE_ALERT = "alert"
        const val APNS_PUSH_TYPE_BACKGROUND = "background"
    }
    
    /**
     * 단일 사용자에게 푸시 알림 전송
     */
    @Transactional
    suspend fun sendToUser(
        userId: String,
        title: String,
        body: String,
        data: Map<String, String>? = null,
        badge: Int? = null
    ): Boolean {
        return try {
            val pushToken = tokenRepository.findByUserId(userId)
                ?: throw IllegalArgumentException("Token not found for user: $userId")
            
            sendToDevice(
                token = pushToken.token,
                platform = pushToken.platform.name,
                title = title,
                body = body,
                data = data,
                badge = badge
            )
            
            true
        } catch (e: Exception) {
            logger.error("Failed to send notification to user $userId", e)
            false
        }
    }
    
    /**
     * 여러 사용자에게 배치 전송
     */
    suspend fun sendToUsers(
        userIds: List<String>,
        title: String,
        body: String,
        data: Map<String, String>? = null,
        badge: Int? = null
    ): Map<String, Boolean> = coroutineScope {
        val results = mutableMapOf<String, Boolean>()
        val tokens = tokenRepository.findAllByUserIdIn(userIds)
        
        logger.info("Sending notifications to ${tokens.size} devices")
        
        val jobs = tokens.map { pushToken ->
            async(Dispatchers.IO) {
                val success = try {
                    sendToDevice(
                        token = pushToken.token,
                        platform = pushToken.platform.name,
                        title = title,
                        body = body,
                        data = data,
                        badge = badge
                    )
                    true
                } catch (e: FirebaseMessagingException) {
                    handleFirebaseException(e, pushToken)
                    false
                } catch (e: Exception) {
                    logger.error("Unexpected error for user ${pushToken.userId}", e)
                    false
                }
                pushToken.userId to success
            }
        }
        
        jobs.awaitAll().forEach { (userId, success) ->
            results[userId] = success
        }
        
        logger.info("Notification batch complete. Success: ${results.count { it.value }}, Failed: ${results.count { !it.value }}")
        results
    }
    
    /**
     * 디바이스에 직접 전송 (내부 메서드)
     */
    private fun sendToDevice(
        token: String,
        platform: String,
        title: String,
        body: String,
        data: Map<String, String>? = null,
        badge: Int? = null
    ): String {
        val message = when (platform.lowercase()) {
            "android" -> buildAndroidMessage(token, title, body, data)
            "ios" -> buildIosMessage(token, title, body, data, badge)
            else -> throw IllegalArgumentException("Unknown platform: $platform")
        }
        
        val response = firebaseMessaging.send(message)
        logger.debug("Message sent successfully. Platform: $platform, Response: $response")
        return response
    }
    
    /**
     * Android 메시지 빌더
     * 
     * Android uses standard FCM tokens
     */
    private fun buildAndroidMessage(
        fcmToken: String,
        title: String,
        body: String,
        data: Map<String, String>?
    ): Message {
        return Message.builder()
            .setToken(fcmToken)
            .setNotification(
                Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build()
            )
            .setAndroidConfig(
                AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setTtl(TTL_ONE_HOUR)
                    .setNotification(
                        AndroidNotification.builder()
                            .setIcon("ic_notification")  // drawable 리소스 이름
                            .setColor("#007AFF")         // 알림 아이콘 색상
                            .setSound("default")
                            .setChannelId("default")     // Android O+ 필수
                            .setClickAction("OPEN_MAIN_ACTIVITY")
                            .build()
                    )
                    .putAllData(data ?: emptyMap())
                    .build()
            )
            .putAllData(data ?: emptyMap())
            .build()
    }
    
    /**
     * iOS 메시지 빌더
     * 
     * IMPORTANT: iOS uses APNs tokens, not FCM tokens!
     * The Firebase Admin SDK will handle the conversion internally.
     */
    private fun buildIosMessage(
        apnsToken: String,
        title: String,
        body: String,
        data: Map<String, String>?,
        badge: Int? = null
    ): Message {
        return Message.builder()
            .setToken(apnsToken)  // This is APNs token for iOS!
            .setNotification(
                Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build()
            )
            .setApnsConfig(
                ApnsConfig.builder()
                    .setAps(
                        Aps.builder()
                            .setAlert(
                                ApsAlert.builder()
                                    .setTitle(title)
                                    .setBody(body)
                                    .build()
                            )
                            .setBadge(badge ?: 0)
                            .setSound("default")
                            .setContentAvailable(true)  // 백그라운드 업데이트 활성화
                            .setMutableContent(true)     // Notification Service Extension 지원
                            .build()
                    )
                    .putAllCustomData(data ?: emptyMap())
                    // iOS 13+ 필수 헤더들
                    .putHeader("apns-push-type", APNS_PUSH_TYPE_ALERT)
                    .putHeader("apns-priority", APNS_PRIORITY_HIGH)
                    .putHeader("apns-expiration", "0")  // 즉시 만료
                    .build()
            )
            .putAllData(data ?: emptyMap())
            .build()
    }
    
    /**
     * 데이터 전용 메시지 (Silent Push)
     * 
     * Used for background updates without showing notification
     */
    fun buildSilentMessage(
        token: String,
        platform: String,
        data: Map<String, String>
    ): Message {
        val builder = Message.builder()
            .setToken(token)
            .putAllData(data)
        
        when (platform.lowercase()) {
            "ios" -> {
                // iOS Silent Push requires special configuration
                builder.setApnsConfig(
                    ApnsConfig.builder()
                        .setAps(
                            Aps.builder()
                                .setContentAvailable(true)  // 필수!
                                .build()
                        )
                        .putHeader("apns-push-type", APNS_PUSH_TYPE_BACKGROUND)
                        .putHeader("apns-priority", APNS_PRIORITY_LOW)
                        .build()
                )
            }
            "android" -> {
                // Android data-only message
                builder.setAndroidConfig(
                    AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setTtl(TTL_ONE_HOUR)
                        .build()
                )
            }
        }
        
        return builder.build()
    }
    
    /**
     * Firebase 예외 처리
     */
    private suspend fun handleFirebaseException(
        e: FirebaseMessagingException,
        token: PushToken
    ) {
        logger.error("Firebase error for user ${token.userId}: ${e.message}, ErrorCode: ${e.errorCode}")
        
        when (e.errorCode) {
            ErrorCode.UNREGISTERED,
            ErrorCode.INVALID_ARGUMENT -> {
                // 무효한 토큰은 DB에서 삭제
                logger.warn("Removing invalid token for user ${token.userId}")
                withContext(Dispatchers.IO) {
                    tokenRepository.delete(token)
                }
            }
            ErrorCode.QUOTA_EXCEEDED -> {
                logger.error("FCM quota exceeded. Consider implementing rate limiting.")
            }
            ErrorCode.UNAVAILABLE -> {
                logger.error("FCM service temporarily unavailable. Retry later.")
            }
            ErrorCode.SENDER_ID_MISMATCH -> {
                logger.error("Sender ID mismatch. Check Firebase configuration.")
            }
            else -> {
                logger.error("Unhandled Firebase error: ${e.errorCode}")
            }
        }
    }
    
    /**
     * 토큰 등록/업데이트
     */
    @Transactional
    fun upsertToken(userId: String, token: String, platform: String): PushToken {
        val platformEnum = when (platform.lowercase()) {
            "ios" -> PushToken.Platform.IOS
            "android" -> PushToken.Platform.ANDROID
            else -> throw IllegalArgumentException("Invalid platform: $platform")
        }
        
        val existingToken = tokenRepository.findByUserId(userId)
        
        return if (existingToken != null) {
            // 기존 토큰 업데이트
            existingToken.apply {
                this.token = token
                this.platform = platformEnum
                this.updatedAt = LocalDateTime.now()
            }
            tokenRepository.save(existingToken)
            logger.info("Token updated for user $userId, platform: $platform")
            existingToken
        } else {
            // 새 토큰 생성
            val newToken = PushToken(
                userId = userId,
                token = token,
                platform = platformEnum
            )
            tokenRepository.save(newToken)
            logger.info("New token registered for user $userId, platform: $platform")
            newToken
        }
    }
    
    /**
     * 토큰 삭제 (로그아웃 시)
     */
    @Transactional
    fun removeToken(userId: String) {
        tokenRepository.deleteByUserId(userId)
        logger.info("Token removed for user $userId")
    }
    
    /**
     * 배치 전송 결과 DTO
     */
    data class BatchResult(
        val total: Int,
        val success: Int,
        val failed: Int,
        val details: Map<String, Boolean>
    )
}
