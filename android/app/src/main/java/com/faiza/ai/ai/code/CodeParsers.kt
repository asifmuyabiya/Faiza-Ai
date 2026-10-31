package com.faiza.ai.ai.code

/** Pure text helpers for Code Studio (unit-tested). */
object CodeParsers {

    enum class Kind { WEB, PYTHON, NODE }

    data class Plan(val name: String, val kind: Kind, val text: String)

    data class Review(val approved: Boolean, val issues: List<String>, val text: String)

    data class Edits(val files: List<Pair<String, String>>, val deletes: List<String>)

    private val FILE_START = Regex("""^=+\s*FILE:\s*(.+?)\s*=+\s*$""")
    private val FILE_END = Regex("""^=+\s*END( FILE)?\s*=+\s*$""")
    private val DELETE = Regex("""^=+\s*DELETE:\s*(.+?)\s*=+\s*$""")

    /**
     * Files from a reply written as
     *   === FILE: path/to/file ===
     *   …content…
     *   === END FILE ===
     * A missing END closes the file at the next FILE header or the end of the reply.
     */
    fun parseFiles(reply: String): Edits {
        val files = mutableListOf<Pair<String, String>>()
        val deletes = mutableListOf<String>()
        var path: String? = null
        val buf = StringBuilder()
        fun close() {
            val p = path ?: return
            safePath(p)?.let { files += it to stripFence(buf.toString()) }
            path = null; buf.setLength(0)
        }
        reply.lines().forEach { line ->
            val start = FILE_START.matchEntire(line.trim())
            val del = DELETE.matchEntire(line.trim())
            when {
                start != null -> { close(); path = start.groupValues[1].trim('`', '"', '\'', ' ') }
                del != null -> { close(); safePath(del.groupValues[1].trim('`', '"', '\'', ' '))?.let { deletes += it } }
                path != null && FILE_END.matches(line.trim()) -> close()
                path != null -> buf.append(line).append('\n')
            }
        }
        close()
        // Last version of a file wins.
        val dedup = LinkedHashMap<String, String>()
        files.forEach { (p, c) -> dedup[p] = c }
        return Edits(dedup.toList(), deletes.distinct())
    }

    /** Removes a ``` fence that wraps the whole file, which models like to add. */
    fun stripFence(content: String): String {
        val t = content.trimEnd('\n', ' ')
        val lines = t.lines()
        if (lines.size >= 2 && lines.first().trim().startsWith("```") && lines.last().trim() == "```") {
            return lines.drop(1).dropLast(1).joinToString("\n") + "\n"
        }
        return if (t.isEmpty()) "" else t + "\n"
    }

    /** A relative path inside the project, or null when it tries to leave it. */
    fun safePath(p: String): String? {
        val parts = p.replace('\\', '/').trim().trimStart('/').split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." } || parts.first().contains(':')) return null
        return parts.joinToString("/")
    }

    fun parsePlan(reply: String, fallbackName: String): Plan {
        val name = Regex("""(?im)^\s*NAME:\s*(.+)$""").find(reply)?.groupValues?.get(1)?.trim()?.take(40) ?: fallbackName
        val kindWord = Regex("""(?im)^\s*TYPE:\s*(\w+)""").find(reply)?.groupValues?.get(1)?.lowercase().orEmpty()
        val kind = when {
            kindWord.startsWith("py") -> Kind.PYTHON
            kindWord.startsWith("node") || kindWord == "js" || kindWord == "javascript" -> Kind.NODE
            else -> Kind.WEB
        }
        return Plan(name, kind, reply.trim())
    }

    /** "VERDICT: APPROVED" or a list of issues. Anything unclear counts as not approved. */
    fun parseReview(reply: String): Review {
        val verdict = Regex("""(?im)^\s*VERDICT:\s*([A-Z_ ]+)""").find(reply)?.groupValues?.get(1)?.trim()?.uppercase().orEmpty()
        val issues = reply.lines().map { it.trim() }
            .filter { Regex("""^(\d+[.)]|[-*•])\s+""").containsMatchIn(it) }
            .map { it.replace(Regex("""^(\d+[.)]|[-*•])\s+"""), "") }
            .filter { it.isNotBlank() && !it.equals("none", true) }
        val approved = verdict.startsWith("APPROVED") && issues.none { it.startsWith("[BLOCKER]", true) || it.startsWith("[MAJOR]", true) }
        return Review(approved, issues, reply.trim())
    }

    fun slug(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9\\u0980-\\u09FF]+"), "-").trim('-').take(40).ifEmpty { "project" }

    /** Project files as one block for a prompt, biggest budget first to the files that matter. */
    fun bundle(files: List<Pair<String, String>>, budget: Int = 180_000): String {
        val sb = StringBuilder()
        files.sortedBy { (p, _) -> if (p.endsWith(".md") || p.endsWith(".txt")) 1 else 0 }.forEach { (p, c) ->
            val room = budget - sb.length
            if (room <= 200) { sb.append("=== FILE: $p === (omitted, too long)\n"); return@forEach }
            sb.append("=== FILE: $p ===\n").append(if (c.length > room - 100) c.take(room - 100) + "\n…(cut)\n" else c)
            if (!c.endsWith("\n")) sb.append('\n')
            sb.append("=== END FILE ===\n\n")
        }
        return sb.toString()
    }

    /** Text files we read back into prompts; images, zips etc. are listed only. */
    fun isText(path: String): Boolean = path.substringAfterLast('.', "").lowercase() in setOf(
        "html", "htm", "css", "js", "mjs", "cjs", "ts", "jsx", "tsx", "json", "md", "txt", "py", "toml", "yaml", "yml",
        "svg", "xml", "csv", "sh", "env", "ini", "cfg", "gitignore", "vue", "svelte", "sql"
    ) || !path.contains('.')
}
