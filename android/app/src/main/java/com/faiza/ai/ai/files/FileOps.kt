package com.faiza.ai.ai.files

/** Pure helpers for the file manager (unit-tested). */
object FileOps {

    /** A file seen while scanning: path relative to storage, size, and (for same-size files) a content hash. */
    data class Seen(val path: String, val size: Long, val modified: Long, val hash: String? = null)

    /** Folders Faiza never reorganises or bins: system / app data and the camera roll itself. */
    fun isProtected(relPath: String): Boolean {
        val p = relPath.trim('/').lowercase()
        if (p.isEmpty()) return true // the storage root itself
        return p == "android" || p.startsWith("android/data") || p.startsWith("android/obb") ||
                p == "dcim/camera" || p.startsWith(".") || p.contains("/.")
    }

    /** Groups of identical files (same size and same hash), biggest waste first. The oldest copy is kept first. */
    fun duplicateGroups(files: List<Seen>): List<List<Seen>> =
        files.filter { it.hash != null && it.size > 0 }
            .groupBy { it.size to it.hash }
            .values.filter { it.size > 1 }
            .map { g -> g.sortedBy { it.modified } }
            .sortedByDescending { g -> g.first().size * (g.size - 1) }

    /** Space duplicates waste (every copy beyond the first). */
    fun wasted(groups: List<List<Seen>>): Long = groups.sumOf { g -> g.first().size * (g.size - 1) }

    /** Category → (count, bytes), biggest first. */
    fun byCategory(files: List<Seen>): List<Triple<String, Int, Long>> =
        files.groupBy { FilePlanner.category(it.path.substringAfterLast('/')) }
            .map { (cat, list) -> Triple(cat, list.size, list.sumOf { it.size }) }
            .sortedByDescending { it.third }

    /** A name for a file put in Faiza Bin, unique and remembering when: "20261006-1030__report.pdf". */
    fun binName(original: String, stamp: String): String = "${stamp}__${original.substringAfterLast('/')}"

    /** The original file name from a bin name. */
    fun originalName(binName: String): String = binName.substringAfter("__", binName)

    /** Old installer files lying in Download (APKs older than [days]). */
    fun oldApks(files: List<Seen>, now: Long, days: Int = 14): List<Seen> =
        files.filter { it.path.lowercase().endsWith(".apk") && now - it.modified > days * 86_400_000L }
}
