package com.bobbyesp.docucraft.core.presentation.components.text

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

@Composable
fun AnimatedCounter(
    count: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    var oldCount by remember { mutableIntStateOf(count) }
    SideEffect { oldCount = count }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        val countString = count.toString()
        val oldCountString = oldCount.toString()
        countString.indices.forEach { i ->
            val oldChar = oldCountString.getOrNull(i)
            val newChar = countString[i]
            val char =
                if (oldChar == newChar) {
                    oldCountString[i]
                } else {
                    countString[i]
                }
            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    slideInVertically { it } togetherWith slideOutVertically { -it }
                },
                label = "AnimatedCounter",
            ) { character ->
                Text(
                    text = character.toString(),
                    style = style,
                    fontFamily = fontFamily,
                    fontStyle = fontStyle,
                    fontWeight = fontWeight,
                    softWrap = false,
                )
            }
        }
        trailingContent?.let { it() }
    }
}
