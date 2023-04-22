package sh.nhp.kuribochat

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.*
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.exception.OpenAIException
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.yield
import java.io.File
import kotlin.system.exitProcess

@OptIn(DelicateCoroutinesApi::class, BetaOpenAI::class)
object AIChat {
    private val botName: String
    private val defaultPrompt: AIMessage.System

    private val openAI: OpenAI
    val manager = ConversationManager(GlobalScope)

    init {
        val apiKey = System.getenv("OPENAI_API_KEY")
        if (apiKey == null) {
            println("OPENAI_API_KEY environment variable not set")
            exitProcess(1)
        }

        val name = System.getenv("KURIBO_BOT_NAME")
        if (name == null) {
            println("KURIBO_BOT_NAME variable was not set.")
            exitProcess(1)
        }
        botName = name

        val promptFileName = System.getenv("PROMPT_FILE")
        if (promptFileName == null) {
            println("PROMPT_FILE environment variable not set")
            exitProcess(1)
        }

        try {
            val promptStr = File(promptFileName)
                .readText()
                .replace("{BOT}", botName)
            defaultPrompt = AIMessage.System(promptStr)
        } catch (e: Exception) {
            println("Could not read prompt file: $e")
            exitProcess(1)
        }

        openAI = OpenAI(apiKey)
    }

    suspend fun generateResponse(context: List<AIMessage>): Flow<String> {
        val maxRequestTokens = 3072
        val promptTokens = defaultPrompt.tokenCount
        val maxContextTokens = maxRequestTokens - promptTokens

        var cumulativeTokens = 0
        val chatContext = context.takeLastWhile {
            cumulativeTokens += it.tokenCount
            cumulativeTokens <= maxContextTokens
        }

        val requestMessages = (listOf(defaultPrompt) + chatContext).asChatMessages()

        val chatCompletionRequest = chatCompletionRequest {
            model = ModelId("gpt-3.5-turbo")
            messages = requestMessages.toList()
            maxTokens = 1024
        }

        val completionFlow = openAI.chatCompletions(chatCompletionRequest)

        // Occasionally, even when specifying user names with the name field, ChatGPT will respond with a message starting with the bot name
        // ...so we'll cut that out.
        fun String.trimBotPrefix(): String {
            var outStr = this
            if (outStr.startsWith("$botName:")) {
                outStr = outStr.removePrefix("${botName}:")
                outStr = outStr.trimStart()
            }
            return outStr
        }

        return flow {
            var currentPart = ""
            var isFirstPart = true
            val partLengthLimit = 256

            completionFlow.onEach {
                if (it.choices[0].finishReason != null) {
                    emit(when (isFirstPart) {
                        true -> currentPart.trimBotPrefix()
                        false -> currentPart
                    })
                    return@onEach
                }

                if (it.choices[0].delta == null) return@onEach

                if (currentPart.length < partLengthLimit) {
                    currentPart += it.choices[0].delta!!.content ?: ""
                } else {
                    if (isFirstPart) {
                        currentPart = currentPart.trimBotPrefix()
                        isFirstPart = false
                    }

                    emit(currentPart)
                    currentPart = it.choices[0].delta!!.content ?: ""
                }
            }.collect()
        }
    }
}
