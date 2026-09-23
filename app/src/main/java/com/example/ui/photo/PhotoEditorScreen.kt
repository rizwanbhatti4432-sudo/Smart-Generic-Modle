package com.example.ui.photo

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import com.example.ui.camera.CameraCaptureScreen
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.photo.PhotoEditSettings
import com.example.data.photo.PhotoFilter
import com.example.ui.theme.GeminiSparkleGold
import com.example.ui.viewmodel.PhotoEditorUiState
import com.example.ui.viewmodel.PhotoEditorViewModel
import kotlin.math.roundToInt

enum class EditorTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    FILTERS("Filters", Icons.Default.Tune),
    ADJUST("Adjust", Icons.Default.Tune),
    TRANSFORM("Transform", Icons.AutoMirrored.Filled.RotateRight),
    AI_PHOTO("AI Photo", Icons.Default.AutoAwesome)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(
    viewModel: PhotoEditorViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()
    val aiResponse by viewModel.aiResponse.collectAsStateWithLifecycle()
    val aiPrompt by viewModel.aiPrompt.collectAsStateWithLifecycle()
    val saveMessage by viewModel.saveStatusMessage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }
    var isComparingOriginal by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }

    // Zero-permission Android Photo Picker contract (Google Play compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.loadFromUri(uri)
        }
    }

    LaunchedEffect(saveMessage) {
        saveMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSaveStatus()
        }
    }

    if (showCamera) {
        CameraCaptureScreen(
            onPhotoCaptured = { uri ->
                viewModel.loadFromUri(uri)
                showCamera = false
            },
            onClose = { showCamera = false }
        )
    } else {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = "Photo & AI Studio",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "Smart Generic Modle",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showCamera = true },
                        modifier = Modifier.testTag("open_camera_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Capture Photo",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("choose_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Pick Photo",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (uiState is PhotoEditorUiState.Ready) {
                        val ready = uiState as PhotoEditorUiState.Ready
                        if (!ready.settings.isDefault) {
                            IconButton(
                                onClick = { viewModel.resetEdits() },
                                modifier = Modifier.testTag("reset_edits_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = "Reset",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                viewModel.saveEditedImage { savedUri ->
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/jpeg"
                                        putExtra(Intent.EXTRA_STREAM, savedUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "Tasveer share karein")
                                    )
                                }
                            },
                            modifier = Modifier.testTag("share_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = {
                                viewModel.saveEditedImage {
                                    Toast.makeText(context, "Tasveer gallery mein save ho gayi!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("save_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save Photo",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is PhotoEditorUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is PhotoEditorUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                is PhotoEditorUiState.Ready -> {
                    // 1. Photo Preview Box with Hold-to-Compare
                    val bitmapToDisplay = if (isComparingOriginal) state.originalBitmap else state.editedBitmap

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.background)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            isComparingOriginal = true
                                            tryAwaitRelease()
                                            isComparingOriginal = false
                                        }
                                    )
                                }
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Image(
                                    bitmap = bitmapToDisplay.asImageBitmap(),
                                    contentDescription = "Edited Preview",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )

                                if (isComparingOriginal) {
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .padding(top = 12.dp)
                                    ) {
                                        Text(
                                            text = "ORIGINAL PHOTO (Tap & Hold)",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Demo Photos Switcher bar (compact row)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Sample Photos:",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            viewModel.demoPhotos.forEach { demo ->
                                SuggestionChip(
                                    onClick = { viewModel.loadDemoPhoto(demo.resId) },
                                    label = { Text(demo.title, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.height(28.dp),
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    )
                                )
                            }
                        }
                    }

                    // 3. Tab Bar (Filters, Adjust, Transform, AI Photo)
                    PrimaryTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        EditorTab.entries.forEachIndexed { index, tab ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(tab.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                icon = { Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(18.dp)) }
                            )
                        }
                    }

                    // 4. Tab Content Panel
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    ) {
                        when (EditorTab.entries[selectedTab]) {
                            EditorTab.FILTERS -> {
                                FilterPresetSelector(
                                    currentFilter = state.settings.filter,
                                    onSelectFilter = { viewModel.setFilter(it) }
                                )
                            }
                            EditorTab.ADJUST -> {
                                AdjustmentSlidersPanel(
                                    settings = state.settings,
                                    onBrightnessChanged = { viewModel.setBrightness(it) },
                                    onContrastChanged = { viewModel.setContrast(it) },
                                    onSaturationChanged = { viewModel.setSaturation(it) },
                                    onWarmthChanged = { viewModel.setWarmth(it) }
                                )
                            }
                            EditorTab.TRANSFORM -> {
                                TransformPanel(
                                    settings = state.settings,
                                    onRotate = { viewModel.rotate90() },
                                    onFlipHorizontal = { viewModel.toggleFlipHorizontal() },
                                    onFlipVertical = { viewModel.toggleFlipVertical() },
                                    onReset = { viewModel.resetEdits() }
                                )
                            }
                            EditorTab.AI_PHOTO -> {
                                AiPhotoPanel(
                                    isAiLoading = isAiLoading,
                                    aiResponse = aiResponse,
                                    aiPrompt = aiPrompt,
                                    quickPrompts = viewModel.aiQuickPrompts,
                                    onPromptChanged = { viewModel.onAiPromptChanged(it) },
                                    onAnalyzeClicked = { viewModel.analyzeWithAi(it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
fun FilterPresetSelector(
    currentFilter: PhotoFilter,
    onSelectFilter: (PhotoFilter) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxSize()
    ) {
        items(PhotoFilter.entries) { filter ->
            val isSelected = filter == currentFilter
            Card(
                onClick = { onSelectFilter(filter) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                border = if (isSelected) androidx.compose.foundation.BorderStroke(
                    2.dp,
                    MaterialTheme.colorScheme.primary
                ) else null,
                modifier = Modifier
                    .width(105.dp)
                    .height(130.dp)
                    .testTag("filter_${filter.name.lowercase()}")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    when (filter) {
                                        PhotoFilter.ORIGINAL -> listOf(Color(0xFF64748B), Color(0xFF94A3B8))
                                        PhotoFilter.VIBRANT -> listOf(Color(0xFFEC4899), Color(0xFFEAB308))
                                        PhotoFilter.WARM_SUNSET -> listOf(Color(0xFFF97316), Color(0xFFEAB308))
                                        PhotoFilter.COOL_OCEAN -> listOf(Color(0xFF06B6D4), Color(0xFF3B82F6))
                                        PhotoFilter.CYBERPUNK -> listOf(Color(0xFFA855F7), Color(0xFF06B6D4))
                                        PhotoFilter.NOIR_BW -> listOf(Color(0xFF1E293B), Color(0xFF94A3B8))
                                        PhotoFilter.SEPIA -> listOf(Color(0xFF78350F), Color(0xFFD97706))
                                        PhotoFilter.DRAMATIC -> listOf(Color(0xFF0F172A), Color(0xFF64748B))
                                    }
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = filter.displayName,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                        ),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )

                    Text(
                        text = filter.description,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        lineHeight = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun AdjustmentSlidersPanel(
    settings: PhotoEditSettings,
    onBrightnessChanged: (Float) -> Unit,
    onContrastChanged: (Float) -> Unit,
    onSaturationChanged: (Float) -> Unit,
    onWarmthChanged: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Brightness
        AdjustmentSliderRow(
            label = "Brightness",
            valueText = "${settings.brightness.roundToInt()}",
            value = settings.brightness,
            range = -50f..50f,
            onValueChange = onBrightnessChanged
        )

        // Contrast
        AdjustmentSliderRow(
            label = "Contrast",
            valueText = String.format("%.2fx", settings.contrast),
            value = settings.contrast,
            range = 0.5f..1.8f,
            onValueChange = onContrastChanged
        )

        // Saturation
        AdjustmentSliderRow(
            label = "Saturation",
            valueText = String.format("%.2fx", settings.saturation),
            value = settings.saturation,
            range = 0.0f..2.0f,
            onValueChange = onSaturationChanged
        )

        // Warmth
        AdjustmentSliderRow(
            label = "Warmth / Tint",
            valueText = "${settings.warmth.roundToInt()}",
            value = settings.warmth,
            range = -50f..50f,
            onValueChange = onWarmthChanged
        )
    }
}

@Composable
fun AdjustmentSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(90.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier
                .weight(1f)
                .height(30.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.width(45.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun TransformPanel(
    settings: PhotoEditSettings,
    onRotate: () -> Unit,
    onFlipHorizontal: () -> Unit,
    onFlipVertical: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FilledTonalButton(
                onClick = onRotate,
                modifier = Modifier.testTag("rotate_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Rotate 90° (${settings.rotationDegrees.roundToInt()}°)")
            }

            FilledTonalButton(
                onClick = onFlipHorizontal,
                modifier = Modifier.testTag("flip_h_button")
            ) {
                Icon(Icons.Default.Flip, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (settings.flipHorizontal) "Flipped H ✓" else "Flip H")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FilledTonalButton(
                onClick = onFlipVertical,
                modifier = Modifier.testTag("flip_v_button")
            ) {
                Icon(Icons.Default.Flip, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (settings.flipVertical) "Flipped V ✓" else "Flip V")
            }

            OutlinedButton(
                onClick = onReset,
                enabled = !settings.isDefault
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reset All")
            }
        }
    }
}

@Composable
fun AiPhotoPanel(
    isAiLoading: Boolean,
    aiResponse: String?,
    aiPrompt: String,
    quickPrompts: List<com.example.ui.viewmodel.AiPhotoPreset>,
    onPromptChanged: (String) -> Unit,
    onAnalyzeClicked: (String?) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Quick AI Prompts row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(quickPrompts) { item ->
                SuggestionChip(
                    onClick = { onAnalyzeClicked(item.prompt) },
                    enabled = !isAiLoading,
                    label = { Text(item.title, style = MaterialTheme.typography.labelSmall) },
                    icon = {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = GeminiSparkleGold,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                )
            }
        }

        // Input row for custom questions about photo
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = aiPrompt,
                onValueChange = onPromptChanged,
                placeholder = {
                    Text(
                        "AI se photo ke bare mein poochein...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("ai_photo_input"),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ),
                maxLines = 1
            )

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = { onAnalyzeClicked(null) },
                enabled = aiPrompt.isNotBlank() && !isAiLoading,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (aiPrompt.isNotBlank() && !isAiLoading) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                    .testTag("ai_photo_send_button")
            ) {
                if (isAiLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Analyze Photo",
                        tint = if (aiPrompt.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // AI Response Box
        if (isAiLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Gemini photo analyze kar raha hai...",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary)
                )
            }
        }

        aiResponse?.let { responseText ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = GeminiSparkleGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "Gemini AI Analysis & Insights",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(responseText))
                                Toast.makeText(context, "Text copy ho gaya!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    SelectionContainer {
                        Text(
                            text = responseText,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
