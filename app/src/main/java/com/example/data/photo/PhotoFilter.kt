package com.example.data.photo

enum class PhotoFilter(val displayName: String, val description: String) {
    ORIGINAL("Original", "No filter applied"),
    VIBRANT("Vibrant", "Boosts color saturation and vibrancy"),
    WARM_SUNSET("Golden Warmth", "Adds sunset golden glow and warmth"),
    COOL_OCEAN("Cool Cyan", "Crisp cool blue and cyan atmosphere"),
    CYBERPUNK("Cyberpunk", "Electric neon magenta and deep blues"),
    NOIR_BW("Noir B&W", "High contrast classic black and white"),
    SEPIA("Vintage", "Nostalgic warm sepia retro finish"),
    DRAMATIC("Dramatic", "Intense contrast and rich shadow depths")
}

data class PhotoEditSettings(
    val filter: PhotoFilter = PhotoFilter.ORIGINAL,
    val brightness: Float = 0f,    // -50 to +50
    val contrast: Float = 1.0f,     // 0.5 to 1.8
    val saturation: Float = 1.0f,   // 0.0 to 2.0
    val warmth: Float = 0f,         // -50 to +50
    val rotationDegrees: Float = 0f,// 0, 90, 180, 270
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false
) {
    val isDefault: Boolean
        get() = filter == PhotoFilter.ORIGINAL &&
                brightness == 0f &&
                contrast == 1.0f &&
                saturation == 1.0f &&
                warmth == 0f &&
                rotationDegrees == 0f &&
                !flipHorizontal &&
                !flipVertical
}
