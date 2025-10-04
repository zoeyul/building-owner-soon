package com.bos.backend.domain.push

import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification

data class PushMessage(
    val title: String,
    val body: String,
    val imageUrl: String? = null,
    val data: Map<String, String>? = null,
) {
    fun toFcmMessage(token: String): Message {
        val notificationBuilder =
            Notification
                .builder()
                .setTitle(title)
                .setBody(body)

        imageUrl?.let { notificationBuilder.setImage(it) }

        val messageBuilder =
            Message
                .builder()
                .setToken(token)
                .setNotification(notificationBuilder.build())

        data?.let { messageBuilder.putAllData(it) }

        return messageBuilder.build()
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
    }
}
