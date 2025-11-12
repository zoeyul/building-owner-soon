package com.bos.backend.domain.push

/**
 * Expo Push Notification 메시지 도메인 모델
 * Expo Push Notification API (https://exp.host/--/api/v2/push/send)를 위한 모델
 */
data class ExpoPushMessage(
    val to: String,
    val title: String,
    val body: String,
//    val data: Map<String, Any>? = null,
//    val sound: String? = "default",
//    val badge: Int? = null,
//    val channelId: String? = null,
//    val categoryId: String? = null,
//    val priority: ExpoPushPriority = ExpoPushPriority.DEFAULT,
//    val ttl: Int? = null,
) {
//    companion object {
//        fun createSimple(
//            token: String,
//            title: String,
//            body: String,
//        ): ExpoPushMessage =
//            ExpoPushMessage(
//                to = token,
//                title = title,
//                body = body,
//            )
//
//        fun createWithData(
//            token: String,
//            title: String,
//            body: String,
//            data: Map<String, Any>,
//        ): ExpoPushMessage =
//            ExpoPushMessage(
//                to = token,
//                title = title,
//                body = body,
//                data = data,
//            )
//
//        fun createWithDeepLink(
//            token: String,
//            title: String,
//            body: String,
//            deepLink: String,
//        ): ExpoPushMessage =
//            ExpoPushMessage(
//                to = token,
//                title = title,
//                body = body,
//                data = mapOf("deepLink" to deepLink),
//            )
//
//        @Suppress("LongParameterList")
//        fun createOptimized(
//            token: String,
//            title: String,
//            body: String,
//            data: Map<String, Any>? = null,
//            badge: Int? = null,
//            channelId: String = "default",
//        ): ExpoPushMessage =
//            ExpoPushMessage(
//                to = token,
//                title = title,
//                body = body,
//                data = data,
//                sound = "default",
//                badge = badge,
//                channelId = channelId,
//                priority = ExpoPushPriority.HIGH,
//            )
//    }
}

/**
 * Expo Push 우선순위
 */
enum class ExpoPushPriority(
    val value: String,
) {
    DEFAULT("default"),
    NORMAL("normal"),
    HIGH("high"),
}

/**
 * Expo Push API 요청 DTO
 */
data class ExpoPushRequest(
    val to: String,
    val title: String,
    val body: String,
//    val data: Map<String, Any>? = null,
//    val sound: String? = "default",
//    val badge: Int? = null,
//    val channelId: String? = null,
//    val categoryId: String? = null,
//    val priority: String = "default",
//    val ttl: Int? = null,
) {
    companion object {
        fun from(message: ExpoPushMessage): ExpoPushRequest =
            ExpoPushRequest(
                to = message.to,
                title = message.title,
                body = message.body,
//                data = message.data,
//                sound = message.sound,
//                badge = message.badge,
//                channelId = message.channelId,
//                categoryId = message.categoryId,
//                priority = message.priority.value,
//                ttl = message.ttl,
            )
    }
}

/**
 * Expo Push API 응답 DTO
 */
data class ExpoPushResponse(
    val data: List<ExpoPushTicket>,
)

data class ExpoPushTicket(
    val status: String,
    val id: String? = null,
    val message: String? = null,
    val details: Map<String, Any>? = null,
) {
    fun isSuccess(): Boolean = status == "ok"

    fun isError(): Boolean = status == "error"
}
