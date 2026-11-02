package com.asif.familyhisab.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.asif.familyhisab.data.AppData
import com.asif.familyhisab.data.Store
import com.asif.familyhisab.ui.screens.AddScreen
import com.asif.familyhisab.ui.screens.BackupScreen
import com.asif.familyhisab.ui.screens.BudgetScreen
import com.asif.familyhisab.ui.screens.CategoriesScreen
import com.asif.familyhisab.ui.screens.DebtsScreen
import com.asif.familyhisab.ui.screens.GoalsScreen
import com.asif.familyhisab.ui.screens.HomeScreen
import com.asif.familyhisab.ui.screens.MemberScreen
import com.asif.familyhisab.ui.screens.MembersScreen
import com.asif.familyhisab.ui.screens.MoreScreen
import com.asif.familyhisab.ui.screens.ReportsScreen
import com.asif.familyhisab.ui.screens.SettingsScreen
import com.asif.familyhisab.ui.screens.ShoppingScreen
import com.asif.familyhisab.ui.screens.TxnsScreen
import com.asif.familyhisab.ui.theme.HeroColors
import java.time.YearMonth

/** Simple route builder for the add/edit screen. */
object Routes {
    fun add(id: String? = null, type: String? = null, member: String? = null, cat: String? = null, amt: Long? = null, items: Boolean = false): String {
        val q = buildList {
            id?.let { add("id=$it") }
            type?.let { add("type=$it") }
            member?.let { add("member=$it") }
            cat?.let { add("cat=$it") }
            amt?.let { add("amt=$it") }
            if (items) add("items=1")
        }
        return "add" + if (q.isEmpty()) "" else "?" + q.joinToString("&")
    }
}

class Nav(val c: NavHostController) {
    fun go(route: String) = c.navigate(route) { launchSingleTop = true }
    fun back() { c.popBackStack() }
    fun tab(route: String) = c.navigate(route) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "হোম", Icons.Rounded.Home),
    Tab("txns", "লেনদেন", Icons.Rounded.ReceiptLong),
    Tab("reports", "রিপোর্ট", Icons.Rounded.PieChart),
    Tab("more", "আরও", Icons.Rounded.Apps),
)

@Composable
fun App(store: Store, d: AppData) {
    val controller = rememberNavController()
    val nav = remember(controller) { Nav(controller) }
    var monthStr by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthStr)
    val setMonth: (YearMonth) -> Unit = { monthStr = it.toString() }
    val entry by controller.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val showBar = current in tabs.map { it.route }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Box(Modifier.fillMaxSize()) {
        NavHost(
            controller, startDestination = "home",
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            enterTransition = { fadeIn() + slideInHorizontally { it / 12 } },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() + slideOutHorizontally { it / 12 } },
        ) {
            composable("home") { HomeScreen(d, month, setMonth, nav) }
            composable("txns") { TxnsScreen(d, month, setMonth, nav, store) }
            composable("reports") { ReportsScreen(d, month, setMonth, nav) }
            composable("more") { MoreScreen(d, nav) }
            composable(
                "add?id={id}&type={type}&member={member}&cat={cat}&amt={amt}&items={items}",
                arguments = listOf("id", "type", "member", "cat", "amt", "items").map {
                    navArgument(it) { type = NavType.StringType; nullable = true; defaultValue = null }
                },
                enterTransition = { slideInVertically { it / 3 } + fadeIn() },
                popExitTransition = { slideOutVertically { it / 3 } + fadeOut() },
            ) { e ->
                val a = e.arguments
                AddScreen(
                    d, store, nav,
                    editId = a?.getString("id"),
                    initType = a?.getString("type"),
                    initMember = a?.getString("member"),
                    initCat = a?.getString("cat"),
                    initAmt = a?.getString("amt")?.toLongOrNull(),
                    fromShopping = a?.getString("items") == "1",
                )
            }
            composable("member/{id}") { e -> MemberScreen(d, e.arguments?.getString("id") ?: "", month, setMonth, nav, store) }
            composable("members") { MembersScreen(d, nav, store) }
            composable("budget") { BudgetScreen(d, store, nav) }
            composable("debts") { DebtsScreen(d, store, nav) }
            composable("shopping") { ShoppingScreen(d, store, nav) }
            composable("goals") { GoalsScreen(d, store, nav) }
            composable("categories") { CategoriesScreen(d, store, nav) }
            composable("backup") { BackupScreen(d, store, nav) }
            composable("settings") { SettingsScreen(d, store, nav) }
        }

        AnimatedVisibility(
            showBar, modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
        ) {
            BottomBar(current, onTab = { nav.tab(it) }, onAdd = { nav.go(Routes.add()) })
        }
    } }
}

@Composable
private fun BottomBar(current: String?, onTab: (String) -> Unit, onAdd: () -> Unit) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Surface(
            Modifier.fillMaxWidth().height(68.dp).shadow(18.dp, RoundedCornerShape(26.dp), ambientColor = HeroColors[0], spotColor = HeroColors[0]),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                tabs.take(2).forEach { TabItem(it, it.route == current) { onTab(it.route) } }
                Box(Modifier.size(64.dp))
                tabs.drop(2).forEach { TabItem(it, it.route == current) { onTab(it.route) } }
            }
        }
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = (-16).dp).size(64.dp)
                .shadow(14.dp, CircleShape, spotColor = HeroColors[1])
                .clip(CircleShape).background(Brush.linearGradient(HeroColors)).clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Add, "নতুন এন্ট্রি", tint = Color.White, modifier = Modifier.size(34.dp)) }
    }
}

@Composable
private fun TabItem(t: Tab, selected: Boolean, onClick: () -> Unit) {
    val col = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 3.dp),
        ) { Icon(t.icon, t.label, tint = col, modifier = Modifier.size(22.dp)) }
        Text(t.label, style = MaterialTheme.typography.labelSmall, color = col)
    }
}

@Suppress("unused")
private fun enc(s: String) = Uri.encode(s)
