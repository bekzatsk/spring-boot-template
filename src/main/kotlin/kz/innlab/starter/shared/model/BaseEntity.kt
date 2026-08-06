package kz.innlab.starter.shared.model

import com.github.f4b6a3.uuid.UuidCreator
import jakarta.persistence.Column
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import jakarta.persistence.Transient
import jakarta.persistence.Version
import org.springframework.data.domain.Persistable
import java.util.UUID

@MappedSuperclass
abstract class BaseEntity : Persistable<UUID> {

    @Id
    @Column(name = "id")
    private val _id: UUID = UuidCreator.getTimeOrderedEpoch()

    @Transient
    private var _new: Boolean = true

    /**
     * Optimistic locking. Users in particular are updated from several independent flows
     * (self-service, admin, provider linking); without a version the later write silently
     * overwrote the earlier one.
     */
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0

    override fun getId(): UUID = _id

    override fun isNew(): Boolean = _new

    @PostPersist
    @PostLoad
    fun markNotNew() {
        _new = false
    }

    /**
     * Identity is the id, never the field values: JPA entities are mutable and a value-based
     * equals breaks the moment an entity sitting in a Set is modified. Declared once here
     * instead of being copy-pasted into each entity (and missing from half of them).
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BaseEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
