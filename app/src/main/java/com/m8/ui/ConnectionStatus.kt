package com.m8.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m8.network.ConnectionState

@Composable
fun ConnectionStatusIndicator(
    state: ConnectionState,
    modifier: Modifier = Modifier,
) {
    val (color, label) = when (state) {
        ConnectionState.CONNECTED -> Color(0xFF00FF00) to "CONNECTED"
        ConnectionState.CONNECTING -> Color(0xFFFFFF00) to "CONNECTING"
        ConnectionState.RECONNECTING -> Color(0xFFFF8800) to "RECONNECTING"
        ConnectionState.DISCONNECTED -> Color(0xFFFF0000) to "DISCONNECTED"
    }

    Row(
        modifier = modifier.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
