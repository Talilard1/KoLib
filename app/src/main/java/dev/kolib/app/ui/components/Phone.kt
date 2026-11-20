package dev.kolib.app.ui.components

import androidx.compose.ui.text.style.LineBreak
import androidx.compose.material3.LocalTextStyle
import dev.kolib.app.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.Immutable
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.LocalContentColor
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Narrower than this (in dp), the app is laid out for a phone. */
const val PHONE_WIDTH_DP = 600

/** True on a phone, or in a window as narrow as one: one column, with the controls in reach of a thumb. */
@Composable
@ReadOnlyComposable
fun phoneLayout(): Boolean = LocalConfiguration.current.screenWidthDp < PHONE_WIDTH_DP

/**
 * Lets a screen inside the app's frame hide the bar of sections along the bottom (on phones), to put a
 * bar of its own there, such as the actions for chosen books.
 */
class FrameBars {
    var hiders by mutableIntStateOf(0)
}

val LocalFrameBars = staticCompositionLocalOf<FrameBars?> { null }

/** Hides the frame's bottom bar for as long as this is in the screen. */
@Composable
fun HideBottomBar() {
    val bars = LocalFrameBars.current ?: return
    DisposableEffect(bars) {
        bars.hiders++
        onDispose { bars.hiders-- }
    }
}

/** Space above the top of the screen's content: the status bar, and on some phones a camera cut-out. */
val TopInsets: WindowInsets @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

/**
 * The search box at the top of a phone's book list: a rounded field the width of the screen, with
 * a button for how to search while it's empty and one to clear it once something is typed.
 */
@Composable
fun PhoneSearchField(
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { FittedText(placeholder, style = LocalTextStyle.current) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Clear, tr("Clear search")) }
            else IconButton(onClick = onHelp) { Icon(Icons.AutoMirrored.Outlined.HelpOutline, tr("How to search")) }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = modifier,
    )
}

/** In place of the search box while books are being chosen: how many, ways to choose them all or none, and to stop. */
@Composable
fun PhoneSelectionHeader(count: Int, onSelectAll: () -> Unit, onDeselectAll: () -> Unit, onCancel: () -> Unit, hint: String? = null) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .windowInsetsPadding(TopInsets)
                .heightIn(min = 64.dp)
                .padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, tr("Stop selecting"), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (count == 0) tr("Tap books to select them") else if (count == 1) tr("1 book selected") else tr("{0} books selected", count),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer) }
            }
            SelectAllButtons(onSelectAll, onDeselectAll, anySelected = count > 0, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

/** Choose everything shown, or nothing (still choosing): two icon buttons, in every bar for choosing. */
@Composable
fun SelectAllButtons(onSelectAll: () -> Unit, onDeselectAll: () -> Unit, anySelected: Boolean, tint: Color = LocalContentColor.current) {
    IconButton(onClick = onSelectAll, modifier = Modifier.testTag("select-all")) { Icon(Icons.Outlined.SelectAll, tr("Select all"), tint = tint) }
    IconButton(onClick = onDeselectAll, enabled = anySelected, modifier = Modifier.testTag("deselect-all")) {
        Icon(Icons.Outlined.FlipToBack, tr("Deselect all"), tint = if (anySelected) tint else tint.copy(alpha = 0.38f))
    }
}

/**
 * How wide one of a bar's buttons may grow. On a phone they share the width between them; in a wider bar they stop
 * at this, so a bar with only two isn't all gaps.
 */
val LocalActionMaxWidth = staticCompositionLocalOf { Dp.Infinity }

/** A bar of labelled buttons along the bottom of a phone's screen, for what can be done with chosen books. */
@Composable
fun PhoneActionBar(actions: List<BarAction>, modifier: Modifier = Modifier, moreTag: String? = null) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 8.dp, modifier = modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Box(
                Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .height(76.dp)
                    .padding(horizontal = 4.dp),
            ) { BarActions(actions, narrowest = 56.dp, moreTag = moreTag) }
        }
    }
}

/** One of the things a bar for chosen books can do. */
@Immutable
class BarAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val main: Boolean = false,
    val destructive: Boolean = false,
    val tag: String? = null,
    /** Goes under More when the bar hasn't room for it (after those that come before it). */
    val canWait: Boolean = false,
    /** Its name under More, where there's room for a longer one. */
    val longLabel: String = label,
)

/**
 * A bar's buttons, each an icon over its label: all of them when they fit at [narrowest] each, and when they don't,
 * as many as fit with the rest under More (before a last button that removes or deletes, if there is one).
 */
@Composable
fun BarActions(actions: List<BarAction>, narrowest: Dp, moreTag: String? = null) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val slots = (maxWidth / narrowest).toInt().coerceAtLeast(1)
        val shown: List<BarAction>
        val under: List<BarAction>
        if (actions.size <= slots) {
            shown = actions
            under = emptyList()
        } else {
            // One place for More; those that can't wait first, then as many of the rest as there's room for, in order.
            val fixed = actions.filter { !it.canWait }
            val keep = fixed + actions.filter { it.canWait }.take((slots - 1 - fixed.size).coerceAtLeast(0))
            shown = actions.filter { it in keep }
            under = actions.filter { it !in keep }
        }
        var more by remember { mutableStateOf(false) }
        val moreBeforeLast = under.isNotEmpty() && shown.lastOrNull()?.destructive == true
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            for (a in shown) {
                if (moreBeforeLast && a === shown.last()) MoreAction(under, more, { more = it }, moreTag)
                PhoneAction(a.icon, a.label, a.onClick, enabled = a.enabled, main = a.main, destructive = a.destructive, tag = a.tag)
            }
            if (under.isNotEmpty() && !moreBeforeLast) MoreAction(under, more, { more = it }, moreTag)
        }
    }
}

/** More, and the menu of what didn't fit on the bar. */
@Composable
private fun RowScope.MoreAction(under: List<BarAction>, open: Boolean, onOpen: (Boolean) -> Unit, tag: String?) {
    PhoneAction(Icons.Filled.MoreVert, tr("More"), { onOpen(true) }, enabled = under.any { it.enabled }, tag = tag, menu = {
        DropdownMenu(expanded = open, onDismissRequest = { onOpen(false) }) {
            for (a in under) {
                DropdownMenuItem(
                    text = { Text(a.longLabel, color = if (a.destructive && a.enabled) MaterialTheme.colorScheme.error else Color.Unspecified) },
                    leadingIcon = { Icon(a.icon, null) },
                    enabled = a.enabled,
                    onClick = { onOpen(false); a.onClick() },
                    modifier = if (a.tag != null) Modifier.testTag(a.tag) else Modifier,
                )
            }
        }
    })
}

/** One button in a [PhoneActionBar]: an icon over a short label. The [main] one has its icon in a filled pill. */
@Composable
fun RowScope.PhoneAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    main: Boolean = false,
    destructive: Boolean = false,
    /** A menu that opens from this button. */
    menu: (@Composable () -> Unit)? = null,
    tag: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    // In the colours of the bar it's in.
    val content = LocalContentColor.current
    val tint = when {
        !enabled -> content.copy(alpha = 0.38f)
        main -> colors.onPrimary
        destructive -> colors.error
        else -> content
    }
    val most = LocalActionMaxWidth.current
    Box(Modifier.weight(1f, fill = most == Dp.Infinity).widthIn(max = most).fillMaxHeight()) {
    Column(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .then(if (main && enabled) Modifier.background(colors.primary) else if (main) Modifier.background(content.copy(alpha = 0.12f)) else Modifier)
                .padding(horizontal = 18.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.height(4.dp))
        FittedText(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = when {
                !enabled -> content.copy(alpha = 0.38f)
                destructive -> colors.error
                else -> content
            },
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
    menu?.invoke()
    }
}

/** A square-ish button with an icon over its label, for a book page's actions on a phone. */
@Composable
fun ActionTile(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.height(84.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                // Broken between words or phrases, never inside one (Japanese has no spaces to break at).
                style = MaterialTheme.typography.labelMedium.copy(lineBreak = LineBreak.Heading),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
