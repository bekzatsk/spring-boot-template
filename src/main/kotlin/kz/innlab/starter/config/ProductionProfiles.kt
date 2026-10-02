package kz.innlab.starter.config

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.core.type.AnnotatedTypeMetadata

/**
 * Which active profiles count as production for the fail-fast guards.
 *
 * The guards used to match the literal `prod` profile only, so a deployment running as `production`
 * started with an in-memory signing key and no safety checks at all. The list is configurable through
 * `app.security.production-profiles` for deployments that name their profile differently. It is read
 * straight from the environment because conditions run before configuration properties are bound.
 */
object ProductionProfiles {
    const val PROPERTY = "app.security.production-profiles"
    private const val DEFAULT = "prod,production"

    fun isActive(environment: Environment): Boolean {
        val names = environment.getProperty(PROPERTY, DEFAULT)
            .split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return names.isNotEmpty() && environment.acceptsProfiles(Profiles.of(*names.toTypedArray()))
    }
}

/** Matches when one of [ProductionProfiles] is active. */
class OnProductionProfileCondition : Condition {
    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean =
        ProductionProfiles.isActive(context.environment)
}
