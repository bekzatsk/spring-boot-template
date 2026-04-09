package kz.innlab.starter.notification.service

import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.FetchProfile
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.internet.InternetAddress
import jakarta.mail.search.FlagTerm
import kz.innlab.starter.config.MailProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Properties

@Service
class ImapService(private val mailProperties: MailProperties) {

    companion object {
        private val logger = LoggerFactory.getLogger(ImapService::class.java)
    }

    fun listInbox(offset: Int, size: Int, unreadOnly: Boolean): InboxPage {
        return withInbox(readWrite = false) { folder ->
            val messages = if (unreadOnly) {
                folder.search(FlagTerm(Flags(Flags.Flag.SEEN), false))
            } else {
                folder.messages
            }

            val total = messages.size

            // Fetch envelopes and flags for performance
            val fp = FetchProfile()
            fp.add(FetchProfile.Item.ENVELOPE)
            fp.add(FetchProfile.Item.FLAGS)
            folder.fetch(messages, fp)

            // Newest first: reverse the array and apply offset/size
            val sorted = messages.reversed()
            val page = sorted.drop(offset).take(size)

            val items = page.map { msg ->
                InboxMessage(
                    messageNumber = msg.messageNumber,
                    subject = msg.subject ?: "",
                    from = (msg.from?.firstOrNull() as? InternetAddress)?.address ?: "",
                    date = msg.sentDate?.toInstant(),
                    isRead = msg.flags.contains(Flags.Flag.SEEN),
                    hasAttachments = hasAttachments(msg)
                )
            }

            InboxPage(messages = items, total = total, offset = offset, size = size)
        }
    }

    fun getMessage(messageNumber: Int): EmailMessage {
        return withInbox(readWrite = false) { folder ->
            val msg = folder.getMessage(messageNumber)

            val textParts = mutableListOf<String>()
            val htmlParts = mutableListOf<String>()
            val attachments = mutableListOf<AttachmentMeta>()
            extractParts(msg, textParts, htmlParts, attachments)

            val toAddresses = msg.getRecipients(jakarta.mail.Message.RecipientType.TO)
                ?.mapNotNull { (it as? InternetAddress)?.address }
                ?: emptyList()

            EmailMessage(
                messageNumber = msg.messageNumber,
                subject = msg.subject ?: "",
                from = (msg.from?.firstOrNull() as? InternetAddress)?.address ?: "",
                to = toAddresses,
                date = msg.sentDate?.toInstant(),
                textBody = textParts.joinToString("\n").ifEmpty { null },
                htmlBody = htmlParts.joinToString("\n").ifEmpty { null },
                attachments = attachments,
                isRead = msg.flags.contains(Flags.Flag.SEEN)
            )
        }
    }

    fun markRead(messageNumber: Int) {
        withInbox(readWrite = true) { folder ->
            folder.getMessage(messageNumber).setFlag(Flags.Flag.SEEN, true)
        }
    }

    fun markUnread(messageNumber: Int) {
        withInbox(readWrite = true) { folder ->
            folder.getMessage(messageNumber).setFlag(Flags.Flag.SEEN, false)
        }
    }

    private fun <T> withInbox(readWrite: Boolean = false, block: (Folder) -> T): T {
        check(mailProperties.imap.host.isNotBlank()) { "IMAP not configured" }

        val protocol = if (mailProperties.imap.sslEnabled) "imaps" else "imap"
        val props = Properties().apply {
            setProperty("mail.$protocol.host", mailProperties.imap.host)
            setProperty("mail.$protocol.port", mailProperties.imap.port.toString())
        }

        val session = Session.getInstance(props)
        var store: Store? = null
        var folder: Folder? = null

        try {
            store = session.getStore(protocol)
            store.connect(
                mailProperties.imap.host,
                mailProperties.imap.port,
                mailProperties.imap.username,
                mailProperties.imap.password
            )
            folder = store.getFolder("INBOX")
            folder.open(if (readWrite) Folder.READ_WRITE else Folder.READ_ONLY)
            return block(folder)
        } finally {
            try {
                folder?.close(false)
            } catch (e: Exception) {
                logger.debug("Error closing IMAP folder: {}", e.message)
            }
            try {
                store?.close()
            } catch (e: Exception) {
                logger.debug("Error closing IMAP store: {}", e.message)
            }
        }
    }

    private fun hasAttachments(part: Part): Boolean {
        if (part.isMimeType("multipart/*")) {
            val mp = part.content as Multipart
            for (i in 0 until mp.count) {
                val bodyPart = mp.getBodyPart(i)
                if (bodyPart.disposition != null) return true
                if (bodyPart.isMimeType("multipart/*") && hasAttachments(bodyPart)) return true
            }
        }
        return false
    }

    private fun extractParts(
        part: Part,
        textParts: MutableList<String>,
        htmlParts: MutableList<String>,
        attachments: MutableList<AttachmentMeta>
    ) {
        if (part.isMimeType("multipart/*")) {
            val mp = part.content as Multipart
            for (i in 0 until mp.count) {
                extractParts(mp.getBodyPart(i), textParts, htmlParts, attachments)
            }
        } else if (part.isMimeType("text/plain") && part.disposition == null) {
            textParts.add(part.content as String)
        } else if (part.isMimeType("text/html") && part.disposition == null) {
            htmlParts.add(part.content as String)
        } else if (part.disposition != null) {
            attachments.add(
                AttachmentMeta(
                    filename = part.fileName ?: "unknown",
                    contentType = part.contentType.split(";")[0],
                    size = part.size
                )
            )
        }
    }
}

data class InboxMessage(
    val messageNumber: Int,
    val subject: String,
    val from: String,
    val date: Instant?,
    val isRead: Boolean,
    val hasAttachments: Boolean
)

data class InboxPage(
    val messages: List<InboxMessage>,
    val total: Int,
    val offset: Int,
    val size: Int
)

data class EmailMessage(
    val messageNumber: Int,
    val subject: String,
    val from: String,
    val to: List<String>,
    val date: Instant?,
    val textBody: String?,
    val htmlBody: String?,
    val attachments: List<AttachmentMeta>,
    val isRead: Boolean
)

data class AttachmentMeta(
    val filename: String,
    val contentType: String,
    val size: Int
)
