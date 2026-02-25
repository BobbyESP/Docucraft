package com.bobbyesp.docucraft.core.util.text

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.text.HtmlCompat

fun htmlToAnnotatedString(html: String): AnnotatedString {
    val spanned: Spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
    val annotatedString = AnnotatedString.Builder()

    spanned.getSpans(0, spanned.length, Any::class.java).forEach { span ->
        val start = spanned.getSpanStart(span)
        val end = spanned.getSpanEnd(span)

        when (span) {
            is StyleSpan -> {
                when (span.style) {
                    Typeface.BOLD ->
                        annotatedString.addStyle(
                            SpanStyle(fontWeight = FontWeight.Bold),
                            start,
                            end,
                        )

                    Typeface.ITALIC ->
                        annotatedString.addStyle(
                            SpanStyle(fontStyle = FontStyle.Italic),
                            start,
                            end,
                        )
                }
            }

            is UnderlineSpan ->
                annotatedString.addStyle(
                    SpanStyle(textDecoration = TextDecoration.Underline),
                    start,
                    end,
                )

            is StrikethroughSpan ->
                annotatedString.addStyle(
                    SpanStyle(textDecoration = TextDecoration.LineThrough),
                    start,
                    end,
                )

            is ForegroundColorSpan ->
                annotatedString.addStyle(SpanStyle(color = Color(span.foregroundColor)), start, end)
        }
    }

    annotatedString.append(spanned.toString())
    return annotatedString.toAnnotatedString()
}
