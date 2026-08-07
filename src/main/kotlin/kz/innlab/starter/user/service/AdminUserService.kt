package kz.innlab.starter.user.service

import kz.innlab.starter.shared.error.ResourceNotFoundException
import kz.innlab.starter.shared.util.normalizeToE164
import kz.innlab.starter.user.dto.UserSummaryResponse
import kz.innlab.starter.user.model.AdminAuditLog
import kz.innlab.starter.user.model.AuthProvider
import kz.innlab.starter.user.model.RequiredAction
import kz.innlab.starter.user.model.Role
import kz.innlab.starter.user.model.User
import kz.innlab.starter.user.repository.AdminAuditLogRepository
import kz.innlab.starter.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AdminUserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val refreshTokenRevoker: RefreshTokenRevoker,
    private val auditLogRepository: AdminAuditLogRepository
) {

    companion object {
        private val log = LoggerFactory.getLogger(AdminUserService::class.java)
    }

    @Transactional(readOnly = true)
    fun list(query: String?, pageable: Pageable): Page<UserSummaryResponse> =
        userRepository.searchSummaries(escapeLikeWildcards(query), pageable)

    /**
     * `%` and `_` are LIKE metacharacters. Unescaped, a search for "%" matches every row and
     * forces a full scan; the queries declare ESCAPE '\' so the escaped input is taken literally.
     */
    private fun escapeLikeWildcards(query: String?): String? = query
        ?.replace("\\", "\\\\")
        ?.replace("%", "\\%")
        ?.replace("_", "\\_")

    @Transactional(readOnly = true)
    fun findById(id: UUID): User =
        userRepository.findById(id).orElseThrow { ResourceNotFoundException("User not found") }

    @Transactional
    fun updatePassword(adminId: UUID, targetId: UUID, newPassword: String, temporary: Boolean): User {
        val user = findById(targetId)
        user.passwordHash = passwordEncoder.encode(newPassword)
        user.linkProvider(AuthProvider.LOCAL)
        user.passwordTemporary = temporary
        if (temporary) {
            user.requiredActions.add(RequiredAction.UPDATE_PASSWORD)
        } else {
            user.requiredActions.remove(RequiredAction.UPDATE_PASSWORD)
        }
        val saved = userRepository.save(user)
        refreshTokenRevoker.revokeAllFor(saved)
        audit(adminId, "UPDATE_PASSWORD", targetId, after = "temporary=$temporary")
        return saved
    }

    @Transactional
    fun updateEmail(adminId: UUID, targetId: UUID, newEmail: String): User {
        val user = findById(targetId)
        if (user.email == newEmail) return user

        val existing = userRepository.findByEmail(newEmail)
        if (existing != null && existing.id != targetId) {
            throw IllegalStateException("Email already in use")
        }

        val before = user.email
        user.email = newEmail
        val saved = userRepository.save(user)
        audit(adminId, "UPDATE_EMAIL", targetId, before = before, after = newEmail)
        return saved
    }

    @Transactional
    fun updatePhone(adminId: UUID, targetId: UUID, rawPhone: String): User {
        val phoneE164 = normalizeToE164(rawPhone)
        val user = findById(targetId)
        if (user.phone == phoneE164) return user

        val existing = userRepository.findByPhone(phoneE164)
        if (existing != null && existing.id != targetId) {
            throw IllegalStateException("Phone number already in use")
        }

        val before = user.phone
        user.phone = phoneE164
        user.linkProvider(AuthProvider.LOCAL)
        val saved = userRepository.save(user)
        audit(adminId, "UPDATE_PHONE", targetId, before = before, after = phoneE164)
        return saved
    }

    @Transactional
    fun updateProfile(adminId: UUID, targetId: UUID, name: String?, picture: String?): User {
        val user = findById(targetId)
        val before = "name=${user.name},picture=${user.picture}"
        user.name = name
        user.picture = picture
        val saved = userRepository.save(user)
        audit(adminId, "UPDATE_PROFILE", targetId, before = before, after = "name=$name,picture=$picture")
        return saved
    }

    @Transactional
    fun updateRoles(adminId: UUID, targetId: UUID, roles: Set<Role>): User {
        val user = findById(targetId)
        val wasAdmin = Role.ADMIN in user.roles
        val willBeAdmin = Role.ADMIN in roles

        if (wasAdmin && !willBeAdmin) {
            ensureNotLastAdmin(targetId)
            if (adminId == targetId) {
                throw IllegalStateException("Admin cannot remove ADMIN role from self")
            }
        }

        val before = user.roles.map { it.name }.sorted().toString()
        user.roles.clear()
        user.roles.addAll(roles)
        val saved = userRepository.save(user)
        audit(adminId, "UPDATE_ROLES", targetId,
            before = before,
            after = roles.map { it.name }.sorted().toString())
        return saved
    }

    @Transactional
    fun deleteUser(adminId: UUID, targetId: UUID) {
        if (adminId == targetId) {
            throw IllegalStateException("Admin cannot delete self")
        }
        val user = findById(targetId)
        if (Role.ADMIN in user.roles) {
            ensureNotLastAdmin(targetId)
        }
        refreshTokenRevoker.revokeAllFor(user)
        userRepository.delete(user)
        audit(adminId, "DELETE_USER", targetId, before = "email=${user.email}")
    }

    private fun ensureNotLastAdmin(targetId: UUID) {
        val adminCount = userRepository.countAdmins()
        val target = userRepository.findById(targetId).orElse(null)
        val targetIsAdmin = target != null && Role.ADMIN in target.roles
        if (targetIsAdmin && adminCount <= 1) {
            throw IllegalStateException("Cannot remove last ADMIN — at least one admin must remain")
        }
    }

    private fun audit(adminId: UUID, action: String, targetId: UUID?, before: String? = null, after: String? = null) {
        auditLogRepository.save(AdminAuditLog(
            adminId = adminId,
            action = action,
            targetId = targetId,
            before = before,
            after = after
        ))
        log.info("ADMIN_AUDIT admin={} action={} target={} before={} after={}",
            adminId, action, targetId, before, after)
    }
}
