package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.ChatMessage
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SuggestionItem(
    val title: String,
    val prompt: String,
    val category: String
)

class ChatViewModel(
    application: Application,
    private val repository: ChatRepository = ChatRepository(
        AppDatabase.getInstance(application).chatDao()
    )
) : AndroidViewModel(application) {

    val messages: StateFlow<List<ChatMessage>> = repository.allMessages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val isApiKeyConfigured: Boolean
        get() = repository.isApiKeyConfigured()

    val suggestions = listOf(
        SuggestionItem(
            title = "Personal Intro",
            prompt = "Mera ek pur-asar aur professional introduction Roman Urdu mein likhein.",
            category = "Writing"
        ),
        SuggestionItem(
            title = "Android Dev Tips",
            prompt = "Android app development seekhne ke bunyadi marhale (steps) kya hain?",
            category = "Coding"
        ),
        SuggestionItem(
            title = "Gemini API Guide",
            prompt = "Google AI Studio aur Gemini 3.5 Flash API ke kya fawaid aur features hain?",
            category = "AI Tech"
        ),
        SuggestionItem(
            title = "Daily Routine",
            prompt = "Ek healthy aur productive daily routine ka timetable Roman Urdu mein banayein.",
            category = "Lifestyle"
        )
    )

    fun onInputTextChanged(text: String) {
        _inputText.value = text
    }

    fun sendMessage(customPrompt: String? = null) {
        val textToSend = (customPrompt ?: _inputText.value).trim()
        if (textToSend.isBlank() || _isLoading.value) return

        if (customPrompt == null) {
            _inputText.value = ""
        }
        _errorMessage.value = null

        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.sendMessage(textToSend)
            _isLoading.value = false
            result.onFailure { error ->
                _errorMessage.value = error.message
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clearChat()
        }
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch {
            repository.deleteMessage(id)
        }
    }

    fun retryLastMessage() {
        val lastUserMsg = messages.value.lastOrNull { it.role == "user" }
        if (lastUserMsg != null && !_isLoading.value) {
            sendMessage(lastUserMsg.content)
        }
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                return ChatViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
