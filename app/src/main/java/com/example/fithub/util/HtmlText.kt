package com.example.fithub.util

/** The wger API returns exercise descriptions as HTML; this turns them into plain text. */
object HtmlText {
    private val TAG = Regex("<[^>]*>")
    private val WHITESPACE = Regex("\\s+")

    fun strip(html: String?): String {
        if (html.isNullOrBlank()) return ""
        return html
            .replace(TAG, " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(WHITESPACE, " ")
            .trim()
    }
}
