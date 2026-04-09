package kz.innlab.starter.notification.controller

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kz.innlab.starter.notification.dto.DeviceTokenResponse
import kz.innlab.starter.notification.dto.NotificationHistoryResponse
import kz.innlab.starter.notification.dto.NotificationPreferenceRequest
import kz.innlab.starter.notification.dto.NotificationPreferenceResponse
import kz.innlab.starter.notification.dto.RegisterTokenRequest
import kz.innlab.starter.notification.dto.SendMulticastRequest
import kz.innlab.starter.notification.dto.SendToTokenRequest
import kz.innlab.starter.notification.dto.SendToTopicRequest
import kz.innlab.starter.notification.dto.TopicSubscribeRequest
import kz.innlab.starter.notification.model.NotificationChannel
import kz.innlab.starter.notification.model.Platform
import kz.innlab.starter.notification.service.DeviceTokenService
import kz.innlab.starter.notification.service.NotificationPreferenceService
import kz.innlab.starter.notification.service.NotificationService
import kz.innlab.starter.notification.service.TopicService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@Tag(name = "Notifications", description = "Push notifications, device tokens, topics, preferences, and history")
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val deviceTokenService: DeviceTokenService,
    private val notificationService: NotificationService,
    private val topicService: TopicService,
    private val notificationPreferenceService: NotificationPreferenceService
) {

    // --- Token management ---

    @PostMapping("/tokens")
    fun registerToken(
        @Valid @RequestBody request: RegisterTokenRequest,
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<DeviceTokenResponse> {
        val userId = UUID.fromString(jwt.subject)
        val platform = Platform.valueOf(request.platform.uppercase())
        val token = deviceTokenService.register(userId, platform, request.fcmToken, request.deviceId)
        return ResponseEntity.status(201).body(DeviceTokenResponse.from(token))
    }

    @GetMapping("/tokens")
    fun listTokens(@Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt): ResponseEntity<List<DeviceTokenResponse>> {
        val userId = UUID.fromString(jwt.subject)
        val tokens = deviceTokenService.listByUser(userId).map { DeviceTokenResponse.from(it) }
        return ResponseEntity.ok(tokens)
    }

    @DeleteMapping("/tokens/{deviceId}")
    fun deleteToken(
        @PathVariable deviceId: String,
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Void> {
        val userId = UUID.fromString(jwt.subject)
        deviceTokenService.deleteByDeviceId(userId, deviceId)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/tokens")
    fun deleteAllTokens(@Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt): ResponseEntity<Void> {
        val userId = UUID.fromString(jwt.subject)
        deviceTokenService.deleteAllByUser(userId)
        return ResponseEntity.noContent().build()
    }

    // --- Send notifications ---

    @PostMapping("/send/token")
    fun sendToToken(
        @Valid @RequestBody request: SendToTokenRequest,
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Map<String, Any>> {
        val userId = UUID.fromString(jwt.subject)
        val historyId = notificationService.sendToToken(userId, request.token, request.title, request.body, request.data)
            ?: return ResponseEntity.ok(mapOf("skipped" to true, "reason" to "PUSH notifications disabled"))
        return ResponseEntity.accepted().body(mapOf("notificationId" to historyId))
    }

    @PostMapping("/send/multicast")
    fun sendMulticast(
        @Valid @RequestBody request: SendMulticastRequest,
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Map<String, Any>> {
        val userId = UUID.fromString(jwt.subject)
        val historyId = notificationService.sendMulticast(userId, request.tokens, request.title, request.body, request.data)
            ?: return ResponseEntity.ok(mapOf("skipped" to true, "reason" to "PUSH notifications disabled"))
        return ResponseEntity.accepted().body(mapOf("notificationId" to historyId))
    }

    @PostMapping("/send/topic")
    fun sendToTopic(
        @Valid @RequestBody request: SendToTopicRequest,
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Map<String, Any>> {
        val userId = UUID.fromString(jwt.subject)
        topicService.validateTopicExists(request.topic)
        val historyId = notificationService.sendToTopic(userId, request.topic, request.title, request.body, request.data)
            ?: return ResponseEntity.ok(mapOf("skipped" to true, "reason" to "PUSH notifications disabled"))
        return ResponseEntity.accepted().body(mapOf("notificationId" to historyId))
    }

    // --- Topic subscriptions ---

    @PostMapping("/topics/{name}/subscribe")
    fun subscribe(
        @PathVariable name: String,
        @Valid @RequestBody request: TopicSubscribeRequest,
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Void> {
        topicService.subscribe(request.token, name)
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/topics/{name}/subscribe")
    fun unsubscribe(
        @PathVariable name: String,
        @Valid @RequestBody request: TopicSubscribeRequest,
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Void> {
        topicService.unsubscribe(request.token, name)
        return ResponseEntity.ok().build()
    }

    // --- Preferences ---

    @GetMapping("/preferences")
    fun getPreferences(@Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt): ResponseEntity<NotificationPreferenceResponse> {
        val userId = UUID.fromString(jwt.subject)
        val prefs = notificationPreferenceService.getPreferences(userId)
        return ResponseEntity.ok(
            NotificationPreferenceResponse(
                push = prefs[NotificationChannel.PUSH] ?: true,
                email = prefs[NotificationChannel.EMAIL] ?: true
            )
        )
    }

    @PutMapping("/preferences")
    fun updatePreferences(
        @Valid @RequestBody request: NotificationPreferenceRequest,
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<NotificationPreferenceResponse> {
        val userId = UUID.fromString(jwt.subject)
        val updates = mutableMapOf<NotificationChannel, Boolean>()
        request.push?.let { updates[NotificationChannel.PUSH] = it }
        request.email?.let { updates[NotificationChannel.EMAIL] = it }
        notificationPreferenceService.updatePreferences(userId, updates)
        val prefs = notificationPreferenceService.getPreferences(userId)
        return ResponseEntity.ok(
            NotificationPreferenceResponse(
                push = prefs[NotificationChannel.PUSH] ?: true,
                email = prefs[NotificationChannel.EMAIL] ?: true
            )
        )
    }

    // --- History ---

    @GetMapping("/history")
    fun getHistory(
        @RequestParam(required = false) cursor: UUID?,
        @RequestParam(defaultValue = "20") size: Int,
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<List<NotificationHistoryResponse>> {
        val userId = UUID.fromString(jwt.subject)
        val history = notificationService.getHistory(userId, cursor, size.coerceIn(1, 100))
        return ResponseEntity.ok(history.map { NotificationHistoryResponse.from(it) })
    }
}
