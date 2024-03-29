package sh.nhp.kuribochat

import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import kotlin.system.exitProcess

suspend fun main() {
    val discordToken = System.getenv("DISCORD_TOKEN")
    if (discordToken == null) {
        println("Please set the DISCORD_TOKEN environment variable.")
        exitProcess(1)
    }
    val discord = Kord(discordToken)
    // Reference this here to run its constructor before it's actually used
    // Otherwise failing to read the required environment variables only gives an error when a user pings the bot
    AIChat

    discord.on<MessageCreateEvent> {
        if (message.author == discord.getSelf()) {
            return@on
        }

        if (message.referencedMessage != null) {
            val reference = message.referencedMessage!!
            if (reference.author == discord.getSelf()) {
                val convo = AIChat.manager.lookupReference(reference.id)

                if (convo != null) {
                    convo.addMessage(message)
                } else {
                    message.channel.createMessage {
                        content = "I'm sorry, it looks like you might have replied to a conversation I no longer remember. Try starting a new conversation with me by mentioning me in a message."
                        messageReference = message.id
                    }
                }
            }
        } else if (message.mentionedUserIds.contains(discord.selfId)) {
            val convo = AIChat.manager.newConversation()
            convo.addMessage(message)
        }
    }

    discord.login {
        @OptIn(PrivilegedIntent::class)
        intents += Intent.MessageContent
    }
}