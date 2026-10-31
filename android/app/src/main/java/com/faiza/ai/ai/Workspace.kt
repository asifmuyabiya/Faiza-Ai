package com.faiza.ai.ai

/**
 * Work rooms in the side menu (MAYA 5.2): the same chat, set up for one kind of job — the right tools, the right
 * format and three starter ideas.
 */
enum class Workspace(
    val id: String,
    val emoji: String,
    val title: String,
    val subtitle: String,
    val chips: List<String>,
    val instruction: String
) {
    MARKETS(
        "markets", "📈", "Markets", "Prices, news and what's moving",
        listOf("Today's gold price in Bangladesh", "Bitcoin price and why it moved", "Dollar to taka rate today"),
        "You are in the MARKETS room. For any price, rate or market news, ALWAYS call search_web first and answer from " +
                "the results with the number, the time/date it was published and the source. Keep it short: the figure, " +
                "the change, one line on why. Never invent prices. Add \"not financial advice\" when the user asks what to " +
                "buy or sell, and don't tell them what to buy or sell."
    ),
    MARKETING(
        "marketing", "📣", "Marketing", "Posts, captions, ads and ideas",
        listOf("Write a Facebook post for my shop", "10 catchy captions for a new product", "A short YouTube video idea"),
        "You are in the MARKETING room. Write ready-to-use copy: posts, captions, ad text, hashtags, video hooks and " +
                "content plans. Ask for the product, audience and tone only if they are missing. Give 2–3 options when " +
                "useful, in the user's language (Bangla, English or Banglish), formatted so it can be copied straight away."
    ),
    DOCUMENTS(
        "documents", "📄", "Documents", "Letters, CVs, reports, summaries",
        listOf("Write a job application letter", "Make my CV from these details", "Summarise this PDF"),
        "You are in the DOCUMENTS room. Write clean, well-structured documents (letters, applications, CVs, reports, " +
                "notices) with headings and proper format. When the user attaches a file, read it and summarise or edit " +
                "it as asked. When they want a file, use create_file to save it and tell them where it is."
    ),
    CODING(
        "coding", "💻", "Website / Coding", "Code, websites, fixing errors",
        listOf("Make a simple landing page in HTML", "Explain this error", "Write a Python script to rename files"),
        "You are in the WEBSITE / CODING room. Write complete, working code in fenced code blocks with the language " +
                "named, then a short explanation of how to run it. For websites, give one self-contained HTML file unless " +
                "asked otherwise. When fixing an error, say the cause in one line, then the fix. Use create_file when the " +
                "user wants the code saved on the phone."
    ),
    STUDY(
        "study", "📚", "Study / Whiteboard", "Learn step by step",
        listOf("Explain photosynthesis simply", "Solve this maths problem step by step", "Quiz me on English grammar"),
        "You are in the STUDY room, a patient tutor. Explain step by step like on a whiteboard: numbered steps, simple " +
                "words, a small example, then a one-line summary. For maths, show every step and the final answer clearly. " +
                "When the user sends a photo of a question, solve it. Offer a quick practice question at the end."
    );

    companion object {
        fun from(id: String?): Workspace? = values().firstOrNull { it.id == id }
    }
}
