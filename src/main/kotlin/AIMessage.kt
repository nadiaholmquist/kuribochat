package sh.nhp.kuribochat

sealed class AIMessage(val content: String) {
    class Bot(content: String) : AIMessage(content)
    class User(content: String, val userName: String) : AIMessage(content)

    override fun toString(): String {
        when (this) {
            is Bot -> return "Bot: $content"
            is User -> return "$userName: $content"
        }
    }
}