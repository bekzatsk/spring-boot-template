package kz.innlab.starter.notification.controller

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kz.innlab.starter.notification.dto.CreateTopicRequest
import kz.innlab.starter.notification.dto.TopicResponse
import kz.innlab.starter.notification.service.TopicService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Admin - Topics", description = "Topic management (requires ADMIN role)")
@RequestMapping("/api/v1/admin/topics")
class TopicAdminController(
    private val topicService: TopicService
) {

    @PostMapping
    fun createTopic(@Valid @RequestBody request: CreateTopicRequest): ResponseEntity<TopicResponse> {
        val topic = topicService.createTopic(request.name)
        return ResponseEntity.status(201).body(TopicResponse.from(topic))
    }

    @DeleteMapping("/{name}")
    fun deleteTopic(@PathVariable name: String): ResponseEntity<Void> {
        topicService.deleteTopic(name)
        return ResponseEntity.noContent().build()
    }
}
