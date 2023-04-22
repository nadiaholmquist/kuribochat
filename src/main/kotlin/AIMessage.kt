package sh.nhp.kuribochat

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingRegistry
import com.knuddels.jtokkit.api.ModelType

sealed class AIMessage(val content: String) {
    class Bot(content: String) : AIMessage(content)
    class User(content: String, val userTag: String) : AIMessage(content) {
        val userName by lazy { userTag.substring(0 until userTag.lastIndexOf('#')) }
    }
    class System(content: String) : AIMessage(content)

    val tokenCount by lazy {
        when (this) {
            is User -> encoding.countTokens("${this.userName}: ${this.content}")
            else -> encoding.countTokens(this.content)
        }
        encoding.countTokens(this.content)
    }

    override fun toString(): String {
        when (this) {
            is Bot -> return "Bot: $content"
            is User -> return "$userName: $content"
            is System -> return "System: $content"
        }
    }

    @OptIn(BetaOpenAI::class)
    fun toChatMessage() = ChatMessage(
        when (this) {
            is Bot -> ChatRole.Assistant
            is User -> ChatRole.User
            is System -> ChatRole.System
        },
        content, if (this is User) { userName } else { null }
    )

    companion object {
        private val encodingRegistry = Encodings.newDefaultEncodingRegistry()
        private val encoding: Encoding = encodingRegistry.getEncodingForModel(ModelType.GPT_3_5_TURBO)
    }
}

@OptIn(BetaOpenAI::class)
fun List<AIMessage>.asChatMessages() = asSequence().map { it.toChatMessage() }