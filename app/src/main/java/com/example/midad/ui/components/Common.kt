package com.example.midad.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.midad.ui.theme.LocalMizan

@Composable
fun MizanCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalMizan.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.surface, RoundedCornerShape(16.dp))
            .border(1.dp, c.stroke, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun StatCard(
    title: String,
    value: String,
    hint: String? = null,
    accent: Color? = null,
    modifier: Modifier = Modifier,
) {
    val c = LocalMizan.current
    Column(
        modifier = modifier
            .background(c.surface, RoundedCornerShape(16.dp))
            .border(1.dp, c.stroke, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text(title, fontSize = 12.sp, color = c.text3)
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent ?: c.text,
        )
        if (hint != null) {
            Spacer(Modifier.height(2.dp))
            Text(hint, fontSize = 11.sp, color = c.text3)
        }
    }
}

@Composable
fun SectionTitle(text: String, trailing: String? = null) {
    val c = LocalMizan.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.text)
        if (trailing != null) Text(trailing, fontSize = 12.sp, color = c.text3)
    }
}

@Composable
fun EmptyHint(text: String) {
    val c = LocalMizan.current
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 13.sp, color = c.text3)
    }
}

@Composable
fun ScreenScaffold(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalMizan.current
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = c.text)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, fontSize = 13.sp, color = c.text3)
        }
        Spacer(Modifier.height(14.dp))
        content()
        Spacer(Modifier.height(16.dp))
    }
}
