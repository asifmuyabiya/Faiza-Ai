package com.asif.familyhisab.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * The whole hisab lives in one JSON file inside the app (no login, no server, no internet).
 * Each save writes a temp file then renames it, and keeps the previous version as a .bak,
 * so a crash mid-save can never wipe the data.
 */
class Store private constructor(context: Context) {

    private val dir = context.filesDir
    private val main = File(dir, "hisab.json")
    private val bak = File(dir, "hisab.bak.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    /** Items handed from the shopping list to the add screen. */
    var pendingItems: List<BazarItem> = emptyList()

    private fun load(): AppData {
        for (f in listOf(main, bak)) {
            if (!f.exists()) continue
            runCatching { return Backup.fromJson(f.readText()) }
        }
        return AppData()
    }

    fun update(transform: (AppData) -> AppData) {
        _data.update(transform)
        val snapshot = _data.value
        scope.launch {
            writeLock.withLock {
                val tmp = File(dir, "hisab.tmp")
                tmp.writeText(Backup.toJson(snapshot))
                if (main.exists()) main.copyTo(bak, overwrite = true)
                if (!tmp.renameTo(main)) { main.writeText(tmp.readText()); tmp.delete() }
            }
        }
    }

    // ---------- Transactions ----------
    fun saveTxn(t: Txn) = update { d ->
        val exists = d.txns.any { it.id == t.id }
        d.copy(txns = if (exists) d.txns.map { if (it.id == t.id) t else it } else d.txns + t)
    }
    fun deleteTxn(id: String) = update { d -> d.copy(txns = d.txns.filterNot { it.id == id }) }

    // ---------- Members / categories ----------
    fun saveMember(m: Member) = update { d ->
        val exists = d.members.any { it.id == m.id }
        d.copy(members = if (exists) d.members.map { if (it.id == m.id) m else it }
        else d.members + m.copy(order = (d.members.maxOfOrNull { it.order } ?: 0) + 1))
    }
    fun saveCategory(c: Category) = update { d ->
        val exists = d.categories.any { it.id == c.id }
        d.copy(categories = if (exists) d.categories.map { if (it.id == c.id) c else it } else d.categories + c)
    }

    // ---------- Budget ----------
    fun saveBudget(b: Budget) = update { it.copy(budget = b) }

    // ---------- Debts ----------
    fun saveDebt(x: Debt) = update { d ->
        val exists = d.debts.any { it.id == x.id }
        d.copy(debts = if (exists) d.debts.map { if (it.id == x.id) x else it } else d.debts + x)
    }
    fun deleteDebt(id: String) = update { d -> d.copy(debts = d.debts.filterNot { it.id == id }) }

    // ---------- Shopping list ----------
    fun addShop(name: String, qty: String) = update { d -> d.copy(shopping = d.shopping + ShopItem(name = name, qty = qty)) }
    fun toggleShop(id: String) = update { d -> d.copy(shopping = d.shopping.map { if (it.id == id) it.copy(done = !it.done) else it }) }
    fun deleteShop(id: String) = update { d -> d.copy(shopping = d.shopping.filterNot { it.id == id }) }
    fun clearDoneShop() = update { d -> d.copy(shopping = d.shopping.filterNot { it.done }) }

    // ---------- Goals ----------
    fun saveGoal(g: Goal) = update { d ->
        val exists = d.goals.any { it.id == g.id }
        d.copy(goals = if (exists) d.goals.map { if (it.id == g.id) g else it } else d.goals + g)
    }
    fun deleteGoal(id: String) = update { d -> d.copy(goals = d.goals.filterNot { it.id == id }) }

    // ---------- Settings / backup ----------
    fun saveSettings(s: Settings) = update { it.copy(settings = s) }
    fun replaceAll(newData: AppData) = update { newData.copy(settings = newData.settings.copy(lastBackupAt = it.settings.lastBackupAt)) }
    fun mergeIn(incoming: AppData) = update { Calc.merge(it, incoming) }
    fun markBackedUp() = update { it.copy(settings = it.settings.copy(lastBackupAt = System.currentTimeMillis())) }
    fun resetAll() = update { AppData(settings = it.settings) }

    companion object {
        @Volatile private var instance: Store? = null
        fun get(context: Context): Store = instance ?: synchronized(this) {
            instance ?: Store(context.applicationContext).also { instance = it }
        }
    }
}
