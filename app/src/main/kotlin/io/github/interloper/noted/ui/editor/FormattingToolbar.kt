package io.github.interloper.noted.ui.editor

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.richeditor.model.HeadingStyle
import com.mohamedrejeb.richeditor.model.RichTextState
import io.github.interloper.noted.R

/** Second toolbar row, opened by the heading, alignment and color buttons. */
private enum class Panel { Heading, Alignment, TextColor, Highlight }

private class Swatch(val color: Color, @param:StringRes val nameRes: Int)

// Mid-tone text colors stay readable on both light and dark backgrounds.
private val textColors = listOf(
    Swatch(Color(0xFFE53935), R.string.color_red),
    Swatch(Color(0xFFF57C00), R.string.color_orange),
    Swatch(Color(0xFF43A047), R.string.color_green),
    Swatch(Color(0xFF1E88E5), R.string.color_blue),
    Swatch(Color(0xFF8E24AA), R.string.color_purple),
    Swatch(Color(0xFF757575), R.string.color_grey),
)

// Translucent, so highlighted text keeps enough contrast in either theme.
private val highlightColors = listOf(
    Swatch(Color(0x66FFEB3B), R.string.color_yellow),
    Swatch(Color(0x664CAF50), R.string.color_green),
    Swatch(Color(0x662196F3), R.string.color_blue),
    Swatch(Color(0x66E91E63), R.string.color_pink),
    Swatch(Color(0x66FF9800), R.string.color_orange),
)

private val headingChoices = listOf(HeadingStyle.Normal, HeadingStyle.H1, HeadingStyle.H2, HeadingStyle.H3, HeadingStyle.H4)

private val urlScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

/** Trims a typed address and assumes https when no scheme was given; null if nothing usable was typed. */
internal fun normaliseUrl(input: String): String? {
    val url = input.trim()
    return when {
        url.isEmpty() -> null
        urlScheme.containsMatchIn(url) -> url
        else -> "https://$url"
    }
}

/**
 * Formatting controls for [state]. Options that need a choice (heading level, alignment, colors) open in a second row
 * rather than a popup menu, so the text field keeps focus and the keyboard stays up.
 */
@Composable
fun FormattingToolbar(state: RichTextState, modifier: Modifier = Modifier) {
    var panel by rememberSaveable { mutableStateOf<Panel?>(null) }
    var linkDialog by remember { mutableStateOf(false) }
    fun togglePanel(target: Panel) {
        panel = if (panel == target) null else target
    }

    val span = state.currentSpanStyle
    val decoration = span.textDecoration
    val align = state.currentParagraphStyle.textAlign

    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.navigationBarsPadding()) {
            AnimatedContent(panel, label = "format panel") { open ->
                if (open != null) {
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            when (open) {
                                Panel.Heading -> HeadingPanel(state)
                                Panel.Alignment -> AlignmentPanel(state, align)
                                Panel.TextColor -> ColorPanel(
                                    swatches = textColors,
                                    current = span.color,
                                    onPick = { state.addSpanStyle(SpanStyle(color = it)) },
                                    onClear = { state.removeSpanStyle(SpanStyle(color = span.color)) },
                                )
                                Panel.Highlight -> ColorPanel(
                                    swatches = highlightColors,
                                    current = span.background,
                                    onPick = { state.addSpanStyle(SpanStyle(background = it)) },
                                    onClear = { state.removeSpanStyle(SpanStyle(background = span.background)) },
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolToggle(Icons.Default.FormatBold, R.string.format_bold, span.fontWeight == FontWeight.Bold) {
                    state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
                }
                ToolToggle(Icons.Default.FormatItalic, R.string.format_italic, span.fontStyle == FontStyle.Italic) {
                    state.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic))
                }
                ToolToggle(
                    Icons.Default.FormatUnderlined,
                    R.string.format_underline,
                    decoration?.contains(TextDecoration.Underline) == true,
                ) { state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline)) }
                ToolToggle(
                    Icons.Default.FormatStrikethrough,
                    R.string.format_strikethrough,
                    decoration?.contains(TextDecoration.LineThrough) == true,
                ) { state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) }
                ToolDivider()
                ToolToggle(Icons.Default.Title, R.string.format_heading, panel == Panel.Heading) { togglePanel(Panel.Heading) }
                ToolToggle(Icons.AutoMirrored.Filled.FormatListBulleted, R.string.format_bullet_list, state.isUnorderedList) {
                    state.toggleUnorderedList()
                }
                ToolToggle(Icons.Default.FormatListNumbered, R.string.format_numbered_list, state.isOrderedList) {
                    state.toggleOrderedList()
                }
                if (state.isList) {
                    IconButton(onClick = { state.decreaseListLevel() }, enabled = state.canDecreaseListLevel) {
                        Icon(Icons.AutoMirrored.Filled.FormatIndentDecrease, stringResource(R.string.format_outdent))
                    }
                    IconButton(onClick = { state.increaseListLevel() }, enabled = state.canIncreaseListLevel) {
                        Icon(Icons.AutoMirrored.Filled.FormatIndentIncrease, stringResource(R.string.format_indent))
                    }
                }
                ToolToggle(alignmentIcon(align), R.string.format_alignment, panel == Panel.Alignment) { togglePanel(Panel.Alignment) }
                ToolDivider()
                ToolToggle(Icons.Default.FormatColorText, R.string.format_text_color, panel == Panel.TextColor) {
                    togglePanel(Panel.TextColor)
                }
                ToolToggle(Icons.Default.FormatColorFill, R.string.format_highlight, panel == Panel.Highlight) {
                    togglePanel(Panel.Highlight)
                }
                ToolToggle(Icons.Default.Link, R.string.format_link, state.isLink) { linkDialog = true }
                ToolToggle(Icons.Default.Code, R.string.format_code, state.isCodeSpan) { state.toggleCodeSpan() }
                IconButton(onClick = { state.clearSpanStyles() }) {
                    Icon(Icons.Default.FormatClear, stringResource(R.string.format_clear))
                }
            }
        }
    }

    if (linkDialog) LinkDialog(state, onDismiss = { linkDialog = false })
}

private fun alignmentIcon(align: TextAlign): ImageVector = when (align) {
    TextAlign.Center -> Icons.Default.FormatAlignCenter
    TextAlign.Right, TextAlign.End -> Icons.AutoMirrored.Filled.FormatAlignRight
    else -> Icons.AutoMirrored.Filled.FormatAlignLeft
}

@Composable
private fun ToolToggle(icon: ImageVector, @StringRes label: Int, checked: Boolean, onClick: () -> Unit) {
    IconToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        colors = IconButtonDefaults.iconToggleButtonColors(
            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) { Icon(icon, stringResource(label)) }
}

@Composable
private fun ToolDivider() {
    VerticalDivider(Modifier.padding(horizontal = 4.dp).height(24.dp))
}

@Composable
private fun HeadingPanel(state: RichTextState) {
    val current = state.currentHeadingStyle
    headingChoices.forEach { heading ->
        FilterChip(
            selected = current == heading,
            onClick = { state.setHeadingStyle(heading) },
            label = {
                Text(
                    if (heading == HeadingStyle.Normal) {
                        stringResource(R.string.format_normal_text)
                    } else {
                        stringResource(R.string.format_heading_level, heading.level)
                    },
                )
            },
        )
    }
}

@Composable
private fun AlignmentPanel(state: RichTextState, current: TextAlign) {
    // The library stores alignment as CSS left/center/right, so the choices are physical rather than start/end.
    val isCenter = current == TextAlign.Center
    val isRight = current == TextAlign.Right || current == TextAlign.End
    fun set(align: TextAlign) = state.addParagraphStyle(ParagraphStyle(textAlign = align))
    ToolToggle(Icons.AutoMirrored.Filled.FormatAlignLeft, R.string.format_align_left, !isCenter && !isRight) { set(TextAlign.Left) }
    ToolToggle(Icons.Default.FormatAlignCenter, R.string.format_align_center, isCenter) { set(TextAlign.Center) }
    ToolToggle(Icons.AutoMirrored.Filled.FormatAlignRight, R.string.format_align_right, isRight) { set(TextAlign.Right) }
}

@Composable
private fun ColorPanel(swatches: List<Swatch>, current: Color, onPick: (Color) -> Unit, onClear: () -> Unit) {
    IconButton(onClick = onClear) { Icon(Icons.Default.FormatColorReset, stringResource(R.string.format_no_color)) }
    swatches.forEach { swatch ->
        val name = stringResource(swatch.nameRes)
        val isCurrent = swatch.color == current
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(swatch.color)
                .border(
                    width = if (isCurrent) 3.dp else 1.dp,
                    color = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
                )
                .clickable(role = Role.Button) { onPick(swatch.color) }
                .semantics {
                    contentDescription = name
                    selected = isCurrent
                },
        )
    }
}

/**
 * Adds a link, or edits the one under the cursor. With text selected the link goes on the selection; with nothing
 * selected the dialog also asks for the link text.
 */
@Composable
private fun LinkDialog(state: RichTextState, onDismiss: () -> Unit) {
    val editing = remember { state.isLink }
    val needsText = remember { !editing && state.selection.collapsed }
    var text by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(if (editing) state.selectedLinkUrl.orEmpty() else "") }
    val normalised = normaliseUrl(url)
    val uriHandler = rememberSafeUriHandler()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.format_link)) },
        text = {
            Column {
                if (needsText) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(stringResource(R.string.link_text)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.link_url)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (editing) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(enabled = normalised != null, onClick = { normalised?.let(uriHandler::openUri) }) {
                            Text(stringResource(R.string.link_open))
                        }
                        TextButton(onClick = { state.removeLink(); onDismiss() }) {
                            Text(stringResource(R.string.link_remove), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = normalised != null,
                onClick = {
                    val target = normalised ?: return@TextButton
                    when {
                        editing -> state.updateLink(target)
                        needsText -> state.addLink(text.trim().ifEmpty { target }, target)
                        else -> state.addLinkToSelection(target)
                    }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
