package kz.innlab.starter.user.model

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapKeyColumn
import jakarta.persistence.MapKeyEnumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import kz.innlab.starter.shared.model.BaseEntity
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
@Table(name = "users", schema = "auth")
class User(
    @Column(nullable = false)
    var email: String,
) : BaseEntity() {

    var name: String? = null

    var picture: String? = null

    @Column(name = "password_hash")
    var passwordHash: String? = null  // Only set for LOCAL email provider

    @Column(name = "phone", unique = true)
    var phone: String? = null  // E.164 format; set for LOCAL phone users

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(
        name = "user_providers",
        schema = "auth",
        joinColumns = [JoinColumn(name = "user_id")],
        uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "provider"])]
    )
    @Column(name = "provider")
    var providers: MutableSet<AuthProvider> = mutableSetOf()

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "user_provider_ids",
        schema = "auth",
        joinColumns = [JoinColumn(name = "user_id")],
        uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "provider"])]
    )
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "provider")
    @Column(name = "provider_id")
    var providerIds: MutableMap<AuthProvider, String> = mutableMapOf()

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "user_roles", schema = "auth", joinColumns = [JoinColumn(name = "user_id")])
    @Column(name = "role")
    var roles: MutableSet<Role> = mutableSetOf(Role.USER)

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is User) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
