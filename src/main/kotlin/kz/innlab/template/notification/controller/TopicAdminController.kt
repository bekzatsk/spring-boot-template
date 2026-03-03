package kz.innlab.template.notification.controller

import kz.innlab.template.notification.dto.TopicResponse
import kz.innlab.template.notification.service.TopicService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/topics")
class TopicAdminController(
    private val topicService: TopicService
) {

    @PostMapping
    fun createTopic(@RequestBody body: Map<String, String>): ResponseEntity<TopicResponse> {
        val name = body["name"] ?: throw IllegalArgumentException("name is required")
        val topic = topicService.createTopic(name)
        return ResponseEntity.status(201).body(TopicResponse.from(topic))
    }

    @DeleteMapping("/{name}")
    fun deleteTopic(@PathVariable name: String): ResponseEntity<Void> {
        topicService.deleteTopic(name)
        return ResponseEntity.noContent().build()
    }
}
