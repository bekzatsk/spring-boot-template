package kz.innlab.starter.authentication.service

interface TelegramBotService {
    fun sendMessage(chatId: Long, text: String)

    /**
     * Sends [text] with a one-button reply keyboard that shares the user's own contact
     * (`request_contact`). The default sends plain text, so an implementation written before this
     * method existed still compiles but cannot collect the phone.
     */
    fun requestContact(chatId: Long, text: String, buttonText: String) = sendMessage(chatId, text)

    /** Sends [text] and removes the reply keyboard left by [requestContact]. */
    fun sendMessageRemovingKeyboard(chatId: Long, text: String) = sendMessage(chatId, text)
}
