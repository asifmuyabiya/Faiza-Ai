package com.asif.familyhisab.data

/** What a fresh install starts with — Asif's family and the usual Bangladeshi household categories. */
object Defaults {

    val members = listOf(
        Member(FAMILY_ID, "পরিবার", "সবার যৌথ খরচ", "🏠", 0xFF6C5CE7, 0),
        Member("asif", "Asif", "আমি (লিডার)", "👑", 0xFF0984E3, 1),
        Member("irin", "Irin", "স্ত্রী", "🌸", 0xFFE84393, 2),
        Member("ibad", "Ibad", "ছেলে", "🧒", 0xFF00B894, 3),
        Member("abbu", "Moslem Mia", "আব্বু", "👴", 0xFFE17055, 4),
        Member("ammu", "Fazilatun Nessa", "আম্মু", "👵", 0xFFF39C12, 5),
    )

    private fun e(id: String, name: String, emoji: String, color: Long, bazar: Boolean = false) =
        Category(id, name, emoji, color, TxType.EXPENSE, bazar)

    private fun i(id: String, name: String, emoji: String, color: Long) =
        Category(id, name, emoji, color, TxType.INCOME)

    val categories = listOf(
        e("bazar", "বাজার", "🛒", 0xFF00B894, true),
        e("fish", "মাছ-মাংস", "🐟", 0xFF0984E3, true),
        e("veg", "সবজি-ফল", "🥬", 0xFF55EFC4, true),
        e("grocery", "চাল-ডাল-তেল", "🍚", 0xFFFDCB6E, true),
        e("food", "খাবার/নাস্তা", "🍔", 0xFFE17055),
        e("bill", "বিদ্যুৎ-গ্যাস-পানি", "💡", 0xFFF39C12),
        e("rent", "বাসা ভাড়া", "🏠", 0xFF6C5CE7),
        e("net", "মোবাইল-ইন্টারনেট", "📶", 0xFF74B9FF),
        e("transport", "যাতায়াত", "🚌", 0xFF00CEC9),
        e("medical", "ওষুধ-ডাক্তার", "💊", 0xFFD63031),
        e("education", "পড়াশোনা", "📚", 0xFF8E44AD),
        e("cloth", "কাপড়-চোপড়", "👕", 0xFFE84393),
        e("baby", "বাচ্চার জিনিস", "🧸", 0xFFFF7675),
        e("household", "ঘরের জিনিস", "🧹", 0xFFA29BFE),
        e("gift", "উপহার-দাওয়াত", "🎁", 0xFFFD79A8),
        e("religion", "দান-সদকা", "🤲", 0xFF00B894),
        e("travel", "ঘোরাঘুরি", "✈️", 0xFF0097E6),
        e("repair", "মেরামত", "🔧", 0xFF636E72),
        e("other", "অন্যান্য", "📦", 0xFFB2BEC3),
        i("salary", "বেতন", "💼", 0xFF00B894),
        i("business", "ব্যবসা", "🏪", 0xFF0984E3),
        i("rentin", "ভাড়া আয়", "🏘️", 0xFF6C5CE7),
        i("gift_in", "উপহার পেলাম", "🎁", 0xFFE84393),
        i("other_in", "অন্যান্য আয়", "💰", 0xFFFDCB6E),
    )

    val units = listOf("কেজি", "গ্রাম", "লিটার", "পিস", "হালি", "ডজন", "প্যাকেট", "আঁটি")

    val emojis = listOf(
        "👑", "🌸", "🧒", "👴", "👵", "👨", "👩", "👦", "👧", "🧑", "👶", "🏠",
        "🛒", "🐟", "🍗", "🥩", "🥬", "🍎", "🍚", "🥛", "🍞", "🍔", "☕", "🍰",
        "💡", "🔥", "💧", "📶", "📱", "🚌", "🚗", "⛽", "💊", "🏥", "📚", "✏️",
        "👕", "👗", "👟", "🧸", "🧹", "🛋️", "🎁", "🤲", "🕌", "✈️", "🔧", "📦",
        "💼", "🏪", "💰", "🎯", "🏍️", "💍", "🎓", "🐄", "🌾", "💻", "🎮", "⚽",
    )

    val colors = listOf(
        0xFF6C5CE7, 0xFF0984E3, 0xFFE84393, 0xFF00B894, 0xFFE17055, 0xFFF39C12,
        0xFFD63031, 0xFF8E44AD, 0xFF00CEC9, 0xFFFD79A8, 0xFF2D3436, 0xFF55EFC4,
    )
}
