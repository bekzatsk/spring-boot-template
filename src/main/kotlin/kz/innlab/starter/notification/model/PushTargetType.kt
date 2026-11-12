package kz.innlab.starter.notification.model

/**
 * What a registered device identifier is. FCM addresses an app install either by its
 * registration token or by its Firebase Installation ID; the two are different values and
 * cannot be swapped for one another, so a registration records which one the client sent.
 */
enum class PushTargetType {
    /** FCM registration token, from `FirebaseMessaging.getToken()` on the client. */
    TOKEN,

    /** Firebase Installation ID, from `FirebaseInstallations.getId()` on the client. */
    FID
}
