package com.asif.familyhisab.data

import kotlinx.serialization.Serializable
import java.util.UUID

/** Every amount in the app is stored in poisha (৳1 = 100) so sums never pick up floating-point dust. */
typealias Poisha = Long

fun newId(): String = UUID.randomUUID().toString().substring(0, 13)

/** The shared "member" that household costs (bazar, bills, rent) are booked to. */
const val FAMILY_ID = "family"

@Serializable
enum class TxType { EXPENSE, INCOME }

@Serializable
enum class PayMethod(val label: String, val emoji: String) {
    CASH("ক্যাশ", "💵"),
    BKASH("বিকাশ", "📱"),
    NAGAD("নগদ", "📲"),
    ROCKET("রকেট", "🚀"),
    BANK("ব্যাংক", "🏦"),
    CARD("কার্ড", "💳"),
}

@Serializable
data class Member(
    val id: String = newId(),
    val name: String,
    val relation: String = "",
    val emoji: String = "🙂",
    val color: Long = 0xFF6C5CE7,
    val order: Int = 0,
    val archived: Boolean = false,
) {
    val isFamily get() = id == FAMILY_ID
}

@Serializable
data class Category(
    val id: String = newId(),
    val name: String,
    val emoji: String,
    val color: Long,
    val type: TxType = TxType.EXPENSE,
    val isBazar: Boolean = false,
    val archived: Boolean = false,
)

@Serializable
data class BazarItem(
    val name: String,
    val qty: Double = 1.0,
    val unit: String = "কেজি",
    val price: Poisha = 0, // total price of this line
)

@Serializable
data class Txn(
    val id: String = newId(),
    val type: TxType = TxType.EXPENSE,
    val amount: Poisha,
    val categoryId: String,
    /** Who the money was spent on (or whose income it is). [FAMILY_ID] = the whole household. */
    val memberId: String = FAMILY_ID,
    /** Days since 1970-01-01 (LocalDate.toEpochDay). */
    val day: Long,
    val note: String = "",
    val method: PayMethod = PayMethod.CASH,
    val items: List<BazarItem> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class Budget(
    val monthlyTotal: Poisha = 0,
    val perCategory: Map<String, Poisha> = emptyMap(),
    val perMember: Map<String, Poisha> = emptyMap(),
)

@Serializable
enum class DebtDir { GAVE, TOOK } // GAVE = someone owes me (পাবো), TOOK = I owe (দেবো)

@Serializable
data class DebtPayment(val amount: Poisha, val day: Long, val note: String = "")

@Serializable
data class Debt(
    val id: String = newId(),
    val person: String,
    val phone: String = "",
    val dir: DebtDir,
    val amount: Poisha,
    val day: Long,
    val dueDay: Long? = null,
    val note: String = "",
    val payments: List<DebtPayment> = emptyList(),
) {
    val paid: Poisha get() = payments.sumOf { it.amount }
    val remaining: Poisha get() = (amount - paid).coerceAtLeast(0)
    val settled: Boolean get() = remaining == 0L
}

@Serializable
data class ShopItem(
    val id: String = newId(),
    val name: String,
    val qty: String = "",
    val done: Boolean = false,
)

@Serializable
data class Goal(
    val id: String = newId(),
    val name: String,
    val emoji: String = "🎯",
    val target: Poisha,
    val saved: Poisha = 0,
    val color: Long = 0xFF00B894,
)

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class Settings(
    val ownerName: String = "Asif",
    val banglaDigits: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val lastBackupAt: Long = 0,
)

@Serializable
data class AppData(
    val version: Int = 1,
    val members: List<Member> = Defaults.members,
    val categories: List<Category> = Defaults.categories,
    val txns: List<Txn> = emptyList(),
    val budget: Budget = Budget(),
    val debts: List<Debt> = emptyList(),
    val shopping: List<ShopItem> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val settings: Settings = Settings(),
) {
    fun member(id: String): Member? = members.firstOrNull { it.id == id }
    fun category(id: String): Category? = categories.firstOrNull { it.id == id }
    val activeMembers get() = members.filter { !it.archived }.sortedBy { it.order }
}
