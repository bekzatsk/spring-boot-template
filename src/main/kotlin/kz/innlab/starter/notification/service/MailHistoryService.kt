package kz.innlab.starter.notification.service

import kz.innlab.starter.notification.model.MailHistory
import kz.innlab.starter.notification.repository.MailHistoryRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Read side of mail history. Exists so MailController does not reach into the repository
 * directly — cursor pagination is service-layer logic, and the write path already goes
 * through [MailService].
 */
@Service
class MailHistoryService(
    private val mailHistoryRepository: MailHistoryRepository
) {

    @Transactional(readOnly = true)
    fun getHistory(userId: UUID, cursor: UUID?, size: Int): List<MailHistory> {
        val pageable = PageRequest.of(0, size)
        return if (cursor == null) {
            mailHistoryRepository.findByUserIdLatest(userId, pageable)
        } else {
            mailHistoryRepository.findByUserIdBeforeCursor(userId, cursor, pageable)
        }
    }
}
