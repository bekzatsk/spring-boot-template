package kz.innlab.starter.user.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kz.innlab.starter.user.dto.AdminCreateUserRequest
import kz.innlab.starter.user.dto.AdminUpdateEmailRequest
import kz.innlab.starter.user.dto.AdminUpdateNameRequest
import kz.innlab.starter.user.dto.AdminUpdatePasswordRequest
import kz.innlab.starter.user.dto.AdminUpdatePhoneRequest
import kz.innlab.starter.user.dto.AdminUpdateRolesRequest
import kz.innlab.starter.user.dto.PageResponse
import kz.innlab.starter.user.dto.UserProfileResponse
import kz.innlab.starter.user.dto.UserSummaryResponse
import kz.innlab.starter.user.service.AdminUserService
import kz.innlab.starter.user.service.UserService
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@Tag(name = "Admin Users", description = "Admin-only user management")
@RequestMapping("/api/v1/admin/users")
class AdminUserController(
    private val userService: UserService,
    private val adminUserService: AdminUserService
) {

    @Operation(summary = "List users (paginated, searchable by email/name/phone). Returns brief representation.")
    @GetMapping
    fun list(
        @RequestParam(required = false) q: String?,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<PageResponse<UserSummaryResponse>> {
        val page = adminUserService.list(q, pageable)
        return ResponseEntity.ok(PageResponse.from(page, UserSummaryResponse::from))
    }

    @Operation(summary = "Get a user by id.")
    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): ResponseEntity<UserProfileResponse> {
        val user = adminUserService.findById(id)
        return ResponseEntity.ok(UserProfileResponse.from(user))
    }

    @Operation(summary = "Create a user (admin). Bypasses registration.enabled flag.")
    @PostMapping
    fun createUser(@Valid @RequestBody request: AdminCreateUserRequest): ResponseEntity<UserProfileResponse> {
        val user = userService.createUserByAdmin(
            email = request.email,
            rawPassword = request.password,
            name = request.name,
            roles = request.roles,
            temporary = request.temporary
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(UserProfileResponse.from(user))
    }

    @Operation(summary = "Reset a user's password (admin). Revokes all refresh tokens.")
    @PatchMapping("/{id}/password")
    fun updatePassword(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
        @Valid @RequestBody request: AdminUpdatePasswordRequest
    ): ResponseEntity<UserProfileResponse> {
        val adminId = UUID.fromString(jwt.subject)
        val user = adminUserService.updatePassword(adminId, id, request.newPassword, request.temporary)
        return ResponseEntity.ok(UserProfileResponse.from(user))
    }

    @Operation(summary = "Change a user's email (admin). Skips OTP verification.")
    @PatchMapping("/{id}/email")
    fun updateEmail(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
        @Valid @RequestBody request: AdminUpdateEmailRequest
    ): ResponseEntity<UserProfileResponse> {
        val adminId = UUID.fromString(jwt.subject)
        val user = adminUserService.updateEmail(adminId, id, request.email)
        return ResponseEntity.ok(UserProfileResponse.from(user))
    }

    @Operation(summary = "Change a user's phone (admin). Skips SMS OTP. Normalizes to E.164.")
    @PatchMapping("/{id}/phone")
    fun updatePhone(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
        @Valid @RequestBody request: AdminUpdatePhoneRequest
    ): ResponseEntity<UserProfileResponse> {
        val adminId = UUID.fromString(jwt.subject)
        val user = adminUserService.updatePhone(adminId, id, request.phone)
        return ResponseEntity.ok(UserProfileResponse.from(user))
    }

    @Operation(summary = "Update a user's name and picture (admin).")
    @PatchMapping("/{id}/profile")
    fun updateProfile(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
        @RequestBody request: AdminUpdateNameRequest
    ): ResponseEntity<UserProfileResponse> {
        val adminId = UUID.fromString(jwt.subject)
        val user = adminUserService.updateProfile(adminId, id, request.name, request.picture)
        return ResponseEntity.ok(UserProfileResponse.from(user))
    }

    @Operation(summary = "Update a user's roles (admin). Prevents last-admin lockout and self-demotion.")
    @PatchMapping("/{id}/roles")
    fun updateRoles(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
        @Valid @RequestBody request: AdminUpdateRolesRequest
    ): ResponseEntity<UserProfileResponse> {
        val adminId = UUID.fromString(jwt.subject)
        val user = adminUserService.updateRoles(adminId, id, request.roles)
        return ResponseEntity.ok(UserProfileResponse.from(user))
    }

    @Operation(summary = "Delete a user (admin). Prevents last-admin lockout and self-delete.")
    @DeleteMapping("/{id}")
    fun deleteUser(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID
    ): ResponseEntity<Void> {
        val adminId = UUID.fromString(jwt.subject)
        adminUserService.deleteUser(adminId, id)
        return ResponseEntity.noContent().build()
    }
}
