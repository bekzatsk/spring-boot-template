package kz.innlab.starter.user.repository

import kz.innlab.starter.user.model.AdminAuditLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AdminAuditLogRepository : JpaRepository<AdminAuditLog, UUID> {
    fun findByAdminIdOrderByCreatedAtDesc(adminId: UUID, pageable: Pageable): Page<AdminAuditLog>
    fun findByTargetIdOrderByCreatedAtDesc(targetId: UUID, pageable: Pageable): Page<AdminAuditLog>
}
