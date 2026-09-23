package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.photo.ImageEditorUtils
import com.example.data.photo.PhotoEditSettings
import com.example.data.photo.PhotoFilter
import com.example.data.repository.PhotoAiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DemoPhoto(
    val title: String,
    val resId: Int,
    val category: String
)

data class AiPhotoPreset(
    val title: String,
    val prompt: String,
    val iconDescription: String
)

sealed interface PhotoEditorUiState {
    data object Loading : PhotoEditorUiState
    data class Ready(
        val originalBitmap: Bitmap,
        val editedBitmap: Bitmap,
        val settings: PhotoEditSettings
    ) : PhotoEditorUiState
    data class Error(val message: String) : PhotoEditorUiState
}

class PhotoEditorViewModel(
    application: Application,
    private val photoAiRepository: PhotoAiRepository = PhotoAiRepository()
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<PhotoEditorUiState>(PhotoEditorUiState.Loading)
    val uiState: StateFlow<PhotoEditorUiState> = _uiState.asStateFlow()

    private var currentSourceBitmap: Bitmap? = null
    private var currentSettings = PhotoEditSettings()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _aiResponse = MutableStateFlow<String?>(null)
    val aiResponse: StateFlow<String?> = _aiResponse.asStateFlow()

    private val _aiPrompt = MutableStateFlow("")
    val aiPrompt: StateFlow<String> = _aiPrompt.asStateFlow()

    private val _saveStatusMessage = MutableStateFlow<String?>(null)
    val saveStatusMessage: StateFlow<String?> = _saveStatusMessage.asStateFlow()

    private var renderJob: Job? = null

    val isApiKeyConfigured: Boolean
        get() = photoAiRepository.isApiKeyConfigured()

    val demoPhotos = listOf(
        DemoPhoto("Golden Lake", R.drawable.sample_photo_nature, "Nature"),
        DemoPhoto("Cyber City", R.drawable.sample_photo_city, "Urban")
    )

    val aiQuickPrompts = listOf(
        AiPhotoPreset(
            title = "Photo Critique",
            prompt = "Is tasveer ka mukammal jaiza (critique) Roman Urdu aur English mein dein: composition, lighting, rangon ka tawazun aur behtari ke mashwary.",
            iconDescription = "Analysis"
        ),
        AiPhotoPreset(
            title = "Social Captions",
            prompt = "Is photo ke liye 3 dilchasp aur trendy social media captions Roman Urdu aur English mein likhein, sath relevant popular hashtags bhi dein.",
            iconDescription = "Captions"
        ),
        AiPhotoPreset(
            title = "Best Edit Style",
            prompt = "Is picture ke mood aur subject ko dekhte hue batayein ke konsa filter (Vibrant, Noir, Cyberpunk, Golden Hour) aur adjustments sab se behtareen lagengi aur kyun?",
            iconDescription = "Suggestions"
        ),
        AiPhotoPreset(
            title = "Describe Scene",
            prompt = "Is tasveer mein kya kya nazar aa raha hai? Roman Urdu aur English mein scene, visual elements aur atmosphere tafseel se bayan karein.",
            iconDescription = "Description"
        )
    )

    init {
        // Load default demo photo initially
        loadDemoPhoto(R.drawable.sample_photo_nature)
    }

    fun loadDemoPhoto(resId: Int) {
        viewModelScope.launch {
            _uiState.value = PhotoEditorUiState.Loading
            _aiResponse.value = null
            withContext(Dispatchers.IO) {
                try {
                    val bmp = ImageEditorUtils.decodeBitmapFromResource(
                        getApplication(),
                        resId,
                        maxDim = 1280
                    )
                    currentSourceBitmap = bmp
                    currentSettings = PhotoEditSettings()
                    val edited = ImageEditorUtils.applyEditsToBitmap(bmp, currentSettings)
                    _uiState.value = PhotoEditorUiState.Ready(
                        originalBitmap = bmp,
                        editedBitmap = edited,
                        settings = currentSettings
                    )
                } catch (e: Exception) {
                    _uiState.value = PhotoEditorUiState.Error("Tasveer load karne mein masla hua: ${e.message}")
                }
            }
        }
    }

    fun loadFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = PhotoEditorUiState.Loading
            _aiResponse.value = null
            withContext(Dispatchers.IO) {
                val bmp = ImageEditorUtils.decodeBitmapFromUri(getApplication(), uri, maxDim = 1440)
                if (bmp != null) {
                    currentSourceBitmap = bmp
                    currentSettings = PhotoEditSettings()
                    val edited = ImageEditorUtils.applyEditsToBitmap(bmp, currentSettings)
                    _uiState.value = PhotoEditorUiState.Ready(
                        originalBitmap = bmp,
                        editedBitmap = edited,
                        settings = currentSettings
                    )
                } else {
                    _uiState.value = PhotoEditorUiState.Error("Gallery se tasveer load nahi ho saki.")
                }
            }
        }
    }

    fun setFilter(filter: PhotoFilter) {
        updateSettings { it.copy(filter = filter) }
    }

    fun setBrightness(brightness: Float) {
        updateSettings { it.copy(brightness = brightness) }
    }

    fun setContrast(contrast: Float) {
        updateSettings { it.copy(contrast = contrast) }
    }

    fun setSaturation(saturation: Float) {
        updateSettings { it.copy(saturation = saturation) }
    }

    fun setWarmth(warmth: Float) {
        updateSettings { it.copy(warmth = warmth) }
    }

    fun rotate90() {
        val nextDeg = (currentSettings.rotationDegrees + 90f) % 360f
        updateSettings { it.copy(rotationDegrees = nextDeg) }
    }

    fun toggleFlipHorizontal() {
        updateSettings { it.copy(flipHorizontal = !it.flipHorizontal) }
    }

    fun toggleFlipVertical() {
        updateSettings { it.copy(flipVertical = !it.flipVertical) }
    }

    fun resetEdits() {
        currentSettings = PhotoEditSettings()
        triggerRender()
    }

    private fun updateSettings(transform: (PhotoEditSettings) -> PhotoEditSettings) {
        currentSettings = transform(currentSettings)
        triggerRender()
    }

    private fun triggerRender() {
        val src = currentSourceBitmap ?: return
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            // Debounce rapid slider gestures slightly
            delay(15)
            val updated = withContext(Dispatchers.Default) {
                ImageEditorUtils.applyEditsToBitmap(src, currentSettings)
            }
            _uiState.value = PhotoEditorUiState.Ready(
                originalBitmap = src,
                editedBitmap = updated,
                settings = currentSettings
            )
        }
    }

    fun onAiPromptChanged(prompt: String) {
        _aiPrompt.value = prompt
    }

    fun analyzeWithAi(customPrompt: String? = null) {
        val promptToSend = (customPrompt ?: _aiPrompt.value).trim()
        if (promptToSend.isBlank() || _isAiLoading.value) return

        val state = _uiState.value
        val bitmapToAnalyze = if (state is PhotoEditorUiState.Ready) state.editedBitmap else currentSourceBitmap
        if (bitmapToAnalyze == null) {
            _saveStatusMessage.value = "Pehle tasveer muntakhab karein."
            return
        }

        viewModelScope.launch {
            _isAiLoading.value = true
            val result = photoAiRepository.analyzePhoto(bitmapToAnalyze, promptToSend)
            _isAiLoading.value = false
            result.onSuccess { text ->
                _aiResponse.value = text
            }.onFailure { err ->
                _aiResponse.value = "⚠️ " + (err.message ?: "Analysis failed")
            }
        }
    }

    fun saveEditedImage(onSuccess: (Uri) -> Unit) {
        val state = _uiState.value
        if (state !is PhotoEditorUiState.Ready) {
            _saveStatusMessage.value = "Tasveer tayar nahi hai."
            return
        }

        viewModelScope.launch {
            _saveStatusMessage.value = "Saving photo..."
            val uri = withContext(Dispatchers.IO) {
                ImageEditorUtils.saveBitmapToGallery(
                    getApplication(),
                    state.editedBitmap,
                    "SmartGenericModle_Edit"
                )
            }
            if (uri != null) {
                _saveStatusMessage.value = "Tasveer gallery mein save ho gayi! 📸"
                onSuccess(uri)
            } else {
                _saveStatusMessage.value = "Tasveer save karne mein masla aya."
            }
        }
    }

    fun dismissSaveStatus() {
        _saveStatusMessage.value = null
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PhotoEditorViewModel::class.java)) {
                return PhotoEditorViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
