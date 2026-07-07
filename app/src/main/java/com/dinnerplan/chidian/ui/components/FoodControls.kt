package com.dinnerplan.chidian.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dinnerplan.chidian.ui.theme.ChiDianColors

@Composable
fun FoodChip(text: String, selected: Boolean = false, green: Boolean = false, modifier: Modifier = Modifier) {
    val activeColor = if (green) ChiDianColors.MintDark else ChiDianColors.Tomato
    Surface(
        modifier = modifier,
        color = if (selected) activeColor else ChiDianColors.SurfaceWarm,
        shape = RoundedCornerShape(999.dp),
        border = if (selected) null else BorderStroke(1.dp, ChiDianColors.Line)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            color = if (selected) Color.White else ChiDianColors.Muted,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun FoodModeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FoodChip(
        text = text,
        selected = selected,
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun FoodSegmentedButtons(
    first: String,
    second: String,
    firstSelected: Boolean,
    onFirst: () -> Unit,
    onSecond: () -> Unit
) {
    Surface(
        color = ChiDianColors.SurfaceWarm,
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            SegmentButton(first, selected = firstSelected, onClick = onFirst, modifier = Modifier.weight(1f))
            SegmentButton(second, selected = !firstSelected, onClick = onSecond, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun FoodSearchPanel(
    icon: ImageVector,
    title: String,
    note: String,
    value: String,
    onValueChange: (String) -> Unit,
    buttonIcon: ImageVector,
    onSubmit: () -> Unit,
    chips: List<String> = emptyList(),
    onChipClick: (String) -> Unit = {}
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    FoodCard {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
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
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = ChiDianColors.Ink, fontWeight = FontWeight.Bold)
                Text(note, color = ChiDianColors.Muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            shape = RoundedCornerShape(8.dp)
        )
        if (chips.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                chips.forEach { chip ->
                    FoodChip(
                        text = chip,
                        modifier = Modifier.clickable { onChipClick(chip) }
                    )
                }
            }
        }
        Button(
            onClick = {
                keyboardController?.hide()
                focusManager.clearFocus()
                onSubmit()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ChiDianColors.Tomato),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(buttonIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("开始推荐")
        }
    }
}

@Composable
private fun SegmentButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(shape)
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            color = if (selected) ChiDianColors.Ink else ChiDianColors.Muted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}
