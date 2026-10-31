base = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main\java\com\faiza\ai'

p = base + r'\ai\guardian\OwnerLock.kt'
s = open(p, encoding='utf-8').read()
a = '''        val latin = buildString {
            for (ch in text.lowercase()) append(TRANSLIT[ch] ?: ch.toString())
        }'''
assert s.count(a) == 1
s = s.replace(a, '''        // "য়" / "ড়" / "ঢ়" can be one letter or letter + dot (nukta): treat both spellings the same.
        val src = java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .replace("\\u09AF\\u09BC", "y").replace("\\u09A1\\u09BC", "r").replace("\\u09A2\\u09BC", "r")
            .replace("\\u09BC", "").replace("\\u093C", "")
        val latin = buildString {
            for (ch in src) append(TRANSLIT[ch] ?: ch.toString())
        }''')
open(p, 'w', encoding='utf-8').write(s)

p = base + r'\ai\contacts\FavoriteContacts.kt'
s = open(p, encoding='utf-8').read()
a = '''        var t = s.trim().trimEnd('.', '!', '?', ',')'''
assert s.count(a) == 1
s = s.replace(a, '''        // One spelling of "য়" (letter + dot), so the endings below always match.
        var t = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).trim().trimEnd('.', '!', '?', ',')''')
a = '''            t = t.replace(Regex("(?i)(^|\\\\s)" + Regex.escape(w) + "(\\\\s|$)"), " ").trim()'''
assert s.count(a) == 1, "regex line"
s = s.replace(a, '''            t = t.replace(Regex("(^|\\\\s)" + Regex.escape(w) + "(\\\\s|$)", RegexOption.IGNORE_CASE), " ").trim()''')
start = s.index('        val endings = listOf(')
end = s.index('        )', start) + len('        )')
s = s[:start] + '''        val y = "\\u09AF\\u09BC" // য়
        val endings = listOf(
            "${y}েরকে", "${y}েরে", "${y}ের", "${y}ে", "েরকে", "দেরকে", "দের", "টাকে", "টার", "টা",
            "কেও", "কে", "এর", "ের", "র", "ko", "को", "ka", "ki", "का", "की", "के"
        )''' + s[end:]
open(p, 'w', encoding='utf-8').write(s)
print("ok")
