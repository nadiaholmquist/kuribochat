package sh.nhp.kuribochat

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatCompletionChunk
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
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
    val botName = "Kuribo"
    private val defaultPrompt: String

    private val openAI: OpenAI
    val manager = ConversationManager(GlobalScope)

    init {
        val apiKey = System.getenv("OPENAI_API_KEY")
        if (apiKey == null) {
            println("OPENAI_API_KEY environment variable not set")
            exitProcess(1)
        }

        val promptFileName = System.getenv("PROMPT_FILE")
        if (promptFileName == null) {
            println("PROMPT_FILE environment variable not set")
            exitProcess(1)
        }

        try {
            defaultPrompt = File(promptFileName)
                .readText()
                .replace("{BOT}", botName)
        } catch (e: Exception) {
            println("Could not read prompt file: $e")
            exitProcess(1)
        }

        openAI = OpenAI(apiKey)
    }

    suspend fun generateResponse(context: List<AIMessage>): Flow<String> {
        val chatMessages: List<ChatMessage> = context.map {
            when (it) {
                is AIMessage.Bot -> ChatMessage(ChatRole.Assistant, it.content)
                is AIMessage.User -> ChatMessage(ChatRole.User, "${it.userName}: ${it.content}")
            }
        }

        val convoWithPrompt = listOf(ChatMessage(ChatRole.System, defaultPrompt)) + chatMessages

        val chatCompletionRequest = ChatCompletionRequest(model = ModelId("gpt-3.5-turbo"), messages = convoWithPrompt)
        val completionFlow = openAI.chatCompletions(chatCompletionRequest)

        return flow {
            var currentPart = ""
            var isFirstPart = true
            val partLengthLimit = 256

            completionFlow.onEach {
                if (it.choices[0].finishReason != null) {
                    emit(when (isFirstPart) {
                        true -> currentPart.removePrefix("${botName}:")
                        false -> currentPart
                    })
                    return@onEach
                }

                if (it.choices[0].delta == null) return@onEach

                if (currentPart.length < partLengthLimit) {
                    currentPart += it.choices[0].delta!!.content ?: ""
                } else {
                    if (isFirstPart) {
                        currentPart = currentPart.removePrefix("${botName}:")
                        isFirstPart = false
                    }
                    emit(currentPart)
                    currentPart = it.choices[0].delta!!.content ?: ""
                }
            }.collect()
        }
    }
}
