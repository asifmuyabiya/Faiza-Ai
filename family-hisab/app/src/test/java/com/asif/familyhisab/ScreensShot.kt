package com.asif.familyhisab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.asif.familyhisab.data.AppData
import com.asif.familyhisab.data.BazarItem
import com.asif.familyhisab.data.Budget
import com.asif.familyhisab.data.Debt
import com.asif.familyhisab.data.DebtDir
import com.asif.familyhisab.data.FAMILY_ID
import com.asif.familyhisab.data.Goal
import com.asif.familyhisab.data.PayMethod
import com.asif.familyhisab.data.ShopItem
import com.asif.familyhisab.data.Store
import com.asif.familyhisab.data.ThemeMode
import com.asif.familyhisab.data.TxType
import com.asif.familyhisab.data.Txn
import com.asif.familyhisab.ui.Nav
import com.asif.familyhisab.ui.screens.AddScreen
import com.asif.familyhisab.ui.screens.DebtsScreen
import com.asif.familyhisab.ui.screens.HomeScreen
import com.asif.familyhisab.ui.screens.MemberScreen
import com.asif.familyhisab.ui.screens.MoreScreen
import com.asif.familyhisab.ui.screens.ReportsScreen
import com.asif.familyhisab.ui.screens.TxnsScreen
import com.asif.familyhisab.ui.theme.HisabTheme
import com.asif.familyhisab.util.Fmt
import com.asif.familyhisab.util.LocalFmt
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Renders the main screens with sample data so the design can be checked without a phone. */
class ScreensShot {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(softButtons = false, screenHeight = 2900), maxPercentDifference = 1.0)

    companion object {
        val month: YearMonth = YearMonth.now()

        fun sample(): AppData {
            val t = ArrayList<Txn>()
            var n = 0
            fun add(m: YearMonth, d: Int, amt: Int, cat: String, mem: String = FAMILY_ID, note: String = "", type: TxType = TxType.EXPENSE, items: List<BazarItem> = emptyList(), method: PayMethod = PayMethod.CASH) {
                if (d > m.lengthOfMonth() || (m == month && d > LocalDate.now().dayOfMonth)) return
                t += Txn(id = "t${n++}", type = type, amount = amt * 100L, categoryId = cat, memberId = mem, day = m.atDay(d).toEpochDay(), note = note, items = items, method = method, createdAt = n.toLong())
            }
            for (back in 0..5) {
                val m = month.minusMonths(back.toLong())
                val k = 1 + back % 3 * 0.1
                add(m, 1, 65000, "salary", "asif", type = TxType.INCOME, method = PayMethod.BANK)
                add(m, 1, 15000, "rent", note = "বাসা ভাড়া")
                add(m, 1, (2400 * k).toInt(), "bazar", note = "সাপ্তাহিক বাজার", items = listOf(
                    BazarItem("চাল", 5.0, "কেজি", 375_00), BazarItem("ডাল", 2.0, "কেজি", 260_00), BazarItem("তেল", 2.0, "লিটার", 380_00),
                ))
                add(m, 2, (1250 * k).toInt(), "fish", note = "রুই মাছ")
                add(m, 2, 850, "veg")
                add(m, 3, 1800, "education", "ibad", note = "স্কুলের বেতন", method = PayMethod.BKASH)
                add(m, 4, 1450, "medical", "abbu", note = "প্রেশারের ওষুধ")
                add(m, 5, 2200, "bill", note = "বিদ্যুৎ বিল", method = PayMethod.BKASH)
                add(m, 6, 950, "medical", "ammu")
                add(m, 8, (2600 * k).toInt(), "bazar", note = "বাজার")
                add(m, 9, 3200, "cloth", "irin", note = "শাড়ি")
                add(m, 10, 600, "net", "asif", method = PayMethod.NAGAD)
                add(m, 11, 1100, "baby", "ibad", note = "খেলনা")
                add(m, 12, 7000, "business", "asif", type = TxType.INCOME)
            }
            return AppData(
                txns = t,
                budget = Budget(monthlyTotal = 45000_00, perMember = mapOf("ibad" to 4000_00)),
                debts = listOf(
                    Debt(person = "করিম ভাই", dir = DebtDir.GAVE, amount = 5000_00, day = LocalDate.now().minusDays(9).toEpochDay(), phone = "017"),
                    Debt(person = "মুদি দোকান", dir = DebtDir.TOOK, amount = 1200_00, day = LocalDate.now().minusDays(3).toEpochDay()),
                ),
                shopping = listOf(ShopItem(name = "পেঁয়াজ", qty = "২ কেজি"), ShopItem(name = "ডিম", qty = "১ ডজন")),
                goals = listOf(Goal(name = "ঈদের কেনাকাটা", emoji = "🌙", target = 30000_00, saved = 12000_00)),
            )
        }
    }

    @Composable
    private fun Frame(dark: Boolean, content: @Composable (Nav, Store) -> Unit) {
        CompositionLocalProvider(LocalFmt provides Fmt(true)) {
            HisabTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                val nav = Nav(rememberNavController())
                androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    content(nav, Store.get(paparazzi.context))
                }
            }
        }
    }

    private val d = sample()

    @Test fun home() = paparazzi.snapshot { Frame(false) { nav, _ -> HomeScreen(d, month, {}, nav) } }
    @Test fun homeDark() = paparazzi.snapshot { Frame(true) { nav, _ -> HomeScreen(d, month, {}, nav) } }
    @Test fun add() = paparazzi.snapshot { Frame(false) { nav, s -> AddScreen(d, s, nav, null, null, FAMILY_ID, "bazar", 245000, false) } }
    @Test fun addDark() = paparazzi.snapshot { Frame(true) { nav, s -> AddScreen(d, s, nav, "t2", null, null, null, null, false) } }
    @Test fun txns() = paparazzi.snapshot { Frame(false) { nav, s -> TxnsScreen(d, month, {}, nav, s) } }
    @Test fun reports() = paparazzi.snapshot { Frame(false) { nav, _ -> ReportsScreen(d, month, {}, nav) } }
    @Test fun member() = paparazzi.snapshot { Frame(false) { nav, s -> MemberScreen(d, "ibad", month, {}, nav, s) } }
    @Test fun more() = paparazzi.snapshot { Frame(true) { nav, _ -> MoreScreen(d, nav) } }
    @Test fun debts() = paparazzi.snapshot { Frame(false) { nav, s -> DebtsScreen(d, s, nav) } }
}
