package kz.innlab.template.notification.controller

import jakarta.validation.Valid
import kz.innlab.template.notification.dto.DeviceTokenResponse
import kz.innlab.template.notification.dto.NotificationHistoryResponse
import kz.innlab.template.notification.dto.RegisterTokenRequest
import kz.innlab.template.notification.dto.SendMulticastRequest
import kz.innlab.template.notification.dto.SendToTokenRequest
import kz.innlab.template.notification.dto.SendToTopicRequest
import kz.innlab.template.notification.dto.TopicSubscribeRequest
import kz.innlab.template.notification.model.Platform
import kz.innlab.template.notification.service.DeviceTokenService
import kz.innlab.template.notification.service.NotificationService
import kz.innlab.template.notification.service.TopicService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val deviceTokenService: DeviceTokenService,
    private val notificationService: NotificationService,
    private val topicService: TopicService
) {

    // --- Token management ---

    @PostMapping("/tokens")
    fun registerToken(
        @Valid @RequestBody request: RegisterTokenRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<DeviceTokenResponse> {
        val userId = UUID.fromString(jwt.subject)
        val platform = Platform.valueOf(request.platform.uppercase())
        val token = deviceTokenService.register(userId, platform, request.fcmToken, request.deviceId)
        return ResponseEntity.status(201).body(DeviceTokenResponse.from(token))
    }

    @GetMapping("/tokens")
    fun listTokens(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<List<DeviceTokenResponse>> {
        val userId = UUID.fromString(jwt.subject)
        val tokens = deviceTokenService.listByUser(userId).map { DeviceTokenResponse.from(it) }
        return ResponseEntity.ok(tokens)
    }

    @DeleteMapping("/tokens/{deviceId}")
    fun deleteToken(
        @PathVariable deviceId: String,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Void> {
        val userId = UUID.fromString(jwt.subject)
        deviceTokenService.deleteByDeviceId(userId, deviceId)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/tokens")
    fun deleteAllTokens(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<Void> {
        val userId = UUID.fromString(jwt.subject)
        deviceTokenService.deleteAllByUser(userId)
        return ResponseEntity.noContent().build()
    }

    // --- Send notifications ---

    @PostMapping("/send/token")
    fun sendToToken(
        @Valid @RequestBody request: SendToTokenRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Map<String, UUID>> {
        val userId = UUID.fromString(jwt.subject)
        val historyId = notificationService.sendToToken(userId, request.token, request.title, request.body, request.data)
        return ResponseEntity.accepted().body(mapOf("notificationId" to historyId))
    }

    @PostMapping("/send/multicast")
    fun sendMulticast(
        @Valid @RequestBody request: SendMulticastRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Map<String, UUID>> {
        val userId = UUID.fromString(jwt.subject)
        val historyId = notificationService.sendMulticast(userId, request.tokens, request.title, request.body, request.data)
        return ResponseEntity.accepted().body(mapOf("notificationId" to historyId))
    }

    @PostMapping("/send/topic")
    fun sendToTopic(
        @Valid @RequestBody request: SendToTopicRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Map<String, UUID>> {
        val userId = UUID.fromString(jwt.subject)
        topicService.validateTopicExists(request.topic)
        val historyId = notificationService.sendToTopic(userId, request.topic, request.title, request.body, request.data)
        return ResponseEntity.accepted().body(mapOf("notificationId" to historyId))
    }

    // --- Topic subscriptions ---

    @PostMapping("/topics/{name}/subscribe")
    fun subscribe(
        @PathVariable name: String,
        @Valid @RequestBody request: TopicSubscribeRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Void> {
        topicService.subscribe(request.token, name)
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/topics/{name}/subscribe")
    fun unsubscribe(
        @PathVariable name: String,
        @Valid @RequestBody request: TopicSubscribeRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Void> {
        topicService.unsubscribe(request.token, name)
        return ResponseEntity.ok().build()
    }

    // --- History ---

    @GetMapping("/history")
    fun getHistory(
        @RequestParam(required = false) cursor: UUID?,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<List<NotificationHistoryResponse>> {
        val userId = UUID.fromString(jwt.subject)
        val history = notificationService.getHistory(userId, cursor, size.coerceIn(1, 100))
        return ResponseEntity.ok(history.map { NotificationHistoryResponse.from(it) })
    }
}
