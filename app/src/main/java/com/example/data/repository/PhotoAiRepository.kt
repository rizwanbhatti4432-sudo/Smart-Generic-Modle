package com.example.data.repository

import android.graphics.Bitmap
import com.example.BuildConfig
import com.example.data.api.GeminiApiService
import com.example.data.api.GeminiBlob
import com.example.data.api.GeminiContent
import com.example.data.api.GeminiGenerationConfig
import com.example.data.api.GeminiPart
import com.example.data.api.GeminiRequest
import com.example.data.photo.ImageEditorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhotoAiRepository(
    private val geminiApiService: GeminiApiService = GeminiApiService.create()
) {

    fun isApiKeyConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

    suspend fun analyzePhoto(
        bitmap: Bitmap,
        userPrompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!isApiKeyConfigured()) {
            return@withContext Result.failure(
                IllegalStateException(
                    "⚠️ **API Key Configured Nahi Hai**\n\n" +
                    "AI Photo analysis aur editing features ke liye Google AI Studio se `GEMINI_API_KEY` Secrets panel mein enter karein."
                )
            )
        }

        try {
            val base64Data = ImageEditorUtils.bitmapToBase64(bitmap, maxDim = 1024, quality = 80)

            val systemInstruction = GeminiContent(
                parts = listOf(
                    GeminiPart(
                        text = "Aap 'Smart Generic Modle' ke kabil tareen AI Photo Editor aur Visual Expert hain. " +
                               "Aap tasveer ka bariki se jaiza (analysis) lete hain, lighting, composition, colors, aur subject ka tafseeli mutala karte hain. " +
                               "Aap user ko Roman Urdu aur English dono mein asan, dilchasp aur behtareen photo editing tajaveez (tips), captions, aur creative suggestions dete hain."
                    )
                )
            )

            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        role = "user",
                        parts = listOf(
                            GeminiPart(text = userPrompt),
                            GeminiPart(
                                inlineData = GeminiBlob(
                                    mimeType = "image/jpeg",
                                    data = base64Data
                                )
                            )
                        )
                    )
                ),
                systemInstruction = systemInstruction,
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.7f,
                    topP = 0.95f,
                    maxOutputTokens = 2048
                )
            )

            val apiKey = BuildConfig.GEMINI_API_KEY
            val response = geminiApiService.generateContent(apiKey = apiKey, request = request)

            if (response.error != null) {
                return@withContext Result.failure(
                    Exception("Gemini API Error ${response.error.code ?: ""}: ${response.error.message ?: "Failed"}")
                )
            }

            val textResult = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!textResult.isNullOrBlank()) {
                Result.success(textResult.trim())
            } else {
                Result.failure(Exception("AI se koi jawab hasil nahi hua. Barah-e-karam dobara koshish karein."))
            }
        } catch (e: Exception) {
            val friendlyError = when {
                e is java.net.UnknownHostException -> "Internet connection ka masla hai. Network check karein."
                e is java.net.SocketTimeoutException -> "AI Photo request timeout ho gayi. Server respond nahi kar raha."
                e.message?.contains("400") == true -> "Image format ya request invalid hai."
                e.message?.contains("403") == true -> "API key unauthorized hai. Google AI Studio permissions check karein."
                else -> "Error: ${e.localizedMessage ?: e.message ?: "An unexpected error occurred"}"
            }
            Result.failure(Exception(friendlyError, e))
        }
    }
}
