package sh.nhp.kuribochat

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.channel.withTyping
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Message
import dev.kord.rest.builder.message.create.allowedMentions
import dev.kord.rest.builder.message.modify.allowedMentions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class Conversation(scope: CoroutineScope) {
    private val queue = Channel<Message>(Channel.UNLIMITED)

    private val memoryLength = 10
    private val context = mutableListOf<Context>()

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

                val userContext = Context(
                    AIMessage.User(contextMessage.trim(), message.author!!.username),
                    listOf(message.id)
                )

                context.add(userContext)

                message.channel.withTyping {
                    var fullResponse = ""
                    var responsePart = ""
                    val newMessages = mutableListOf<Message>()

                    AIChat.generateResponse(context.aiMessages().toList())
                        .onEach { chunk ->
                            if (newMessages.isEmpty()) {
                                responsePart += chunk

                                newMessages += message.channel.createMessage {
                                    content = responsePart
                                    messageReference = message.id
                                    allowedMentions {}
                                }
                            } else {
                                // Discord only allows up to 2000 characters per message
                                // Maybe split this more intelligently in the future
                                if ((responsePart.length + chunk.length) > 2000) {
                                    val last = newMessages.last()
                                    val edited = last.edit {
                                        content = responsePart
                                        allowedMentions {}
                                    }
                                    newMessages[newMessages.lastIndex] = edited

                                    newMessages += message.channel.createMessage {
                                        content = chunk
                                        allowedMentions {}
                                    }

                                    fullResponse += responsePart
                                    responsePart = ""
                                }

                                responsePart += chunk

                                val last = newMessages.last()
                                val edited = last.edit {
                                    content = responsePart
                                    allowedMentions {}
                                }
                                newMessages[newMessages.lastIndex] = edited
                            }
                        }.onCompletion {
                            fullResponse += responsePart
                            context += Context(
                                AIMessage.Bot(fullResponse),
                                newMessages.map { it.id }
                            )
                        }.catch {
                            messages.onEach { it.delete() }

                            message.channel.createMessage {
                                content = "Uh-oh, looks like the bot did a fucky wucky!!\n```\n$it\n```"
                                messageReference = message.id
                            }
                        }.collect()
                }

                // Prune the message context that likely will not be sent to the bot anyway
                if (context.sumOf { it.aiMessage.tokenCount } > 3072) {
                    context.removeFirst()
                }
            }
        }
    }

    suspend fun addMessage(message: Message) {
        queue.send(message)
    }

    fun getAIMessages() =
        context.aiMessages()

    fun references(id: Snowflake): Boolean {
        val contextMessage = context.find {
            it.discordMessages.contains(id)
        }
        return contextMessage != null
    }

    private data class Context(
        val aiMessage: AIMessage,
        val discordMessages: List<Snowflake>
    )

    private fun List<Context>.aiMessages() = asSequence().map { it.aiMessage }
}