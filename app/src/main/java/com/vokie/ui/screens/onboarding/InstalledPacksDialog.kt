package com.vokie.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vokie.VokieApplication
import com.vokie.models.BundledModelStore
import com.vokie.models.DownloadState
import com.vokie.tts.TtsLanguage
import com.vokie.tts.TtsModelManager
import com.vokie.ui.communication.CommunicationViewModel
import com.vokie.ui.components.DesignAction
import com.vokie.ui.components.SetupProgress
import com.vokie.ui.theme.VokieTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class PackRow(val language: TtsLanguage, val installedBytes: Long, val expectedBytes: Long)

/** Full-screen destination; the dialog is only a window host, never an AlertDialog. */
@Composable
fun InstalledPacksDialog(onDismiss: () -> Unit, vm: CommunicationViewModel = viewModel(), initialLanguage: TtsLanguage? = null, onReady: (() -> Unit)? = null) {
    val context = LocalContext.current.applicationContext
    val download by (context as VokieApplication).modelDownloads.state.collectAsState()
    val operationError by vm.error.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    var rows by remember { mutableStateOf<List<PackRow>?>(null) }
    var inventoryError by remember { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<TtsLanguage?>(initialLanguage) }
    var request by rememberSaveable { mutableStateOf<TtsLanguage?>(null) }
    LaunchedEffect(refresh, download) {
        // Do not scan files at every progress update.
        if (download is DownloadState.Downloading) return@LaunchedEffect
        inventoryError = false
        try {
            rows = withContext(Dispatchers.IO) {
                val manager = TtsModelManager(context)
                val manifest = BundledModelStore(context).manifest()
                TtsLanguage.entries.map { language ->
                    val prefix = "tts/${language.iso6393}/"
                    PackRow(language, if (manager.isInstalled(language)) manager.installedSizeBytes(language) else 0,
                        manifest.files.filterKeys { it.startsWith(prefix) }.values.sumOf { it.sizeBytes })
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            inventoryError = true
        }
    }
    val active = when (val state = download) {
        is DownloadState.Downloading -> state.language
        is DownloadState.Cancelled -> if (request == state.language) state.language else null
        is DownloadState.Complete -> if (request == state.language) state.language else null
        is DownloadState.Error -> if (request == state.language) state.language else null
        else -> request
    }
    val failed = inventoryError || (active != null && (download is DownloadState.Error || download is DownloadState.Cancelled || operationError != null))
    val finished = active != null && download is DownloadState.Complete && !failed
    val downloading = active != null && !failed && !finished
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(onBack = onDismiss)
        val byteProgress = when (val state = download) {
            is DownloadState.Downloading -> state.downloadedBytes to state.totalBytes
            is DownloadState.Cancelled -> state.downloadedBytes to state.totalBytes
            is DownloadState.Error -> state.downloadedBytes to state.totalBytes
            else -> 0L to 0L
        }
        PackPage(rows, selected, active, if (byteProgress.second > 0) ((byteProgress.first * 100 / byteProgress.second).toInt()).coerceIn(0, 99) else null,
            failed, finished, downloading, onSelect = { selected = it }, onBack = onDismiss,
            onAction = {
                val language = active ?: selected
                if (finished || (language != null && rows?.any { it.language == language && it.installedBytes > 0 } == true)) (onReady ?: onDismiss)()
                else if (language != null && language != TtsLanguage.ENGLISH) {
                    request = language
                    vm.clearError()
                    vm.downloadTtsLanguage(language)
                } else if (inventoryError) { refresh++ } else onDismiss()
            }, onCancel = vm::cancelLanguageDownload, downloadedBytes = byteProgress.first, totalBytes = byteProgress.second,
            cancelled = download is DownloadState.Cancelled)
    }
}

@Composable
internal fun PackPage(rows: List<PackRow>?, selected: TtsLanguage?, active: TtsLanguage?, percent: Int?,
                      failed: Boolean, finished: Boolean, downloading: Boolean,
                      onSelect: (TtsLanguage) -> Unit, onBack: () -> Unit, onAction: () -> Unit,
                      onCancel: () -> Unit = {}, downloadedBytes: Long = 0, totalBytes: Long = 0, cancelled: Boolean = false) {
    val colors = VokieTheme.colors
    var search by rememberSaveable { mutableStateOf("") }
    val name = (active ?: selected)?.messageLanguage?.displayName ?: "Language"
    val progress by animateFloatAsState((percent ?: 0) / 100f, label = "pack_progress")
    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 28.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(34.dp))
                SetupProgress(if (active == null) 2 else 3)
                Text(when { cancelled -> "Download paused"; failed -> "Download stopped"; finished -> "You're ready"; downloading -> "Getting $name\nready"; else -> "Your primary\nlanguage" },
                    fontSize = 26.sp, lineHeight = 32.5.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary, modifier = Modifier.padding(top = 18.dp))
                Text(when {
                    failed -> "Your progress is kept on this phone. Retry when you are ready."
                    finished -> "Your voice pack is stored on this phone."
                    downloading -> "Keep Wi-Fi on until this finishes. It only happens once."
                    else -> "English is included. Download a missing voice pack once, then use it offline."
                }, fontSize = 14.sp, lineHeight = 21.sp, color = colors.textSecondary, modifier = Modifier.padding(top = 10.dp))
                if (active != null) {
                    Surface(color = colors.surface, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, if (failed) colors.warning else colors.border),
                        modifier = Modifier.fillMaxWidth().padding(top = 26.dp)) {
                        Column(Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PackGlyph(active, true)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = colors.textPrimary)
                                    Text(if (failed) "Not installed" else if (finished) "Installed" else "Voice pack", fontSize = 13.sp,
                                        color = if (failed) colors.warning else if (finished) colors.success else colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                                }
                                if (finished) Icon(Icons.Rounded.Check, "Installed", tint = colors.success)
                                else if (percent != null) Text("$percent%", fontSize = 20.sp, color = colors.accent, fontWeight = FontWeight.Bold)
                            }
                            if (!finished) {
                                if (percent == null && !failed) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(6.dp), color = colors.accent)
                                else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(6.dp), color = if (failed) colors.warning else colors.accent)
                                rows?.firstOrNull { it.language == active }?.let {
                                    Text(if (totalBytes > 0) String.format(java.util.Locale.US, "%.1f MB of %.1f MB", downloadedBytes / 1_000_000.0, totalBytes / 1_000_000.0) else String.format(java.util.Locale.US, "%.1f MB pack", it.expectedBytes / 1_000_000.0), color = colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                                }
                            }
                        }
                    }
                    rows?.firstOrNull { it.language == TtsLanguage.ENGLISH }?.let { PackChoice(it, false, {}) }
                } else {
                    OutlinedTextField(search, { search = it }, placeholder = { Text("Search language", fontSize = 15.sp) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null, modifier = Modifier.size(19.dp)) }, singleLine = true,
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = colors.surface, focusedContainerColor = colors.surface,
                            unfocusedBorderColor = colors.border, focusedBorderColor = colors.accent, focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary))
                    if (rows == null && !failed) CircularProgressIndicator(Modifier.padding(24.dp))
                    rows?.filter { it.language.nativeName.contains(search, true) || it.language.messageLanguage.displayName.contains(search, true) }?.forEach { row ->
                        PackChoice(row, selected == row.language) { onSelect(row.language) }
                    }
                    if (rows != null && rows.none { it.language.nativeName.contains(search, true) || it.language.messageLanguage.displayName.contains(search, true) }) {
                        Text("No supported language matches", color = colors.textSecondary, modifier = Modifier.padding(top = 16.dp))
                    }
                }
            }
            Column(Modifier.padding(top = 20.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (downloading) {
                    DesignAction("Cancel download", onCancel, secondary = true)
                } else {
                    DesignAction(when { failed -> "Retry download"; finished -> "Continue"; selected == null -> "Choose a language";
                        rows?.any { it.language == selected && it.installedBytes > 0 } == true -> "Continue"; else -> "Download $name pack" },
                        onAction, enabled = failed || finished || selected != null)
                    DesignAction("Back", onBack, secondary = true)
                }
            }
        }
    }
}

@Composable
private fun PackChoice(row: PackRow, selected: Boolean, onClick: () -> Unit) {
    val colors = VokieTheme.colors
    Surface(onClick = onClick, color = colors.surface, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, if (selected) colors.accent else colors.border),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 58.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            PackGlyph(row.language, selected)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(row.language.messageLanguage.displayName, color = colors.textPrimary, fontSize = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                Text(if (row.installedBytes > 0) "Installed" else String.format(java.util.Locale.US, "%.1f MB download", row.expectedBytes / 1_000_000.0),
                    fontSize = 12.sp, color = if (row.installedBytes > 0) colors.success else colors.textSecondary, modifier = Modifier.padding(top = 3.dp))
            }
            Icon(if (selected || row.installedBytes > 0) Icons.Rounded.Check else Icons.Rounded.ChevronRight, null, tint = if (selected) colors.accent else colors.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PackGlyph(language: TtsLanguage, selected: Boolean) {
    Box(Modifier.size(34.dp).background(if (selected) Color(0xFF2A1A17) else Color(0xFF232830), CircleShape), contentAlignment = Alignment.Center) {
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
        }, fontSize = 14.sp, color = if (selected) VokieTheme.colors.accent else VokieTheme.colors.textPrimary)
    }
}
