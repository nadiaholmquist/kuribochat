package sh.nhp.kuribochat

import dev.kord.common.entity.MessageFlag
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.channel.withTyping
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Message
import dev.kord.rest.builder.message.create.allowedMentions
import dev.kord.rest.builder.message.modify.allowedMentions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

class Conversation(scope: CoroutineScope) {
    private val queue = Channel<Message>(Channel.UNLIMITED)

    private val memoryLength = 10
    private val context = mutableListOf<AIMessage>()
    private val references = mutableListOf<Snowflake>()

    init {
        scope.launch {
            for (message in queue) {
                if (message.author == null) {
                    continue
                }

                var contextMessage = message.content
                if (contextMessage.startsWith("<@")) {
                    contextMessage = contextMessage.substringAfter(">")
                }

                context.add(AIMessage.User(contextMessage.trim(), message.author!!.username))

                while (context.count() > memoryLength) {
                    context.removeAt(0)
                }

                var gatheredContent = ""
                var newMessage: Message? = null

                message.channel.withTyping {
                    AIChat.generateResponse(context)
                        .onEach {
                            gatheredContent += it

                            if (newMessage == null) {
                                newMessage = message.channel.createMessage {
                                    content = gatheredContent
                                    messageReference = message.id
                                    allowedMentions {}
                                }

                                references.add(newMessage!!.id)
                            } else {
                                newMessage = newMessage!!.edit {
                                    content = gatheredContent
                                    allowedMentions {}
                                }
                            }
                        }.catch {
                            if (newMessage != null) {
                                newMessage!!.delete()
                                references.removeLast()
                            }

                            message.channel.createMessage {
                                content = "Uh-oh, looks like the bot did a fucky wucky!!\n```\n$it\n```"
                                messageReference = message.id
                            }
                        }.collect()
                }

                if (newMessage == null) continue
                if (references.count() > memoryLength) {
                    references.removeAt(0)
                }

                context.add(AIMessage.Bot(gatheredContent))

                while (context.count() > memoryLength) {
                    context.removeAt(0)
                }
            }
        }
    }

    suspend fun addMessage(message: Message) {
        queue.send(message)
    }

    fun references(id: Snowflake): Boolean {
        return references.contains(id)
    }

    fun getContext(): List<AIMessage> = context

    /*private suspend fun generateResponse(): String {
        val response = AIChat.generateResponse(context)

        return if (response.isSuccess) {
            response.getOrThrow()
        } else {
            "Error: Nadia probably broke something!\n```\n${response.exceptionOrNull()}\n```"
        }
    }*/
}