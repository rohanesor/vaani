package com.vokie.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vokie.proximity.ProximityGuidanceState
import com.vokie.proximity.RssiTrend
import com.vokie.ui.theme.VokieTheme

data class SignalUiInfo(
    val label: String,
    val color: Color,
    val icon: ImageVector,
)

@Composable
fun signalUiInfo(
    proximityState: ProximityGuidanceState?,
    rssiTrend: RssiTrend,
    hasLocation: Boolean,
): SignalUiInfo {
    return when {
        !hasLocation && proximityState == null -> SignalUiInfo(
            label = "Signal unknown",
            color = VokieTheme.colors.textSecondary,
            icon = Icons.Default.HelpOutline,
        )
        proximityState == ProximityGuidanceState.NEARBY -> SignalUiInfo(
            label = "Nearby",
            color = VokieTheme.colors.success,
            icon = Icons.Default.NearMe,
        )
        proximityState == ProximityGuidanceState.GETTING_CLOSER || rssiTrend == RssiTrend.STRENGTHENING -> SignalUiInfo(
            label = "Getting closer",
            color = VokieTheme.colors.success,
            icon = Icons.Default.TrendingUp,
        )
        proximityState == ProximityGuidanceState.GETTING_FARTHER || rssiTrend == RssiTrend.WEAKENING -> SignalUiInfo(
            label = "Getting farther",
            color = VokieTheme.colors.warning,
            icon = Icons.Default.TrendingDown,
        )
        proximityState == ProximityGuidanceState.SIGNAL_UNRELIABLE -> SignalUiInfo(
            label = "Signal unreliable",
            color = VokieTheme.colors.warning,
            icon = Icons.Default.Warning,
        )
        proximityState == ProximityGuidanceState.SEARCHING -> SignalUiInfo(
            label = "Searching for signal…",
            color = VokieTheme.colors.accent,
            icon = Icons.Default.BluetoothSearching,
        )
        else -> SignalUiInfo(
            label = "Signal stable",
            color = VokieTheme.colors.textSecondary,
            icon = Icons.Default.HelpOutline,
        )
    }
}

@Composable
fun SignalStatusBadge(
    proximityState: ProximityGuidanceState?,
    rssiTrend: RssiTrend,
    hasLocation: Boolean,
    modifier: Modifier = Modifier,
) {
    val info = signalUiInfo(proximityState, rssiTrend, hasLocation)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Icon(
            imageVector = info.icon,
            contentDescription = info.label,
            tint = info.color,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = info.label,
            style = VokieTheme.typography.body,
            color = info.color,
        )
    }
}
