package sh.nhp.kuribochat

import dev.kord.common.entity.Snowflake
import kotlinx.coroutines.CoroutineScope

class ConversationManager(private val scope: CoroutineScope) {
    private val conversations = mutableListOf<Conversation>()
    private val conversationLimit = 16

    fun newConversation(): Conversation {
        val convo = Conversation(scope)
        conversations.add(convo)

        if (conversations.count() > conversationLimit) {
            conversations.removeAt(0)
        }

        return convo
    }

    fun lookupReference(id: Snowflake): Conversation? {
        return conversations.find { it.references(id) }
    }
}