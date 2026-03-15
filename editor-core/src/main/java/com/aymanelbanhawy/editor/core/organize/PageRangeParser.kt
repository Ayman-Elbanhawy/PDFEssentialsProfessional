package com.aymanelbanhawy.editor.core.organize

object PageRangeParser {
    fun parse(expression: String, pageCount: Int): List<IntRange> {
        val normalized = expression.trim()
        require(normalized.isNotEmpty()) { "Range expression cannot be empty." }
        return normalized.split(',')
            .map { token -> token.trim() }
            .filter { it.isNotEmpty() }
            .map { token -> parseToken(token, pageCount) }
    }

    fun expand(expression: String, pageCount: Int): List<Int> {
        return parse(expression, pageCount)
            .flatMap { range -> range.toList() }
            .distinct()
            .sorted()
    }

    private fun parseToken(token: String, pageCount: Int): IntRange {
        return if ('-' in token) {
            val parts = token.split('-', limit = 2)
            require(parts.size == 2) { "Invalid range token: $token" }
            val start = parsePageNumber(parts[0], pageCount)
            val end = parsePageNumber(parts[1], pageCount)
            require(start <= end) { "Range start must be <= end in $token" }
            start..end
        } else {
            val page = parsePageNumber(token, pageCount)
            page..page
        }
    }

    private fun parsePageNumber(raw: String, pageCount: Int): Int {
        val page = raw.trim().toIntOrNull() ?: error("Invalid page number: $raw")
        require(page in 1..pageCount) { "Page $page is out of bounds for document size $pageCount" }
        return page - 1
    }
}
