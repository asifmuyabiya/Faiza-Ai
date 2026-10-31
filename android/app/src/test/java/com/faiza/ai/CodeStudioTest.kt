package com.faiza.ai

import com.faiza.ai.ai.code.CodeParsers
import com.faiza.ai.ai.code.LocalServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class CodeStudioTest {

    @Test fun parsesFilesFencesAndDeletes() {
        val reply = """
            Here you go.
            === FILE: index.html ===
            <h1>Hi</h1>
            === END FILE ===
            === FILE: `css/style.css` ===
            ```css
            body { color: red; }
            ```
            === END FILE ===
            === DELETE: old.js ===
            === FILE: js/app.js ===
            console.log(1)
        """.trimIndent()
        val e = CodeParsers.parseFiles(reply)
        assertEquals(listOf("index.html", "css/style.css", "js/app.js"), e.files.map { it.first })
        assertEquals("<h1>Hi</h1>\n", e.files[0].second)
        assertEquals("body { color: red; }\n", e.files[1].second)
        assertEquals("console.log(1)\n", e.files[2].second)
        assertEquals(listOf("old.js"), e.deletes)
    }

    @Test fun laterVersionOfAFileWins() {
        val e = CodeParsers.parseFiles("=== FILE: a.js ===\n1\n=== END FILE ===\n=== FILE: a.js ===\n2\n=== END FILE ===")
        assertEquals(listOf("a.js" to "2\n"), e.files)
    }

    @Test fun pathsCannotEscapeTheProject() {
        assertNull(CodeParsers.safePath("../etc/passwd"))
        assertNull(CodeParsers.safePath("a/../../b"))
        assertNull(CodeParsers.safePath("C:/Windows/x"))
        assertEquals("a/b.js", CodeParsers.safePath("/a/./b.js"))
        assertEquals("a/b.js", CodeParsers.safePath("a\\b.js"))
        assertTrue(CodeParsers.parseFiles("=== FILE: ../x.js ===\nbad\n=== END FILE ===").files.isEmpty())
    }

    @Test fun planKindAndName() {
        val p = CodeParsers.parsePlan("NAME: Shop Site\nTYPE: web\nFILES:\n- index.html", "x")
        assertEquals("Shop Site", p.name); assertEquals(CodeParsers.Kind.WEB, p.kind)
        assertEquals(CodeParsers.Kind.PYTHON, CodeParsers.parsePlan("TYPE: Python", "x").kind)
        assertEquals(CodeParsers.Kind.NODE, CodeParsers.parsePlan("TYPE: node", "x").kind)
        assertEquals("fallback", CodeParsers.parsePlan("no headers", "fallback").name)
    }

    @Test fun reviewVerdicts() {
        val ok = CodeParsers.parseReview("VERDICT: APPROVED\nISSUES:\n1. [MINOR] style.css — tighten spacing")
        assertTrue(ok.approved); assertEquals(1, ok.issues.size)
        val bad = CodeParsers.parseReview("VERDICT: CHANGES_NEEDED\nISSUES:\n1. [BLOCKER] app.js — crash\n2. [MINOR] x")
        assertFalse(bad.approved); assertEquals(2, bad.issues.size)
        // "Approved" but with a major issue is not approved.
        assertFalse(CodeParsers.parseReview("VERDICT: APPROVED\n- [MAJOR] broken layout").approved)
        assertFalse(CodeParsers.parseReview("looks fine").approved)
    }

    @Test fun slugs() {
        assertEquals("my-shop-site", CodeParsers.slug("My Shop Site!"))
        assertEquals("project", CodeParsers.slug("!!!"))
        assertEquals("আমার-দোকান", CodeParsers.slug("আমার দোকান"))
    }

    @Test fun bundleRespectsBudget() {
        val big = "x".repeat(10_000)
        val b = CodeParsers.bundle(listOf("a.js" to big, "b.js" to big), budget = 12_000)
        assertTrue(b.length < 13_000)
        assertTrue(b.contains("=== FILE: a.js ==="))
    }

    @Test fun localServerServesProjectFiles() {
        val root = createTempDir("faiza-code")
        File(root, "site/css").mkdirs()
        File(root, "site/index.html").writeText("<h1>hello</h1>")
        File(root, "site/css/s.css").writeText("body{}")
        LocalServer.ensure(root)
        fun get(path: String): Pair<Int, String> {
            val c = URL("http://127.0.0.1:${LocalServer.PORT}$path").openConnection() as HttpURLConnection
            c.instanceFollowRedirects = true
            val code = c.responseCode
            val body = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
            return code to body
        }
        assertEquals(200 to "<h1>hello</h1>", get("/site/"))
        assertEquals(200, get("/site/css/s.css").first)
        assertEquals(404, get("/site/missing.js").first)
        assertTrue(get("/site/../../etc/passwd").first in setOf(403, 404))
        assertTrue(get("/").second.contains("site"))
        root.deleteRecursively()
    }
}
