package com.dinnerplan.chidian.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dinnerplan.chidian.ui.theme.ChiDianColors
import com.dinnerplan.chidian.ui.theme.ChiDianGradients

@Composable
fun FoodCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = ChiDianColors.Surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, ChiDianColors.Line),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
fun AppetiteHeroCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    gradient: Brush = ChiDianGradients.AppetiteHero
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(gradient, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp, lineHeight = 19.sp)
            if (!note.isNullOrBlank()) {
                Surface(color = Color.White.copy(alpha = 0.18f), shape = RoundedCornerShape(999.dp)) {
                    Text(
                        text = note,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun StatusCard(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    FoodCard(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Surface(color = ChiDianColors.SurfaceWarm, shape = RoundedCornerShape(999.dp)) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ChiDianColors.Tomato,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, color = ChiDianColors.Ink, fontWeight = FontWeight.Bold)
                Text(message, color = ChiDianColors.Muted, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
        if (actionText != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ChiDianColors.Tomato),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(actionText)
            }
        }
    }
}

@Composable
fun EmptyFoodState(
    icon: ImageVector,
    title: String,
    message: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    FoodCard(modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ChiDianColors.Tomato,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(28.dp)
        )
        Text(
            text = title,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = ChiDianColors.Ink,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = message,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = ChiDianColors.Muted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onAction,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ChiDianColors.Tomato),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(actionText)
        }
    }
}

@Composable
fun FoodInfoTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = ChiDianColors.SurfaceWarm, shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(10.dp)) {
            Text(label, color = ChiDianColors.Muted, fontSize = 11.sp)
            Text(value, color = ChiDianColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}
