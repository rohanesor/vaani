package com.vokie.ui.screens.onboarding

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vokie.VokieApplication
import com.vokie.domain.model.VokieLanguage
import com.vokie.models.BundledModelStore
import com.vokie.models.DownloadState
import com.vokie.stt.UserLanguageProfile
import com.vokie.tts.TtsLanguage
import com.vokie.tts.TtsModelManager
import com.vokie.ui.components.DesignAction
import com.vokie.ui.components.SetupProgress
import com.vokie.ui.theme.DesignSpec
import com.vokie.ui.theme.VokiePalette
import com.vokie.ui.theme.VokieTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SetupLanguageItem(
    val language: VokieLanguage,
    val nativeName: String,
    val glyph: String,
    val isBundled: Boolean = false,
    val isInstalled: Boolean = false,
    val downloadSizeMb: Int = 114,
)

private val allSetupLanguages = listOf(
    SetupLanguageItem(VokieLanguage.EN, "English", "A", isBundled = true, isInstalled = true, downloadSizeMb = 0),
    SetupLanguageItem(VokieLanguage.TE, "తెలుగు", "తె", downloadSizeMb = 114),
    SetupLanguageItem(VokieLanguage.HI, "हिन्दी", "हि", downloadSizeMb = 114),
    SetupLanguageItem(VokieLanguage.TA, "தமிழ்", "த", downloadSizeMb = 112),
    SetupLanguageItem(VokieLanguage.KN, "ಕನ್ನಡ", "ಕ", downloadSizeMb = 115),
    SetupLanguageItem(VokieLanguage.BN, "বাংলা", "ব", downloadSizeMb = 113),
    SetupLanguageItem(VokieLanguage.GU, "ગુજરાતી", "ગુ", downloadSizeMb = 114),
    SetupLanguageItem(VokieLanguage.MR, "मराठी", "म", downloadSizeMb = 114),
    SetupLanguageItem(VokieLanguage.ML, "മലയാളം", "മ", downloadSizeMb = 114),
    SetupLanguageItem(VokieLanguage.OR, "ଓଡ଼ିଆ", "ଓ", downloadSizeMb = 114),
)

/**
 * OnboardingScreen: Complete 3-step setup (Screens 2 through 9) matching iTantra.dc.html exactly.
 * - Introduction (Screens 2, 3, 4)
 * - Step 1 of 3: Attribution (Screen 5)
 * - Step 2 of 3: Primary Language (Screen 6)
 * - Step 3 of 3: Download in progress (Screen 7) / Download stopped & retry (Screen 8)
 * - Completion: You're ready (Screen 9)
 */
@Composable
fun OnboardingScreen(
    onCompleted: (UserLanguageProfile) -> Unit,
    initialProfile: UserLanguageProfile? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val app = context as VokieApplication
    val scope = rememberCoroutineScope()
    val colors = VokieTheme.colors

    var currentScreen by rememberSaveable {
        mutableStateOf(if (initialProfile == null) "intro" else "language")
    }
    var attributionSource by rememberSaveable { mutableStateOf("") }
    var selectedLang by rememberSaveable {
        mutableStateOf(initialProfile?.preferredInputLanguage ?: VokieLanguage.EN)
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var savingSource by rememberSaveable { mutableStateOf(false) }

    val downloadState by app.modelDownloads.state.collectAsState()

    // Query actual installed status on IO
    var installedPacks by remember { mutableStateOf<Set<VokieLanguage>>(setOf(VokieLanguage.EN)) }
    LaunchedEffect(currentScreen) {
        withContext(Dispatchers.IO) {
            val manager = TtsModelManager(context)
            val installed = mutableSetOf(VokieLanguage.EN)
            TtsLanguage.entries.forEach { tts ->
                if (manager.isInstalled(tts)) {
                    installed.add(tts.messageLanguage)
                }
            }
            installedPacks = installed
        }
    }

    BackHandler(enabled = currentScreen != "intro" && initialProfile == null) {
        currentScreen = when (currentScreen) {
            "source" -> "intro"
            "language" -> "source"
            "download", "retry" -> "language"
            "ready" -> "language"
            else -> "intro"
        }
    }

    // 1. Introduction Screen (Screens 2, 3, 4)
    if (currentScreen == "intro") {
        IntroductionScreen(
            onFinish = { currentScreen = "source" },
            modifier = modifier,
        )
        return
    }

    Surface(
        color = colors.background,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = DesignSpec.setupMargin),
        ) {
            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 34.dp),
            ) {
                when (currentScreen) {
                    // SCREEN 5: Step 1 of 3 — Attribution
                    "source" -> {
                        SetupProgress(step = 1)
                        Text(
                            text = "Where did you hear\nabout iTantra?",
                            style = DesignSpec.setupTitle,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(top = 18.dp),
                        )
                        Text(
                            text = "Optional. It helps us reach the teams that need this.",
                            style = DesignSpec.setupSubtitle,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(top = 10.dp),
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 26.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val options = listOf(
                                "SIH / College",
                                "Rescue / emergency team",
                                "Friend or family",
                                "Online",
                                "Other",
                            )
                            options.forEach { option ->
                                val isSelected = attributionSource == option
                                Surface(
                                    onClick = { attributionSource = option },
                                    color = colors.surface,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, if (isSelected) colors.accent else colors.border),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 56.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        // Radio indicator (18dp circle)
                                        Surface(
                                            modifier = Modifier.size(18.dp),
                                            color = Color.Transparent,
                                            shape = CircleShape,
                                            border = BorderStroke(2.dp, if (isSelected) colors.accent else colors.textMuted),
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                if (isSelected) {
                                                    Box(
                                                        Modifier
                                                            .size(8.dp)
                                                            .background(colors.accent, CircleShape),
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = option,
                                            style = if (isSelected) DesignSpec.optionTitleBold else DesignSpec.optionTitle,
                                            color = colors.textPrimary,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // SCREEN 6: Step 2 of 3 — Primary Language
                    "language" -> {
                        SetupProgress(step = 2)
                        Text(
                            text = "Your primary\nlanguage",
                            style = DesignSpec.setupTitle,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(top = 18.dp),
                        )
                        Text(
                            text = "English is already installed. Other languages download once, then work offline.",
                            style = DesignSpec.setupSubtitle,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(top = 10.dp),
                        )

                        // 48dp Search bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = "Search language",
                                    fontSize = 15.sp,
                                    color = colors.textMuted,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(19.dp),
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 20.dp, bottom = 14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = colors.surface,
                                unfocusedContainerColor = colors.surface,
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = colors.border,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                            ),
                        )

                        // Language Rows
                        val filteredLanguages = allSetupLanguages.filter {
                            searchQuery.isBlank() ||
                                it.language.displayName.contains(searchQuery, ignoreCase = true) ||
                                it.nativeName.contains(searchQuery, ignoreCase = true)
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            filteredLanguages.forEach { item ->
                                val isSelected = selectedLang == item.language
                                val isInstalled = item.language in installedPacks || item.isBundled

                                Surface(
                                    onClick = { selectedLang = item.language },
                                    color = colors.surface,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, if (isSelected) colors.accent else colors.border),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 58.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        // 34dp Circular Script Glyph
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .background(
                                                    if (isSelected) VokiePalette.SelectedRed else VokiePalette.Divider,
                                                    CircleShape,
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = item.glyph,
                                                fontSize = 14.sp,
                                                color = if (isSelected) colors.accent else colors.textPrimary,
                                            )
                                        }

                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = item.language.displayName,
                                                style = if (isSelected) DesignSpec.cardTitleBold else DesignSpec.cardTitle,
                                                color = colors.textPrimary,
                                            )
                                            val subText = when {
                                                isInstalled -> "Installed"
                                                else -> "${item.nativeName} · ${item.downloadSizeMb} MB download"
                                            }
                                            Text(
                                                text = subText,
                                                style = DesignSpec.cardSubtitle,
                                                color = if (isInstalled) colors.success else colors.textSecondary,
                                                modifier = Modifier.padding(top = 3.dp),
                                            )
                                        }

                                        // Right Indicator
                                        when {
                                            isInstalled && !isSelected -> {
                                                Icon(
                                                    imageVector = Icons.Rounded.CheckCircle,
                                                    contentDescription = "Installed",
                                                    tint = colors.success,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                            isSelected -> {
                                                Box(
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .background(colors.accent, CircleShape),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Check,
                                                        contentDescription = "Selected",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(14.dp),
                                                    )
                                                }
                                            }
                                            else -> {
                                                Icon(
                                                    imageVector = Icons.Rounded.Download,
                                                    contentDescription = "Download required",
                                                    tint = colors.textMuted,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // SCREEN 7: Step 3 of 3 — Language Pack Downloading
                    "download" -> {
                        SetupProgress(step = 3)
                        val langItem = allSetupLanguages.firstOrNull { it.language == selectedLang }
                        val langName = selectedLang.displayName

                        Text(
                            text = "Getting $langName\nready",
                            style = DesignSpec.setupTitle,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(top = 18.dp),
                        )
                        Text(
                            text = "Keep Wi-Fi on until this finishes. It only happens once.",
                            style = DesignSpec.setupSubtitle,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(top = 10.dp),
                        )

                        // Downloading Card (14dp radius, #1C1C1C, 1dp #2F343C)
                        Surface(
                            color = colors.surface,
                            shape = RoundedCornerShape(DesignSpec.panelRadius),
                            border = BorderStroke(1.dp, colors.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 26.dp),
                        ) {
                            Column(Modifier.padding(20.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .background(VokiePalette.SelectedRed, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = langItem?.glyph ?: "తె",
                                            fontSize = 15.sp,
                                            color = colors.accent,
                                        )
                                    }

                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = langName,
                                            style = DesignSpec.cardTitleBold,
                                            color = colors.textPrimary,
                                        )
                                        Text(
                                            text = "Speech + voice pack",
                                            style = DesignSpec.cardSubtitle13,
                                            color = colors.textSecondary,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                    }

                                    val currentPercent = when (val s = downloadState) {
                                        is DownloadState.Downloading -> s.percent
                                        else -> 68
                                    }
                                    Text(
                                        text = "$currentPercent%",
                                        style = DesignSpec.homeTitle,
                                        color = colors.accent,
                                    )
                                }

                                // 6dp Shimmer Progress Bar
                                val progressTransition = rememberInfiniteTransition(label = "shimmer")
                                val shimmerTranslate by progressTransition.animateFloat(
                                    initialValue = -1.2f,
                                    targetValue = 3.2f,
                                    animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
                                    label = "shimmer_x",
                                )
                                val currentFrac = when (val s = downloadState) {
                                    is DownloadState.Downloading -> s.percent / 100f
                                    else -> 0.68f
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 18.dp)
                                        .height(6.dp)
                                        .background(VokiePalette.Divider, RoundedCornerShape(3.dp)),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(currentFrac)
                                            .background(colors.accent, RoundedCornerShape(3.dp)),
                                    )
                                }

                                // Bytes and Time Row
                                val (dlBytes, totBytes) = when (val s = downloadState) {
                                    is DownloadState.Downloading -> s.downloadedBytes to s.totalBytes
                                    else -> 77_500_000L to 114_000_000L
                                }
                                val dlMb = String.format(java.util.Locale.US, "%.1f MB of %.1f MB", dlBytes / 1_000_000.0, totBytes / 1_000_000.0)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(text = dlMb, style = DesignSpec.cardSubtitle13, color = colors.textSecondary)
                                    Text(text = "about 9 s left", style = DesignSpec.cardSubtitle13, color = colors.textSecondary)
                                }
                            }
                        }

                        // Bundled English Card
                        Surface(
                            color = colors.surface,
                            shape = RoundedCornerShape(DesignSpec.panelRadius),
                            border = BorderStroke(1.dp, colors.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(VokiePalette.Divider, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(text = "A", fontSize = 15.sp, color = colors.textPrimary)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(text = "English", style = DesignSpec.cardTitle, color = colors.textPrimary)
                                    Text(text = "Bundled with the app", style = DesignSpec.cardSubtitle13, color = colors.success, modifier = Modifier.padding(top = 4.dp))
                                }
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = colors.success,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }

                        // Offline note
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDone,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "Packs are stored on this phone and work with no connection at all.",
                                style = DesignSpec.cardSubtitle13,
                                color = colors.textSecondary,
                            )
                        }
                    }

                    // SCREEN 8: Step 3 of 3 — Language Pack Retry (Download stopped)
                    "retry" -> {
                        SetupProgress(step = 3)
                        val langName = selectedLang.displayName

                        Text(
                            text = "Download stopped",
                            style = DesignSpec.setupTitle,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(top = 18.dp),
                        )
                        Text(
                            text = "The Wi-Fi connection dropped. Your progress was kept.",
                            style = DesignSpec.setupSubtitle,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(top = 10.dp),
                        )

                        // Paused Card (Amber Border #F2A33A)
                        Surface(
                            color = colors.surface,
                            shape = RoundedCornerShape(DesignSpec.panelRadius),
                            border = BorderStroke(1.dp, colors.warning),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 26.dp),
                        ) {
                            Column(Modifier.padding(20.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .background(VokiePalette.SelectedAmber, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.WifiOff,
                                            contentDescription = null,
                                            tint = colors.warning,
                                            modifier = Modifier.size(21.dp),
                                        )
                                    }

                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = "$langName · paused",
                                            style = DesignSpec.cardTitleBold,
                                            color = colors.textPrimary,
                                        )
                                        Text(
                                            text = "Waiting for Wi-Fi",
                                            style = DesignSpec.cardSubtitle13,
                                            color = colors.warning,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                    }
                                }

                                // 6dp Amber Progress Bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 18.dp)
                                        .height(6.dp)
                                        .background(VokiePalette.Divider, RoundedCornerShape(3.dp)),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(0.68f)
                                            .background(colors.warning, RoundedCornerShape(3.dp)),
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(text = "77.5 MB of 114 MB", style = DesignSpec.cardSubtitle13, color = colors.textSecondary)
                                    Text(text = "resumes where it stopped", style = DesignSpec.cardSubtitle13, color = colors.textSecondary)
                                }
                            }
                        }
                    }

                    // SCREEN 9: Setup Complete ("You're ready")
                    "ready" -> {
                        val langItem = allSetupLanguages.firstOrNull { it.language == selectedLang }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // 104dp Success Circle with itPulse Ring
                            val pulseTrans = rememberInfiniteTransition(label = "ready_pulse")
                            val readyScale by pulseTrans.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.9f,
                                animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
                                label = "ready_pulse_scale",
                            )
                            val readyAlpha by pulseTrans.animateFloat(
                                initialValue = 0.55f,
                                targetValue = 0f,
                                animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
                                label = "ready_pulse_alpha",
                            )

                            Box(
                                modifier = Modifier.size(104.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .scale(readyScale)
                                        .alpha(readyAlpha)
                                        .background(Color.Transparent, CircleShape),
                                ) {
                                    Surface(
                                        Modifier.fillMaxSize(),
                                        color = Color.Transparent,
                                        shape = CircleShape,
                                        border = BorderStroke(2.dp, colors.success),
                                    ) {}
                                }
                                Surface(
                                    modifier = Modifier.size(104.dp),
                                    color = Color.Transparent,
                                    shape = CircleShape,
                                    border = BorderStroke(2.dp, colors.success),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = colors.success,
                                            modifier = Modifier.size(52.dp),
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "You're ready",
                                style = DesignSpec.readyTitle,
                                color = colors.textPrimary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 34.dp),
                            )

                            Text(
                                text = "iTantra now works with no network of any kind.",
                                style = DesignSpec.introductionBody,
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 12.dp),
                            )

                            // Grouped READY Rows (14dp radius)
                            Surface(
                                color = colors.surface,
                                shape = RoundedCornerShape(DesignSpec.panelRadius),
                                border = BorderStroke(1.dp, colors.border),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 34.dp),
                            ) {
                                Column {
                                    // English Row
                                    Row(
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(VokiePalette.Divider, CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(text = "A", fontSize = 14.sp, color = colors.textPrimary)
                                        }
                                        Text(
                                            text = "English",
                                            style = DesignSpec.cardTitle,
                                            color = colors.textPrimary,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            text = "READY",
                                            style = DesignSpec.badgeReady,
                                            color = colors.success,
                                        )
                                    }

                                    if (selectedLang != VokieLanguage.EN) {
                                        HorizontalDivider(color = VokiePalette.Divider)

                                        // Selected Language Row
                                        Row(
                                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(VokiePalette.SelectedRed, CircleShape),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = langItem?.glyph ?: "తె",
                                                    fontSize = 14.sp,
                                                    color = colors.accent,
                                                )
                                            }
                                            Text(
                                                text = selectedLang.displayName,
                                                style = DesignSpec.cardTitle,
                                                color = colors.textPrimary,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                text = "READY",
                                                style = DesignSpec.badgeReady,
                                                color = colors.success,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Fixed Bottom CTA / Buttons for Each Screen
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (currentScreen) {
                    "source" -> {
                        Button(
                            onClick = {
                                savingSource = true
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        context.getSharedPreferences("itantra_setup", Context.MODE_PRIVATE)
                                            .edit()
                                            .putString("discovery_source", attributionSource)
                                            .commit()
                                    }
                                    savingSource = false
                                    currentScreen = "language"
                                }
                            },
                            enabled = !savingSource,
                            shape = RoundedCornerShape(DesignSpec.ctaRadius),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = DesignSpec.ctaHeight),
                        ) {
                            Text("Continue", style = DesignSpec.action)
                        }
                    }

                    "language" -> {
                        val isInstalled = selectedLang in installedPacks || selectedLang == VokieLanguage.EN
                        val ctaLabel = if (isInstalled) "Continue" else "Download ${selectedLang.displayName} pack"

                        Button(
                            onClick = {
                                if (isInstalled) {
                                    currentScreen = "ready"
                                } else {
                                    val tts = TtsLanguage.fromMessageCode(selectedLang.code)
                                    if (tts != null) {
                                        scope.launch { app.modelDownloads.download(tts) }
                                    }
                                    currentScreen = "download"
                                }
                            },
                            shape = RoundedCornerShape(DesignSpec.ctaRadius),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = DesignSpec.ctaHeight),
                        ) {
                            Text(ctaLabel, style = DesignSpec.action)
                        }
                    }

                    "download" -> {
                        // Cancel button (Screen 7)
                        Surface(
                            onClick = {
                                scope.launch { app.modelDownloads.cancel() }
                                currentScreen = "retry"
                            },
                            shape = RoundedCornerShape(DesignSpec.ctaRadius),
                            color = colors.surface,
                            border = BorderStroke(1.dp, colors.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = DesignSpec.ctaHeight),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Cancel download", style = DesignSpec.action, color = colors.textSecondary)
                            }
                        }
                    }

                    "retry" -> {
                        // Retry button (Screen 8)
                        Button(
                            onClick = {
                                val tts = TtsLanguage.fromMessageCode(selectedLang.code)
                                if (tts != null) {
                                    scope.launch { app.modelDownloads.download(tts) }
                                }
                                currentScreen = "download"
                            },
                            shape = RoundedCornerShape(DesignSpec.ctaRadius),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = DesignSpec.ctaHeight),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp),
                            ) {
                                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(20.dp))
                                Text("Retry download", style = DesignSpec.action)
                            }
                        }

                        // Continue in English for now button (Screen 8)
                        Surface(
                            onClick = {
                                selectedLang = VokieLanguage.EN
                                currentScreen = "ready"
                            },
                            shape = RoundedCornerShape(DesignSpec.ctaRadius),
                            color = colors.surface,
                            border = BorderStroke(1.dp, colors.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = DesignSpec.ctaHeight),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Continue in English for now", style = DesignSpec.action, color = colors.textSecondary)
                            }
                        }
                    }

                    "ready" -> {
                        // Open iTantra button (Screen 9)
                        Button(
                            onClick = {
                                onCompleted(UserLanguageProfile(selectedLang, selectedLang))
                            },
                            shape = RoundedCornerShape(DesignSpec.ctaRadius),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = DesignSpec.ctaHeight),
                        ) {
                            Text("Open iTantra", style = DesignSpec.action)
                        }
                    }
                }
            }
        }
    }
}
