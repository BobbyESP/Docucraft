package com.bobbyesp.docucraft.core.presentation.components.text

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.tooling.preview.PreviewLightDark
import kotlinx.coroutines.delay

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

    val countString = count.toString()
    val oldCountString = oldCount.toString()

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (countString.startsWith('-') || oldCountString.startsWith('-')) {
            AnimatedContent(
                targetState = countString.startsWith('-'),
                transitionSpec = {
                    slideInVertically { it } togetherWith slideOutVertically { -it }
                },
                label = "AnimatedCounter_sign",
            ) { isNegative ->
                if (isNegative) {
                    Text(
                        text = "-",
                        style = style,
                        fontFamily = fontFamily,
                        fontStyle = fontStyle,
                        fontWeight = fontWeight,
                        softWrap = false,
                    )
                }
            }
        }

        val absCountString = countString.trimStart('-')
        val absOldCountString = oldCountString.trimStart('-')

        val maxLen = maxOf(absCountString.length, absOldCountString.length)
        val paddedNew = absCountString.padStart(maxLen, ' ')
        val paddedOld = absOldCountString.padStart(maxLen, ' ')

        paddedNew.forEachIndexed { i, newChar ->
            val oldChar = paddedOld[i]

            if (newChar == ' ' && oldChar == ' ') return@forEachIndexed

            AnimatedContent(
                targetState = newChar,
                transitionSpec = {
                    val direction = if (count > oldCount) 1 else -1
                    slideInVertically { it * direction } togetherWith
                            slideOutVertically { -it * direction }
                },
                label = "AnimatedCounter_digit_$i",
            ) { character ->
                if (character != ' ') {
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
        }

        trailingContent?.invoke()
    }
}

@PreviewLightDark
@Composable
private fun AnimatedCounterPreview() {
    var counter by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        counter++
        delay(800)
    }

    AnimatedCounter(
        count = counter,
        modifier = Modifier,
        style = MaterialTheme.typography.bodySmall,
    )
}