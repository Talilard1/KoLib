package dev.kolib.app.ui.components

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/**
 * One line of text that gets a little smaller, down to [smallest] of its size, when its words are too
 * long for the space (German and French often are), instead of being cut short. Past that it ends in "…".
 */
@Composable
fun FittedText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    smallest: Float = 0.75f,
) {
    val size = style.fontSize
    Text(
        text,
        modifier = modifier,
        color = color,
        style = style,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = if (size.isSp) TextAutoSize.StepBased(minFontSize = size * smallest, maxFontSize = size, stepSize = 0.25.sp) else null,
    )
}
