package com.vokie.ui.screens.onboarding

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vokie.domain.model.VokieLanguage
import com.vokie.stt.UserLanguageProfile
import com.vokie.tts.TtsLanguage
import com.vokie.tts.TtsModelManager
import com.vokie.models.BundledModelStore
import com.vokie.ui.components.*
import com.vokie.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** First-run UI only; the existing language editor remains available from Settings. */
@Composable
internal fun DesignSetupFlow(onCompleted: (UserLanguageProfile) -> Unit, modifier: Modifier = Modifier) {
    var page by rememberSaveable { mutableStateOf("intro") }
    var source by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<VokieLanguage?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<PackRow>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val colors = VokieTheme.colors
    val languages = UserLanguageProfile.supportedLanguages
    LaunchedEffect(page) {
        if (page != "language" && page != "complete") return@LaunchedEffect
        try {
            rows = withContext(Dispatchers.IO) {
                val manager = TtsModelManager(context)
                val manifest = BundledModelStore(context).manifest()
                languages.mapNotNull { language ->
                    val tts = TtsLanguage.fromMessageCode(language.code) ?: return@mapNotNull null
                    PackRow(tts, if (manager.isInstalled(tts)) manager.installedSizeBytes(tts) else 0,
                        manifest.files.filterKeys { it.startsWith("tts/${tts.iso6393}/") }.values.sumOf { it.sizeBytes })
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = "Could not check language packs. Try again."
        }
    }
    BackHandler(page != "intro") { page = if (page == "source") "intro" else "language" }
    if (page == "intro") { IntroductionScreen({ page = "source" }, modifier); return }
    if (page == "download") {
        InstalledPacksDialog(onDismiss = { page = "language" }, initialLanguage = selected?.let { TtsLanguage.fromMessageCode(it.code) }, onReady = { page = "complete" })
    }
    Surface(color = colors.background, modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = DesignSpec.setupMargin)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 34.dp)) {
                if (page == "complete") {
                    Column(Modifier.fillMaxWidth().padding(top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(Modifier.size(104.dp), color = Color.Transparent, shape = CircleShape, border = BorderStroke(2.dp, colors.success)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, null, tint = colors.success, modifier = Modifier.size(52.dp)) }
                        }
                        Text("You're ready", fontSize = 28.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary, modifier = Modifier.padding(top = 34.dp))
                        Text("Your installed packs work offline.", style = DesignSpec.introductionBody, color = colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                    }
                    Surface(color = colors.surface, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, colors.border), modifier = Modifier.fillMaxWidth().padding(top = 34.dp)) {
                        Column {
                            rows?.filter { it.language.messageLanguage == VokieLanguage.EN || it.language.messageLanguage == selected }?.forEachIndexed { i, row ->
                                if (i > 0) HorizontalDivider(color = colors.border)
                                Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    LanguageGlyph(row.language)
                                    Spacer(Modifier.width(14.dp))
                                    Text(row.language.messageLanguage.displayName, fontSize = 16.sp, color = colors.textPrimary, modifier = Modifier.weight(1f))
                                    Text(if (row.installedBytes > 0) "READY" else "NOT INSTALLED", fontSize = 12.sp, color = if (row.installedBytes > 0) colors.success else colors.warning)
                                }
                            }
                        }
                    }
                } else {
                    SetupProgress(if (page == "source") 1 else 2)
                    Text(if (page == "source") "Where did you hear\nabout iTantra?" else "Your primary\nlanguage", fontSize = 26.sp, lineHeight = 32.5.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary, modifier = Modifier.padding(top = 18.dp))
                    Text(if (page == "source") "Optional. It helps us reach the teams that need this." else "English is included. Other voice packs download once, then work offline.", fontSize = 14.sp, lineHeight = 21.sp, color = colors.textSecondary, modifier = Modifier.padding(top = 10.dp))
                    if (page == "source") {
                        Column(Modifier.padding(top = 26.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf("SIH / College", "Rescue / emergency team", "Friend or family", "Online", "Other").forEach { option ->
                                val chosen = source == option
                                Surface(onClick = { source = option }, color = colors.surface, border = BorderStroke(1.dp, if (chosen) colors.accent else colors.border), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                    Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(Modifier.size(20.dp), color = Color.Transparent, shape = CircleShape, border = BorderStroke(2.dp, if (chosen) colors.accent else colors.textMuted)) {
                                            Box(contentAlignment = Alignment.Center) { if (chosen) Box(Modifier.size(8.dp).background(colors.accent, CircleShape)) }
                                        }
                                        Text(option, color = colors.textPrimary, fontSize = 16.sp, fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(start = 14.dp))
                                    }
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(search, { search = it }, placeholder = { Text("Search language", fontSize = 15.sp) },
                            leadingIcon = { Icon(Icons.Rounded.Search, null, modifier = Modifier.size(19.dp)) }, singleLine = true, shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 14.dp),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = colors.surface, focusedContainerColor = colors.surface, focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border, focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary))
                        val filtered = rows?.filter { it.language.nativeName.contains(search, true) || it.language.messageLanguage.displayName.contains(search, true) }
                        if (rows == null) CircularProgressIndicator(Modifier.padding(16.dp))
                        filtered?.forEach { row ->
                            val chosen = selected == row.language.messageLanguage
                            Surface(onClick = { selected = row.language.messageLanguage }, color = colors.surface, border = BorderStroke(1.dp, if (chosen) colors.accent else colors.border), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).heightIn(min = 58.dp)) {
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    LanguageGlyph(row.language)
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(row.language.messageLanguage.displayName, color = colors.textPrimary, fontSize = 16.sp, fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal)
                                        Text(if (row.installedBytes > 0) "Installed" else String.format(java.util.Locale.US, "%.1f MB download", row.expectedBytes / 1e6), color = if (row.installedBytes > 0) colors.success else colors.textSecondary, fontSize = 12.sp)
                                    }
                                    if (chosen || row.installedBytes > 0) Icon(Icons.Rounded.Check, if (chosen) "Selected" else "Installed", tint = if (chosen) colors.accent else colors.success, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        if (filtered?.isEmpty() == true) Text("No supported language matches. Clear search to see available languages.", color = colors.textSecondary)
                    }
                }
                error?.let { Text(it, color = colors.warning, modifier = Modifier.padding(top = 16.dp)) }
            }
            DesignAction(if (page == "complete") "Open iTantra" else if (page == "source") "Continue" else {
                val installed = rows?.any { it.language.messageLanguage == selected && it.installedBytes > 0 } == true
                if (selected == null || installed) "Continue" else "Download ${selected?.displayName} pack"
            }, onClick = {
                when (page) {
                    "source" -> {
                        saving = true
                        scope.launch {
                            val saved = withContext(Dispatchers.IO) { context.getSharedPreferences("itantra_setup", Context.MODE_PRIVATE).edit().putString("discovery_source", source).commit() }
                            saving = false
                            if (saved) page = "language" else error = "Could not save setup. Please retry."
                        }
                    }
                    "complete" -> selected?.let { onCompleted(UserLanguageProfile(it, it)) }
                    else -> if (selected != null) page = if (rows?.any { it.language.messageLanguage == selected && it.installedBytes > 0 } == true) "complete" else "download"
                }
            }, enabled = !saving && (page == "source" || selected != null), modifier = Modifier.padding(top = 20.dp, bottom = 30.dp))
        }
    }
}

@Composable
private fun LanguageGlyph(language: TtsLanguage) {
    Box(Modifier.size(34.dp).background(Color(0xFF232830), CircleShape), contentAlignment = Alignment.Center) {
        Text(when (language) {
            TtsLanguage.ENGLISH -> "A"
            TtsLanguage.HINDI -> "हि"
            TtsLanguage.GUJARATI -> "ગુ"
            TtsLanguage.MARATHI -> "मर"
            TtsLanguage.KANNADA -> "ಕ"
            TtsLanguage.MALAYALAM -> "മ"
            TtsLanguage.TAMIL -> "த"
            TtsLanguage.TELUGU -> "తె"
            TtsLanguage.ODIA -> "ଓ"
            TtsLanguage.BENGALI -> "বা"
        }, color = VokieTheme.colors.textPrimary, fontSize = 14.sp)
    }
}
