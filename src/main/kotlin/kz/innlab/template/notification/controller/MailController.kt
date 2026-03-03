package kz.innlab.template.notification.controller

import jakarta.validation.Valid
import kz.innlab.template.notification.dto.EmailMessageResponse
import kz.innlab.template.notification.dto.InboxMessageResponse
import kz.innlab.template.notification.dto.MailHistoryResponse
import kz.innlab.template.notification.dto.SendEmailRequest
import kz.innlab.template.notification.repository.MailHistoryRepository
import kz.innlab.template.notification.service.EmailAttachment
import kz.innlab.template.notification.service.ImapService
import kz.innlab.template.notification.service.MailService
import org.springframework.data.domain.PageRequest
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
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/v1/mail")
class MailController(
    private val mailService: MailService,
    private val imapService: ImapService,
    private val mailHistoryRepository: MailHistoryRepository
) {

    // --- Send email ---

    @PostMapping("/send")
    fun sendEmail(
        @Valid @RequestBody request: SendEmailRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Map<String, UUID>> {
        val userId = UUID.fromString(jwt.subject)
        val mailId = mailService.sendEmail(
            userId = userId,
            to = request.to,
            subject = request.subject,
            textBody = request.textBody,
            htmlBody = request.htmlBody
        )
        return ResponseEntity.accepted().body(mapOf("mailId" to mailId))
    }

    @PostMapping("/send/with-attachments")
    fun sendEmailWithAttachments(
        @Valid @RequestPart("email") request: SendEmailRequest,
        @RequestPart("files") files: List<MultipartFile>,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Map<String, UUID>> {
        val userId = UUID.fromString(jwt.subject)
        val attachments = files.map { file ->
            EmailAttachment(
                filename = file.originalFilename ?: "attachment",
                contentType = file.contentType ?: "application/octet-stream",
                bytes = file.bytes
            )
        }
        val mailId = mailService.sendEmail(
            userId = userId,
            to = request.to,
            subject = request.subject,
            textBody = request.textBody,
            htmlBody = request.htmlBody,
            attachments = attachments
        )
        return ResponseEntity.accepted().body(mapOf("mailId" to mailId))
    }

    // --- IMAP inbox ---

    @GetMapping("/inbox")
    fun listInbox(
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "false") unreadOnly: Boolean,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Map<String, Any>> {
        val page = imapService.listInbox(offset, size.coerceIn(1, 100), unreadOnly)
        return ResponseEntity.ok(
            mapOf(
                "messages" to page.messages.map { InboxMessageResponse.from(it) },
                "total" to page.total,
                "offset" to page.offset,
                "size" to page.size
            )
        )
    }

    @GetMapping("/inbox/{messageNumber}")
    fun getMessage(
        @PathVariable messageNumber: Int,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<EmailMessageResponse> {
        val msg = imapService.getMessage(messageNumber)
        return ResponseEntity.ok(EmailMessageResponse.from(msg))
    }

    @PutMapping("/inbox/{messageNumber}/read")
    fun markRead(
        @PathVariable messageNumber: Int,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Void> {
        imapService.markRead(messageNumber)
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/inbox/{messageNumber}/read")
    fun markUnread(
        @PathVariable messageNumber: Int,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Void> {
        imapService.markUnread(messageNumber)
        return ResponseEntity.ok().build()
    }

    // --- Mail history ---

    @GetMapping("/history")
    fun getHistory(
        @RequestParam(required = false) cursor: UUID?,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<List<MailHistoryResponse>> {
        val userId = UUID.fromString(jwt.subject)
        val pageable = PageRequest.of(0, size.coerceIn(1, 100))
        val history = if (cursor == null) {
            mailHistoryRepository.findByUserIdLatest(userId, pageable)
        } else {
            mailHistoryRepository.findByUserIdBeforeCursor(userId, cursor, pageable)
        }
        return ResponseEntity.ok(history.map { MailHistoryResponse.from(it) })
    }
}
