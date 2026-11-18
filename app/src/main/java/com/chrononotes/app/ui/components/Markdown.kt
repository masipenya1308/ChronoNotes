package com.chrononotes.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

private val INLINE_TOKEN = Regex("(\\*\\*[^*\\n]+\\*\\*|\\*[^*\\n]+\\*|`[^`\\n]+`|\\[[^\\]]+\\]\\([^)]+\\))")
private val ORDERED_ITEM = Regex("^\\d+\\.\\s")

/**
 * Небольшой Markdown-рендерер без внешних зависимостей:
 * заголовки, списки, интерактивные чек-листы, код, цитаты, ссылки,жирный/курсив.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    onToggleTask: ((Int) -> Unit)? = null,
) {
    val lines = text.lines()
    val uriHandler = LocalUriHandler.current
    var taskIndex = 0
    var orderedIndex = 0
    var i = 0

    Column(modifier) {
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.trimStart().startsWith("```") -> {
                    val language = line.trim().removePrefix("```")
                    val code = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                        code.add(lines[i])
                        i++
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            if (language.isNotBlank()) {
                                Text(
                                    language,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                code.joinToString("\n"),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                line.startsWith("- [ ] ") || line.startsWith("- [x] ") -> {
                    val index = taskIndex++
                    val checked = line.startsWith("- [x] ")
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { onToggleTask?.invoke(index) },
                            enabled = onToggleTask != null,
                            modifier = Modifier.size(34.dp),
                        )
                        val body = line.removePrefix("- [ ] ").removePrefix("- [x] ")
                        RichLine(
                            text = body,
                            style = if (checked) {
                                MaterialTheme.typography.bodyLarge.copy(
                                    textDecoration = TextDecoration.LineThrough,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                            onLinkClick = { uriHandler.openUri(it) },
                        )
                    }
                }

                line.startsWith("### ") -> Heading(line.removePrefix("### "), 3)
                line.startsWith("## ") -> Heading(line.removePrefix("## "), 2)
                line.startsWith("# ") -> Heading(line.removePrefix("# "), 1)

                line.startsWith("> ") -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        RichLine(
                            text = line.removePrefix("> "),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                            onLinkClick = { uriHandler.openUri(it) },
                        )
                    }
                }

                line.startsWith("---") || line.startsWith("***") -> {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }

                line.startsWith("- ") || line.startsWith("* ") -> {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text("•  ", style = MaterialTheme.typography.bodyLarge)
                        RichLine(
                            text = line.removePrefix("- ").removePrefix("* "),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                            onLinkClick = { uriHandler.openUri(it) },
                        )
                    }
                }

                ORDERED_ITEM.containsMatchIn(line) -> {
                    orderedIndex++
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text("$orderedIndex.  ", style = MaterialTheme.typography.bodyLarge)
                        RichLine(
                            text = line.replace(ORDERED_ITEM, ""),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                            onLinkClick = { uriHandler.openUri(it) },
                        )
                    }
                }

                line.isBlank() -> Spacer(Modifier.height(6.dp))

                else -> RichLine(
                    text = line,
                    style = MaterialTheme.typography.bodyLarge,
                    onLinkClick = { uriHandler.openUri(it) },
                )
            }
            i++
        }
    }
}

@Composable
private fun Heading(text: String, level: Int) {
    Text(
        text = inlineMarkdown(text),
        style = when (level) {
            1 -> MaterialTheme.typography.headlineSmall
            2 -> MaterialTheme.typography.titleLarge
            else -> MaterialTheme.typography.titleMedium
        },
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun RichLine(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit,
) {
    val annotated = inlineMarkdown(text)
    ClickableText(
        text = annotated,
        style = style.copy(color = MaterialTheme.colorScheme.onSurface),
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset)
                .firstOrNull()
                ?.let { onLinkClick(it.item) }
        },
    )
}

/** Преобразует базовую разметку в AnnotatedString. */
fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    INLINE_TOKEN.findAll(text).forEach { match ->
        append(text.substring(last, match.range.first))
        val token = match.value
        when {
            token.startsWith("**") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(token.removeSurrounding("**"))
                }
            }
            token.startsWith("`") -> {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = androidx.compose.ui.graphics.Color(0x22000000),
                    ),
                ) {
                    append(token.removeSurrounding("`"))
                }
            }
            token.startsWith("[") -> {
                val labelStart = token.indexOf('[') + 1
                val labelEnd = token.indexOf(']')
                val urlStart = token.indexOf('(') + 1
                val urlEnd = token.lastIndexOf(')')
                if (labelEnd > labelStart && urlEnd > urlStart) {
                    val label = token.substring(labelStart, labelEnd)
                    val url = token.substring(urlStart, urlEnd)
                    val start = length
                    withStyle(
                        SpanStyle(
                            color = androidx.compose.ui.graphics.Color(0xFF5B7CFA),
                            textDecoration = TextDecoration.Underline,
                        ),
                    ) {
                        append(label)
                    }
                    addStringAnnotation("URL", url, start, length)
                } else {
                    append(token)
                }
            }
            else -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(token.removeSurrounding("*"))
                }
            }
        }
        last = match.range.last + 1
    }
    append(text.substring(last))
}
