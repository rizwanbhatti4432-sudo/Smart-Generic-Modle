package com.example.data.repository

import com.example.BuildConfig
import com.example.data.api.GeminiApiService
import com.example.data.api.GeminiContent
import com.example.data.api.GeminiGenerationConfig
import com.example.data.api.GeminiPart
import com.example.data.api.GeminiRequest
import com.example.data.db.ChatDao
import com.example.data.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ChatRepository(
    private val chatDao: ChatDao,
    private val geminiApiService: GeminiApiService = GeminiApiService.create()
) {
    val allMessages: Flow<List<ChatMessage>> = chatDao.getAllMessages()

    fun isApiKeyConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

    suspend fun sendMessage(userPrompt: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmedPrompt = userPrompt.trim()
        if (trimmedPrompt.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Prompt cannot be empty"))
        }

        // 1. Insert user message to local database
        val userMessage = ChatMessage(
            role = "user",
            content = trimmedPrompt,
            timestamp = System.currentTimeMillis()
        )
        chatDao.insertMessage(userMessage)

        // 2. Validate API Key
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (!isApiKeyConfigured()) {
            val keyMissingNotice = "⚠️ **API Key Not Configured**\n\n" +
                "Google AI Studio se live jawab hasil karne ke liye aap ko apni **GEMINI_API_KEY** configure karni hogi:\n\n" +
                "1. [aistudio.google.com](https://aistudio.google.com) par ja kar API key hasil karein.\n" +
                "2. AI Studio ke **Secrets Panel** mein ja kar `GEMINI_API_KEY` save karein.\n\n" +
                "*(Note: Seekhne aur testing ke liye aap apni free tier key asani se generate kar sakte hain!)*"
            val errorMsg = ChatMessage(
                role = "model",
                content = keyMissingNotice,
                timestamp = System.currentTimeMillis(),
                isError = true
            )
            chatDao.insertMessage(errorMsg)
            return@withContext Result.failure(IllegalStateException(keyMissingNotice))
        }

        // 3. Build multi-turn conversation history
        val recentMessages = chatDao.getRecentMessages(limit = 12).reversed()
        val historyContents = mutableListOf<GeminiContent>()

        for (msg in recentMessages) {
            if (!msg.isError) {
                val role = if (msg.role == "user") "user" else "model"
                historyContents.add(
                    GeminiContent(
                        role = role,
                        parts = listOf(GeminiPart(text = msg.content))
                    )
                )
            }
        }

        // If the current message isn't at the end of history list, ensure it's there
        if (historyContents.isEmpty() || historyContents.last().role != "user" || historyContents.last().parts.firstOrNull()?.text != trimmedPrompt) {
            historyContents.add(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(text = trimmedPrompt))
                )
            )
        }

        val systemInstruction = GeminiContent(
            parts = listOf(
                GeminiPart(
                    text = "Aap ek madadgar, qabil aur dostana AI assistant hain jo har sawal ka jawab roman Urdu aur English mein behtareen aur asan andaz mein de sakta hai. " +
                        "Agar user Roman Urdu mein sawal pooche toh Roman Urdu mein jawab dein. Agar English mein pooche toh English mein jawab dein. " +
                        "Aap programming, science, general knowledge, rozmarrah ke masail aur creative writing mein mukammal rehnumai karte hain."
                )
            )
        )

        val request = GeminiRequest(
            contents = historyContents,
            systemInstruction = systemInstruction,
            generationConfig = GeminiGenerationConfig(
                temperature = 0.7f,
                topP = 0.95f,
                maxOutputTokens = 2048
            )
        )

        try {
            val response = geminiApiService.generateContent(apiKey = apiKey, request = request)

            if (response.error != null) {
                val errorText = "Error ${response.error.code ?: ""}: ${response.error.message ?: "Gemini API request failed."}"
                chatDao.insertMessage(
                    ChatMessage(
                        role = "model",
                        content = errorText,
                        timestamp = System.currentTimeMillis(),
                        isError = true
                    )
                )
                return@withContext Result.failure(Exception(errorText))
            }

            val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!replyText.isNullOrBlank()) {
                chatDao.insertMessage(
                    ChatMessage(
                        role = "model",
                        content = replyText.trim(),
                        timestamp = System.currentTimeMillis(),
                        isError = false
                    )
                )
                return@withContext Result.success(replyText)
            } else {
                val noResponseText = "Gemini se koi jawab nahi mila. Barah-e-karam dobara koshish karein."
                chatDao.insertMessage(
                    ChatMessage(
                        role = "model",
                        content = noResponseText,
                        timestamp = System.currentTimeMillis(),
                        isError = true
                    )
                )
                return@withContext Result.failure(Exception(noResponseText))
            }
        } catch (e: Exception) {
            val friendlyError = when {
                e is java.net.UnknownHostException -> "Internet connection ka masla hai. Barah-e-karam apna network check karein."
                e is java.net.SocketTimeoutException -> "Request timeout ho gayi. Server respond nahi kar raha."
                e.message?.contains("400") == true -> "API request invalid hai. API key ya parameters check karein."
                e.message?.contains("403") == true -> "API key unauthorized ya restricted hai. Google AI Studio mein key permissions check karein."
                else -> "Error: ${e.localizedMessage ?: e.message ?: "An unexpected error occurred."}"
            }

            chatDao.insertMessage(
                ChatMessage(
                    role = "model",
                    content = friendlyError,
                    timestamp = System.currentTimeMillis(),
                    isError = true
                )
            )
            return@withContext Result.failure(Exception(friendlyError, e))
        }
    }

    suspend fun clearChat() = withContext(Dispatchers.IO) {
        chatDao.clearAll()
    }

    suspend fun deleteMessage(id: Long) = withContext(Dispatchers.IO) {
        chatDao.deleteMessageById(id)
    }
}
