package com.faiza.ai

import com.faiza.ai.ai.files.FileOps
import com.faiza.ai.ai.files.FileOps.Seen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileOpsTest {

    @Test
    fun protectedFolders() {
        listOf("", "/", "Android", "Android/data/com.x", "android/obb", "DCIM/Camera", ".thumbnails", "Download/.hidden").forEach {
            assertTrue(it, FileOps.isProtected(it))
        }
        listOf("Download", "Documents/Office", "Pictures", "Telegram", "DCIM/Screenshots").forEach {
            assertFalse(it, FileOps.isProtected(it))
        }
    }

    @Test
    fun duplicatesKeepTheOldestAndCountWaste() {
        val files = listOf(
            Seen("Download/a.jpg", 1_000, modified = 30, hash = "h1"),
            Seen("Pictures/a (1).jpg", 1_000, modified = 10, hash = "h1"),
            Seen("WhatsApp/Media/a.jpg", 1_000, modified = 20, hash = "h1"),
            Seen("Download/big.mp4", 50_000, modified = 5, hash = "h2"),
            Seen("Movies/big.mp4", 50_000, modified = 9, hash = "h2"),
            Seen("Download/same-size-different.mp4", 50_000, modified = 1, hash = "h3"),
            Seen("Download/unhashed.txt", 10, modified = 1, hash = null)
        )
        val groups = FileOps.duplicateGroups(files)
        assertEquals(2, groups.size)
        assertEquals("Download/big.mp4", groups[0].first().path) // biggest waste first, oldest copy kept first
        assertEquals("Pictures/a (1).jpg", groups[1].first().path)
        assertEquals(50_000L + 2_000L, FileOps.wasted(groups))
    }

    @Test
    fun binNamesAndCategories() {
        val b = FileOps.binName("Download/report.pdf", "20261006-103000")
        assertEquals("20261006-103000__report.pdf", b)
        assertEquals("report.pdf", FileOps.originalName(b))
        val cats = FileOps.byCategory(listOf(Seen("x/a.jpg", 5, 0), Seen("x/b.png", 5, 0), Seen("x/c.pdf", 100, 0)))
        assertEquals(2, cats.size)
        assertEquals(100L, cats[0].third)
        val now = 100L * 86_400_000L
        val apks = FileOps.oldApks(listOf(Seen("Download/old.apk", 1, now - 30L * 86_400_000L), Seen("Download/new.apk", 1, now)), now)
        assertEquals(listOf("Download/old.apk"), apks.map { it.path })
    }
}
