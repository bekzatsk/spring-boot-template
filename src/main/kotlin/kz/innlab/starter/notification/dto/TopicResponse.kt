package kz.innlab.starter.notification.dto

import kz.innlab.starter.notification.model.NotificationTopic
import java.time.Instant
import java.util.UUID

data class TopicResponse(
    val id: UUID,
    val name: String,
    val createdAt: Instant
) {
    companion object {
        fun from(topic: NotificationTopic): TopicResponse = TopicResponse(
            id = topic.id,
            name = topic.name,
            createdAt = topic.createdAt
        )
    }
}
