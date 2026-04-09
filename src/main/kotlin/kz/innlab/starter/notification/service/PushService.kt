package kz.innlab.starter.notification.service

interface PushService {
    fun sendToToken(token: String, title: String, body: String, data: Map<String, String>): String?
    fun sendMulticast(tokens: List<String>, title: String, body: String, data: Map<String, String>): List<String>
    fun sendToTopic(topic: String, title: String, body: String, data: Map<String, String>): String?
    fun subscribeToTopic(tokens: List<String>, topic: String)
    fun unsubscribeFromTopic(tokens: List<String>, topic: String)
}
