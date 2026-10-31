import os
main = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main\java\com\faiza\ai'


def edit(p, a, b, cnt=1):
    p = os.path.join(main, *p.split('/'))
    s = open(p, encoding='utf-8').read()
    assert s.count(a) == cnt, (p, a[:70], s.count(a))
    open(p, 'w', encoding='utf-8').write(s.replace(a, b))


p = 'ai/FaizaAgent.kt'
edit(p, '''            val skills = activeSkills()
            val specs = skills.map { it.spec }''', '''            val skills = runnable()
            val specs = toolsToSend().map { it.spec }''')
edit(p, '''                for (call in reply.toolCalls) {
                    history += ChatTurn.ToolResult(call.id, call.name, runSkill(skills, call))
                }''', '''                for (call in reply.toolCalls) {
                    history += ChatTurn.ToolResult(call.id, call.name, runSkill(skills, com.faiza.ai.ai.skills.ToolRouter.unwrap(call)))
                }''')
edit(p, '''    fun currentSkills(): List<Skill> = activeSkills()''', '''    fun currentSkills(): List<Skill> = toolsToSend()

    // ====== Fewer tools per request (faster answers) ======

    private val fastTools get() = prefs.isFastTools

    /** What the model is given: the everyday tools in full + use_tool / tool_help for the rest. */
    private fun toolsToSend(): List<Skill> {
        val all = activeSkills()
        if (!fastTools) return all
        val (core, rest) = com.faiza.ai.ai.skills.ToolRouter.split(all)
        return if (rest.isEmpty()) core else core + com.faiza.ai.ai.skills.ToolRouter.metaSkills(rest)
    }

    /** Everything that may actually run (the real tools + tool_help). */
    private fun runnable(): List<Skill> {
        val all = activeSkills()
        if (!fastTools) return all
        val rest = com.faiza.ai.ai.skills.ToolRouter.split(all).second
        return all + com.faiza.ai.ai.skills.ToolRouter.metaSkills(rest).filter { it.id == com.faiza.ai.ai.skills.ToolRouter.HELP }
    }

    private fun toolCatalog(): String {
        if (!fastTools) return ""
        val rest = com.faiza.ai.ai.skills.ToolRouter.split(activeSkills()).second
        return com.faiza.ai.ai.skills.ToolRouter.catalog(rest)
    }''')
edit(p, '''    suspend fun executeTool(call: ToolCall): String = runSkill(activeSkills(), call)''',
     '''    suspend fun executeTool(call: ToolCall): String = runSkill(runnable(), com.faiza.ai.ai.skills.ToolRouter.unwrap(call))''')
edit(p, '''            .replace("{RELATIONSHIP}", relationship)
    }
}''', '''            .replace("{RELATIONSHIP}", relationship)
            .let { prompt -> toolCatalog().let { if (it.isEmpty()) prompt else prompt + "\\n\\n" + it } }
    }
}''')

# Live: unwrap before choosing the lane, so phone tasks still run one after another.
p = 'service/FloatingOrbService.kt'
edit(p, '''        override suspend fun onToolCall(call: ToolCall): String {
            lastUserActivityAt = System.currentTimeMillis()''', '''        override suspend fun onToolCall(raw: ToolCall): String {
            val call = com.faiza.ai.ai.skills.ToolRouter.unwrap(raw)
            lastUserActivityAt = System.currentTimeMillis()''')

edit('util/PrefsManager.kt', '''    // Driving''', '''    /** Send only the everyday tools in full (faster, more accurate answers). */
    var isFastTools: Boolean
        get() = prefs.getBoolean("fast_tools", true)
        set(value) = prefs.edit().putBoolean("fast_tools", value).apply()

    // Driving''')
print('ok')
