package com.bos.backend.domain.push

import com.bos.backend.domain.user.enum.Platform
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.ApnsConfig
import com.google.firebase.messaging.Aps
import com.google.firebase.messaging.ApsAlert
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification

/**
 * 푸시 메시지 타입
 * - NOTIFICATION: 알림 + 데이터 (사용자에게 표시됨)
 * - DATA_ONLY: 데이터만 (백그라운드 처리, 사용자에게 표시 안 됨)
 */
enum class PushMessageType {
    NOTIFICATION,
    DATA_ONLY,
}

/**
 * iOS 전용 푸시 설정
 */
data class ApnsSettings(
    // 배지 카운트
    val badge: Int? = null,
    // 사운드 파일명
    val sound: String? = "default",
    // high / normal
    val priority: String = "high",
    // 백그라운드 업데이트
    val contentAvailable: Boolean = false,
)

/**
 * Android 전용 푸시 설정
 */
data class AndroidSettings(
    // 알림 채널 ID
    val channelId: String = "default",
    // high / normal
    val priority: String = "high",
    // 알림 색상 (#RRGGBB)
    val color: String? = null,
    // 알림 아이콘
    val icon: String? = null,
    // Time To Live (밀리초)
    val ttl: Long? = null,
)

/**
 * 푸시 메시지 도메인 모델
 * Expo 앱에 Firebase Messaging으로 직접 전송하기 위한 모델
 */
data class PushMessage(
    val title: String,
    val body: String,
    val imageUrl: String? = null,
    val data: Map<String, String>? = null,
    val messageType: PushMessageType = PushMessageType.NOTIFICATION,
    val apnsSettings: ApnsSettings? = null,
    val androidSettings: AndroidSettings? = null,
) {
    /**
     * 플랫폼별 최적화된 FCM 메시지 생성
     */
    fun toFcmMessage(
        token: String,
        platform: Platform?,
    ): Message {
        val messageBuilder =
            Message
                .builder()
                .setToken(token)

        // 메시지 타입에 따라 notification 포함 여부 결정
        if (messageType == PushMessageType.NOTIFICATION) {
            val notificationBuilder =
                Notification
                    .builder()
                    .setTitle(title)
                    .setBody(body)

            imageUrl?.let { notificationBuilder.setImage(it) }

            messageBuilder.setNotification(notificationBuilder.build())
        }

        // 데이터 페이로드 추가
        data?.let { messageBuilder.putAllData(it) }

        // 플랫폼별 설정 적용
        when (platform) {
            Platform.IOS -> apnsSettings?.let { messageBuilder.setApnsConfig(buildApnsConfig(it)) }
            Platform.ANDROID ->
                androidSettings?.let {
                    messageBuilder.setAndroidConfig(
                        buildAndroidConfig(it),
                    )
                }
            null -> {
                // 플랫폼 정보가 없는 경우 기본 설정 적용
                apnsSettings?.let { messageBuilder.setApnsConfig(buildApnsConfig(it)) }
                androidSettings?.let { messageBuilder.setAndroidConfig(buildAndroidConfig(it)) }
            }
        }

        return messageBuilder.build()
    }

    /**
     * iOS APNs 설정 빌드
     */
    private fun buildApnsConfig(settings: ApnsSettings): ApnsConfig {
        val apsBuilder = Aps.builder()

        settings.badge?.let { apsBuilder.setBadge(it) }
        settings.sound?.let { apsBuilder.setSound(it) }
        if (settings.contentAvailable) {
            apsBuilder.setContentAvailable(true)
        }

        val immutableList: List<String> = mutableListOf("A", "B", "C")
        // immutableList.add("D") -> 불가능
        (immutableList as MutableList).add("E") // -> 가능

        // Expo 앱의 경우 alert를 직접 설정하면 notification과 중복될 수 있으므로
        // notification 메시지 타입일 때는 Firebase의 notification 필드를 사용
        if (messageType == PushMessageType.DATA_ONLY) {
            apsBuilder.setAlert(
                ApsAlert
                    .builder()
                    .setTitle(title)
                    .setBody(body)
                    .build(),
            )
        }

        return ApnsConfig
            .builder()
            .setAps(apsBuilder.build())
            .putHeader("apns-priority", if (settings.priority == "high") "10" else "5")
            .build()
    }

    /**
     * Android 설정 빌드
     */
    private fun buildAndroidConfig(settings: AndroidSettings): AndroidConfig {
        val androidConfigBuilder = AndroidConfig.builder()
        val notificationBuilder = AndroidNotification.builder()

        notificationBuilder.setChannelId(settings.channelId)
        settings.color?.let { notificationBuilder.setColor(it) }
        settings.icon?.let { notificationBuilder.setIcon(it) }

        androidConfigBuilder
            .setNotification(notificationBuilder.build())
            .setPriority(
                if (settings.priority == "high") {
                    AndroidConfig.Priority.HIGH
                } else {
                    AndroidConfig.Priority.NORMAL
                },
            )

        settings.ttl?.let { androidConfigBuilder.setTtl(it) }

        return androidConfigBuilder.build()
    }

    companion object {
        fun createSimpleMessage(
            title: String,
            body: String,
        ): PushMessage {
            return PushMessage(
                title = title,
                body = body,
            )
        }

        fun createMessageWithDeepLink(
            title: String,
            body: String,
            deepLink: String,
        ): PushMessage {
            return PushMessage(
                title = title,
                body = body,
                data = mapOf("deepLink" to deepLink),
            )
        }

        fun createMessageWithImage(
            title: String,
            body: String,
            imageUrl: String,
        ): PushMessage {
            return PushMessage(
                title = title,
                body = body,
                imageUrl = imageUrl,
            )
        }

        /**
         * 플랫폼별 최적화된 메시지 생성
         */
        fun createOptimizedMessage(
            title: String,
            body: String,
            deepLink: String? = null,
            imageUrl: String? = null,
            badge: Int? = null,
        ): PushMessage {
            val dataMap = mutableMapOf<String, String>()
            deepLink?.let { dataMap["deepLink"] = it }

            return PushMessage(
                title = title,
                body = body,
                imageUrl = imageUrl,
                data = dataMap.takeIf { it.isNotEmpty() },
                messageType = PushMessageType.NOTIFICATION,
                apnsSettings =
                    ApnsSettings(
                        badge = badge,
                        sound = "default",
                        priority = "high",
                    ),
                androidSettings =
                    AndroidSettings(
                        channelId = "default",
                        priority = "high",
                    ),
            )
        }

        /**
         * 백그라운드 데이터 전용 메시지 생성 (조용한 푸시)
         */
        fun createDataOnlyMessage(
            title: String,
            body: String,
            data: Map<String, String>,
        ): PushMessage {
            return PushMessage(
                title = title,
                body = body,
                data = data,
                messageType = PushMessageType.DATA_ONLY,
                apnsSettings =
                    ApnsSettings(
                        sound = null,
                        priority = "normal",
                        contentAvailable = true,
                    ),
                androidSettings =
                    AndroidSettings(
                        priority = "normal",
                        // 24시간
                        ttl = 86400000,
                    ),
            )
        }
    }
}
