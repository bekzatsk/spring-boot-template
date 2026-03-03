package kz.innlab.template.shared.model

import com.github.f4b6a3.uuid.UuidCreator
import jakarta.persistence.Column
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import jakarta.persistence.Transient
import org.springframework.data.domain.Persistable
import java.util.UUID

@MappedSuperclass
abstract class BaseEntity : Persistable<UUID> {

    @Id
    @Column(name = "id")
    private val _id: UUID = UuidCreator.getTimeOrderedEpoch()

    @Transient
    private var _new: Boolean = true

    override fun getId(): UUID = _id

    override fun isNew(): Boolean = _new

    @PostPersist
    @PostLoad
    fun markNotNew() {
        _new = false
    }
}
