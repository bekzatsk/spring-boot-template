package kz.innlab.starter.notification.repository

import kz.innlab.starter.notification.model.NotificationTopic
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NotificationTopicRepository : JpaRepository<NotificationTopic, UUID> {

    fun findByName(name: String): NotificationTopic?

    fun existsByName(name: String): Boolean
}
