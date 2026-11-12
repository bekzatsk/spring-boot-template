package kz.innlab.starter.notification.service

import kz.innlab.starter.notification.model.PushTargetType

interface PushService {
    /** Sends to one FCM registration token. */
    fun sendToToken(token: String, title: String, body: String, data: Map<String, String>): String?

    /**
     * Sends to one Firebase Installation ID. The default refuses, so an implementation written
     * before FIDs existed keeps compiling and fails loudly instead of misrouting an FID as a token.
     */
    fun sendToFid(fid: String, title: String, body: String, data: Map<String, String>): String? =
        throw UnsupportedOperationException("${this::class.simpleName} does not support Firebase Installation IDs")

    /** Sends to registration tokens; returns the tokens FCM reported as stale. */
    fun sendMulticast(tokens: List<String>, title: String, body: String, data: Map<String, String>): List<String>

    /**
     * Sends to a mix of registration tokens and FIDs; returns the identifiers FCM reported as
     * stale. The default handles tokens only, through [sendMulticast].
     */
    fun sendMulticastToTargets(targets: List<PushTarget>, title: String, body: String, data: Map<String, String>): List<String> {
        if (targets.any { it.type != PushTargetType.TOKEN }) {
            throw UnsupportedOperationException("${this::class.simpleName} does not support Firebase Installation IDs")
        }
        return sendMulticast(targets.map { it.value }, title, body, data)
    }

    fun sendToTopic(topic: String, title: String, body: String, data: Map<String, String>): String?

    /** Topic subscription takes registration tokens only; FCM has no FID variant. */
    fun subscribeToTopic(tokens: List<String>, topic: String)
    fun unsubscribeFromTopic(tokens: List<String>, topic: String)
}
