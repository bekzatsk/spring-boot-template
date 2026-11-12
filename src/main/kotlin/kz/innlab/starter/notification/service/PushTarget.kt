package kz.innlab.starter.notification.service

import kz.innlab.starter.notification.model.PushTargetType

/** One app install to push to: its identifier and whether that is a registration token or an FID. */
data class PushTarget(
    val value: String,
    val type: PushTargetType = PushTargetType.TOKEN
)
