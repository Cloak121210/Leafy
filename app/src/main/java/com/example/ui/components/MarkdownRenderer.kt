package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFontFamily

sealed interface MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock
    data class BlockQuote(val lines: List<String>) : MarkdownBlock
    data class BulletItem(val text: String) : MarkdownBlock
    data class NumberedItem(val number: String, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data object Divider : MarkdownBlock
}

/**
 * Parses raw Markdown text into structured blocks and renders them with native Jetpack Compose components.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    isUser: Boolean = false
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (block in blocks) {
            when (block) {
                is MarkdownBlock.Header -> {
                    val headerStyle = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    }
                    val textColor = if (color != Color.Unspecified) color
                    else if (isUser) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.primary

                    Text(
                        text = parseInlineMarkdown(block.text, isUser = isUser),
                        style = headerStyle,
                        color = textColor,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                is MarkdownBlock.CodeBlock -> {
                    CodeBlockCard(
                        language = block.language,
                        code = block.code,
                        isUser = isUser
                    )
                }

                is MarkdownBlock.BlockQuote -> {
                    BlockQuoteView(
                        lines = block.lines,
                        isUser = isUser,
                        baseColor = color
                    )
                }

                is MarkdownBlock.BulletItem -> {
                    BulletItemView(
                        text = block.text,
                        isUser = isUser,
                        color = color
                    )
                }

                is MarkdownBlock.NumberedItem -> {
                    NumberedItemView(
                        number = block.number,
                        text = block.text,
                        isUser = isUser,
                        color = color
                    )
                }

                is MarkdownBlock.Paragraph -> {
                    val textColor = if (color != Color.Unspecified) color
                    else if (isUser) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface

                    Text(
                        text = parseInlineMarkdown(block.text, isUser = isUser),
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor
                    )
                }

                MarkdownBlock.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BulletItemView(
    text: String,
    isUser: Boolean,
    color: Color
) {
    val textColor = if (color != Color.Unspecified) color
    else if (isUser) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface

    val bulletColor = if (isUser) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(bulletColor)
        )
        Text(
            text = parseInlineMarkdown(text, isUser = isUser),
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun NumberedItemView(
    number: String,
    text: String,
    isUser: Boolean,
    color: Color
) {
    val textColor = if (color != Color.Unspecified) color
    else if (isUser) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface

    val numberColor = if (isUser) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$number.",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = numberColor
        )
        Text(
            text = parseInlineMarkdown(text, isUser = isUser),
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BlockQuoteView(
    lines: List<String>,
    isUser: Boolean,
    baseColor: Color
) {
    val quoteBg = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f)
    else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)

    val barColor = if (isUser) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.tertiary

    val textColor = if (baseColor != Color.Unspecified) baseColor
    else if (isUser) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(quoteBg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.5.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(barColor)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (line in lines) {
                Text(
                    text = parseInlineMarkdown(line, isUser = isUser),
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun CodeBlockCard(
    language: String,
    code: String,
    isUser: Boolean
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    val containerColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }

    val borderColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Code header strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.10f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            try {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("Leafy Code", code.take(64_000))
                                clipboard?.setPrimaryClip(clip)
                                copied = true
                                Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {}
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Done else Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (isUser) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (copied) "Copied" else "Copy",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Code content with horizontal scroll
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    style = TextStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    ),
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Parses multi-line Markdown text into structured blocks:
 * Code blocks, headers, blockquotes, bullets, numbered lists, dividers, and paragraphs.
 */
fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    if (text.isBlank()) return emptyList()

    val lines = text.lines()
    val blocks = mutableListOf<MarkdownBlock>()

    var inCodeBlock = false
    var codeLang = ""
    val codeLines = mutableListOf<String>()

    val quoteLines = mutableListOf<String>()

    fun flushQuote() {
        if (quoteLines.isNotEmpty()) {
            blocks.add(MarkdownBlock.BlockQuote(quoteLines.toList()))
            quoteLines.clear()
        }
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        // 1. Code Block Delimiter
        if (trimmed.startsWith("```")) {
            flushQuote()
            if (inCodeBlock) {
                // Closing code block
                blocks.add(MarkdownBlock.CodeBlock(codeLang, codeLines.joinToString("\n")))
                codeLines.clear()
                codeLang = ""
                inCodeBlock = false
            } else {
                // Opening code block
                inCodeBlock = true
                codeLang = trimmed.removePrefix("```").trim()
            }
            i++
            continue
        }

        if (inCodeBlock) {
            codeLines.add(line)
            i++
            continue
        }

        // 2. BlockQuote
        if (trimmed.startsWith(">")) {
            quoteLines.add(trimmed.removePrefix(">").trimStart())
            i++
            continue
        } else {
            flushQuote()
        }

        // 3. Blank line
        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // 4. Horizontal Divider
        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            blocks.add(MarkdownBlock.Divider)
            i++
            continue
        }

        // 5. Headers (# Header)
        if (trimmed.startsWith("#")) {
            val hashCount = trimmed.takeWhile { it == '#' }.length
            if (hashCount in 1..6 && trimmed.getOrNull(hashCount) == ' ') {
                val headerContent = trimmed.substring(hashCount).trim()
                blocks.add(MarkdownBlock.Header(hashCount, headerContent))
                i++
                continue
            }
        }

        // 6. Bullet lists
        if (trimmed.startsWith("• ") || trimmed.startsWith("- ") ||
            trimmed.startsWith("* ") || trimmed.startsWith("+ ")
        ) {
            val bulletContent = trimmed.substring(2).trim()
            blocks.add(MarkdownBlock.BulletItem(bulletContent))
            i++
            continue
        }

        // 7. Numbered lists (1. , 2. )
        val numberedMatch = NUMBERED_LIST_REGEX.find(trimmed)
        if (numberedMatch != null) {
            val num = numberedMatch.groupValues[1]
            val rest = numberedMatch.groupValues[2]
            blocks.add(MarkdownBlock.NumberedItem(num, rest))
            i++
            continue
        }

        // 8. Regular paragraph
        blocks.add(MarkdownBlock.Paragraph(line))
        i++
    }

    // Flush any pending blocks
    flushQuote()
    if (inCodeBlock) {
        blocks.add(MarkdownBlock.CodeBlock(codeLang, codeLines.joinToString("\n")))
    }

    return blocks
}

private val NUMBERED_LIST_REGEX = Regex("^(\\d+)\\.\\s+(.+)")

/**
 * Parses inline Markdown tags:
 * - ***bold italic***
 * - **bold** or __bold__
 * - *italic* or _italic_
 * - `inline code`
 * - [text](url)
 */
@Composable
fun parseInlineMarkdown(text: String, isUser: Boolean): AnnotatedString {
    val codeBg = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
    else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)

    val codeColor = if (isUser) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.primary

    val linkColor = if (isUser) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.tertiary

    return remember(text, isUser, codeBg, codeColor, linkColor) {
        buildAnnotatedString {
            // Regex parsing for inline code, links, bold-italic, bold, and italic
            var idx = 0
            val len = text.length

            while (idx < len) {
                // Inline code: `code`
                if (text[idx] == '`') {
                    val endBacktick = text.indexOf('`', idx + 1)
                    if (endBacktick != -1) {
                        val codeContent = text.substring(idx + 1, endBacktick)
                        pushStyle(
                            SpanStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                background = codeBg,
                                color = codeColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        append(" $codeContent ")
                        pop()
                        idx = endBacktick + 1
                        continue
                    }
                }

                // Bold-Italic: ***text***
                if (text.startsWith("***", idx)) {
                    val end = text.indexOf("***", idx + 3)
                    if (end != -1) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                        append(text.substring(idx + 3, end))
                        pop()
                        idx = end + 3
                        continue
                    }
                }

                // Bold: **text**
                if (text.startsWith("**", idx)) {
                    val end = text.indexOf("**", idx + 2)
                    if (end != -1) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(text.substring(idx + 2, end))
                        pop()
                        idx = end + 2
                        continue
                    }
                }

                // Bold: __text__
                if (text.startsWith("__", idx)) {
                    val end = text.indexOf("__", idx + 2)
                    if (end != -1) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(text.substring(idx + 2, end))
                        pop()
                        idx = end + 2
                        continue
                    }
                }

                // Italic: *text* (ensure not followed by space or part of bullet)
                if (text[idx] == '*' && idx + 1 < len && text[idx + 1] != ' ') {
                    val end = text.indexOf('*', idx + 1)
                    if (end != -1 && end > idx + 1) {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(text.substring(idx + 1, end))
                        pop()
                        idx = end + 1
                        continue
                    }
                }

                // Italic: _text_
                if (text[idx] == '_' && idx + 1 < len && text[idx + 1] != ' ') {
                    val end = text.indexOf('_', idx + 1)
                    if (end != -1 && end > idx + 1) {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(text.substring(idx + 1, end))
                        pop()
                        idx = end + 1
                        continue
                    }
                }

                // Markdown links: [title](url)
                if (text[idx] == '[') {
                    val closeBracket = text.indexOf(']', idx + 1)
                    if (closeBracket != -1 && closeBracket + 1 < len && text[closeBracket + 1] == '(') {
                        val closeParen = text.indexOf(')', closeBracket + 2)
                        if (closeParen != -1) {
                            val linkText = text.substring(idx + 1, closeBracket)
                            pushStyle(
                                SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            append(linkText)
                            pop()
                            idx = closeParen + 1
                            continue
                        }
                    }
                }

                // Normal character
                append(text[idx])
                idx++
            }
        }
    }
}
