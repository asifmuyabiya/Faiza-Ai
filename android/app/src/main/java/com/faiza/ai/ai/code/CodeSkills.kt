package com.faiza.ai.ai.code

import android.content.Intent
import com.faiza.ai.ai.provider.ParamType
import com.faiza.ai.ai.provider.ToolParam
import com.faiza.ai.ai.skills.Skill
import com.faiza.ai.ai.skills.require
import com.faiza.ai.ai.skills.str
import com.faiza.ai.ui.CodeStudioActivity
import com.faiza.ai.ui.windows.BrowserWindow
import com.faiza.ai.ui.windows.WindowCenter
import com.faiza.ai.ui.windows.WindowKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Voice control for Code Studio. */
object CodeSkills {

    fun skills(): List<Skill> = listOf(
        Skill(
            "code_studio", "💻", "Code Studio", "\"আমার দোকানের জন্য একটা ওয়েবসাইট বানাও\"",
            "Faiza's software team builds websites, web apps, games, Python or Node programs: a manager plans, a coder " +
                    "writes, a tester tests, a runner runs it (websites in a real browser with a screenshot), a reviewer " +
                    "checks code and design, a fixer repairs — repeating until it's right. Runs in the background " +
                    "(minutes); you are told when it's done. action=build (new project: put the owner's full wishes in " +
                    "request — purpose, pages, features, style, colours, language of the site) | change (an existing " +
                    "project + what to change) | status | stop | list | preview (show a website in the floating window) " +
                    "| export (ZIP to Downloads for uploading) | open (the Code Studio screen). Before build, if the " +
                    "request is very vague, ask ONE short question about what matters most; otherwise just start.",
            listOf(
                ToolParam("action", ParamType.STRING, "build | change | status | stop | list | preview | export | open"),
                ToolParam("request", ParamType.STRING, "what to build or change, in detail", required = false),
                ToolParam("project", ParamType.STRING, "project name (for change / preview / export)", required = false)
            )
        ) { ctx, args ->
            val action = args.require("action").lowercase()
            fun project() = CodeProjects.find(ctx, args.str("project"))
                ?: CodeAgent.currentSlug.value?.let { CodeProjects.find(ctx, it) }
                ?: CodeProjects.list(ctx).firstOrNull()
            when (action) {
                "build", "change" -> {
                    if (CodeAgent.running) return@Skill "BUSY: Code Studio is still working on the last job. Use status, or stop it first."
                    val req = args.require("request")
                    val slug = if (action == "change") (project() ?: return@Skill "ERROR: no such project. Use list.").slug else null
                    CodeAgent.start(ctx, req, slug, tellWhenDone = true)
                    "STARTED (${CodeAgent.brainLabel()}). Tell the user in one sentence that the team started and you'll " +
                            "tell them when it's ready; they can watch it in Code Studio."
                }
                "status" -> if (!CodeAgent.running) {
                    CodeAgent.outcome.value?.let { "Not running. Last: ${it.name} — ${it.summary.take(400)}" } ?: "Not running."
                } else "RUNNING — now: ${CodeAgent.stage.value.label}. Recent:\n" + CodeAgent.log.value.takeLast(6).joinToString("\n")
                "stop" -> { CodeAgent.cancel(); "Stopped." }
                "list" -> CodeProjects.list(ctx).take(15).joinToString("\n") { "${it.meta.name} (${it.kind.name.lowercase()}, ${it.meta.status})" }
                    .ifEmpty { "No projects yet." }
                "preview" -> {
                    val p = project() ?: return@Skill "No projects yet."
                    if (p.kind != CodeParsers.Kind.WEB) return@Skill "${p.meta.name} isn't a website; open Code Studio to run it."
                    LocalServer.ensure(CodeProjects.root(ctx))
                    withContext(Dispatchers.Main) {
                        (WindowCenter.show(ctx, WindowKind.BROWSER) as? BrowserWindow)?.go(LocalServer.url(p.slug))
                    } ?: return@Skill "ERROR: can't show windows over other apps."
                    "Showing ${p.meta.name} in the floating browser."
                }
                "export" -> {
                    val p = project() ?: return@Skill "No projects yet."
                    val where = withContext(Dispatchers.IO) { runCatching { p.exportZip(ctx) } }
                        .getOrElse { return@Skill "ERROR: ${it.message}" }
                    "Saved $where. To publish: open Netlify Drop and choose this ZIP (Code Studio → project → How to put it online)."
                }
                "open" -> {
                    ctx.startActivity(Intent(ctx, CodeStudioActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    "Code Studio is open."
                }
                else -> "ERROR: unknown action"
            }
        }
    )
}
