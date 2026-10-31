"""Generates activity_dashboard.xml (MAYA-style home: top bar, greeting, hero, chips, info cards, ask box,
shortcuts, bottom navigation with a centre mic button, and a side drawer). Run: python tools/gen_dashboard.py"""
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "layout", "activity_dashboard.xml")


def tile(icon, color, ind):
    return f"""{ind}<ImageView
{ind}    style="@style/Widget.Faiza.IconTile"
{ind}    android:background="@drawable/bg_tile_{color}"
{ind}    android:src="@drawable/{icon}"
{ind}    app:tint="@color/tile_{color}" />
"""


def section(title):
    return f"""
            <TextView
                style="@style/Text.Faiza.Section"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="{title}" />
"""


def tile_card(card_id, icon, color, title, sub, sub_id=None, side="start"):
    margin = 'android:layout_marginEnd="6dp"' if side == "start" else 'android:layout_marginStart="6dp"'
    sid = f'\n                        android:id="@+id/{sub_id}"' if sub_id else ""
    return f"""
                <com.google.android.material.card.MaterialCardView
                    android:id="@+id/{card_id}"
                    android:layout_width="0dp"
                    android:layout_height="match_parent"
                    {margin}
                    android:layout_weight="1"
                    android:clickable="true"
                    android:focusable="true">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="vertical"
                        android:padding="16dp">

{tile(icon, color, " " * 24)}
                        <TextView
                            style="@style/Widget.Faiza.RowTitle"
                            android:layout_marginTop="14dp"
                            android:text="{title}" />

                        <TextView{sid}
                            style="@style/Widget.Faiza.RowSubtitle"
                            android:layout_width="match_parent"
                            android:ellipsize="end"
                            android:maxLines="1"
                            android:text="{sub}"
                            android:textSize="12sp" />
                    </LinearLayout>
                </com.google.android.material.card.MaterialCardView>
"""


def tile_row(a, b, first=False):
    top = "" if first else '\n                android:layout_marginTop="12dp"'
    return f"""
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"{top}
                android:orientation="horizontal">
{tile_card(*a, side="start")}{tile_card(*b, side="end")}
            </LinearLayout>
"""


def chip(cid, icon, label, side):
    margin = {"start": 'android:layout_marginEnd="5dp"', "mid": 'android:layout_marginStart="5dp"\n                android:layout_marginEnd="5dp"', "end": 'android:layout_marginStart="5dp"'}[side]
    return f"""
                <LinearLayout
                    android:id="@+id/{cid}"
                    android:layout_width="0dp"
                    android:layout_height="46dp"
                    {margin}
                    android:layout_weight="1"
                    android:background="@drawable/bg_chip_button"
                    android:clickable="true"
                    android:focusable="true"
                    android:gravity="center"
                    android:orientation="horizontal">

                    <ImageView
                        android:layout_width="18dp"
                        android:layout_height="18dp"
                        android:importantForAccessibility="no"
                        android:src="@drawable/{icon}"
                        app:tint="@color/primary_light" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="8dp"
                        android:fontFamily="sans-serif-medium"
                        android:text="{label}"
                        android:textColor="@color/text_primary"
                        android:textSize="14sp" />
                </LinearLayout>
"""


def info_card(cid, icon, label, value_id, value, sub_id, sub, side):
    margin = {"start": 'android:layout_marginEnd="5dp"', "mid": 'android:layout_marginStart="5dp"\n                android:layout_marginEnd="5dp"', "end": 'android:layout_marginStart="5dp"'}[side]
    return f"""
                <com.google.android.material.card.MaterialCardView
                    android:id="@+id/{cid}"
                    android:layout_width="0dp"
                    android:layout_height="match_parent"
                    {margin}
                    android:layout_weight="1"
                    android:clickable="true"
                    android:focusable="true"
                    app:cardCornerRadius="18dp">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="vertical"
                        android:padding="12dp">

                        <LinearLayout
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:gravity="center_vertical"
                            android:orientation="horizontal">

                            <ImageView
                                android:layout_width="14dp"
                                android:layout_height="14dp"
                                android:importantForAccessibility="no"
                                android:src="@drawable/{icon}"
                                app:tint="@color/text_secondary" />

                            <TextView
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:layout_marginStart="6dp"
                                android:text="{label}"
                                android:textColor="@color/text_secondary"
                                android:textSize="12sp" />
                        </LinearLayout>

                        <TextView
                            android:id="@+id/{value_id}"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:layout_marginTop="6dp"
                            android:ellipsize="end"
                            android:fontFamily="sans-serif-medium"
                            android:maxLines="1"
                            android:text="{value}"
                            android:textColor="@color/text_primary"
                            android:textSize="20sp" />

                        <TextView
                            android:id="@+id/{sub_id}"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:ellipsize="end"
                            android:maxLines="1"
                            android:text="{sub}"
                            android:textColor="@color/text_secondary"
                            android:textSize="12sp" />
                    </LinearLayout>
                </com.google.android.material.card.MaterialCardView>
"""


def nav_item(nid, icon, label, selected=False):
    color = "primary_light" if selected else "text_muted"
    return f"""
            <LinearLayout
                android:id="@+id/{nid}"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:gravity="center"
                android:orientation="vertical">

                <ImageView
                    android:layout_width="22dp"
                    android:layout_height="22dp"
                    android:importantForAccessibility="no"
                    android:src="@drawable/{icon}"
                    app:tint="@color/{color}" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="3dp"
                    android:text="{label}"
                    android:textColor="@color/{color}"
                    android:textSize="11sp" />
            </LinearLayout>
"""


xml = f"""<?xml version="1.0" encoding="utf-8"?>
<!-- GENERATED by android/tools/gen_dashboard.py: edit the script, not this file. -->
<androidx.drawerlayout.widget.DrawerLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/drawer"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bg">

    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <ScrollView
            android:id="@+id/dashScroll"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:clipToPadding="false"
            android:fillViewport="true"
            android:paddingBottom="96dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:paddingStart="20dp"
                android:paddingTop="8dp"
                android:paddingEnd="20dp"
                android:paddingBottom="16dp">

            <!-- Top bar: menu · title · notifications · profile (MAYA) -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="56dp"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <ImageButton
                    android:id="@+id/btnMenu"
                    android:layout_width="44dp"
                    android:layout_height="44dp"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:contentDescription="Menu"
                    android:padding="10dp"
                    android:scaleType="fitCenter"
                    android:src="@drawable/ic_menu"
                    app:tint="@color/text_primary" />

                <TextView
                    android:id="@+id/tvAppTitle"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:fontFamily="sans-serif-medium"
                    android:gravity="center"
                    android:text="Faiza"
                    android:textColor="@color/text_primary"
                    android:textSize="18sp" />

                <ImageButton
                    android:id="@+id/btnQuiet"
                    android:layout_width="44dp"
                    android:layout_height="44dp"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:contentDescription="Quiet mode"
                    android:padding="11dp"
                    android:scaleType="fitCenter"
                    android:src="@drawable/ic_moon"
                    app:tint="@color/text_primary" />

                <FrameLayout
                    android:layout_width="44dp"
                    android:layout_height="44dp">

                    <ImageButton
                        android:id="@+id/btnBell"
                        android:layout_width="44dp"
                        android:layout_height="44dp"
                        android:background="?attr/selectableItemBackgroundBorderless"
                        android:contentDescription="Notifications"
                        android:padding="11dp"
                        android:scaleType="fitCenter"
                        android:src="@drawable/ic_bell"
                        app:tint="@color/text_primary" />

                    <View
                        android:id="@+id/bellDot"
                        android:layout_width="9dp"
                        android:layout_height="9dp"
                        android:layout_gravity="top|end"
                        android:layout_marginTop="10dp"
                        android:layout_marginEnd="11dp"
                        android:background="@drawable/bg_dot_orange"
                        android:visibility="gone" />
                </FrameLayout>

                <TextView
                    android:id="@+id/btnSettings"
                    android:layout_width="36dp"
                    android:layout_height="36dp"
                    android:layout_marginStart="6dp"
                    android:background="@drawable/bg_avatar"
                    android:contentDescription="Settings"
                    android:fontFamily="sans-serif-medium"
                    android:gravity="center"
                    android:text="A"
                    android:textColor="#FFFFFF"
                    android:textSize="15sp" />
            </LinearLayout>

            <!-- Greeting -->
            <TextView
                android:id="@+id/tvGreeting"
                style="@style/Text.Faiza.Caption"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="10dp"
                android:text="Good evening,"
                android:textSize="15sp" />

            <TextView
                android:id="@+id/tvHello"
                style="@style/Text.Faiza.Title"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:ellipsize="end"
                android:maxLines="1"
                android:text="Asif"
                android:textSize="30sp" />

            <LinearLayout
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="4dp"
                android:layout_marginBottom="16dp"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <ImageView
                    android:id="@+id/ivLiveStatus"
                    android:layout_width="14dp"
                    android:layout_height="14dp"
                    android:importantForAccessibility="no"
                    android:src="@drawable/ic_moon"
                    app:tint="@color/text_secondary" />

                <TextView
                    android:id="@+id/tvLiveStatus"
                    style="@style/Text.Faiza.Caption"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="6dp"
                    android:text="Faiza is asleep — say “Hey Faiza”" />
            </LinearLayout>

            <!-- Hero -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:background="@drawable/bg_hero"
                android:gravity="center_horizontal"
                android:orientation="vertical"
                android:paddingStart="24dp"
                android:paddingTop="20dp"
                android:paddingEnd="24dp"
                android:paddingBottom="24dp">

                <TextView
                    android:id="@+id/tvPlanStatus"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:background="@drawable/bg_pill_success"
                    android:fontFamily="sans-serif-medium"
                    android:paddingStart="10dp"
                    android:paddingTop="3dp"
                    android:paddingEnd="10dp"
                    android:paddingBottom="3dp"
                    android:text="Pro plan · Active"
                    android:textColor="@color/success"
                    android:textSize="11sp" />

                <com.faiza.ai.ui.orb.OrbView
                    android:id="@+id/orbHero"
                    android:layout_width="150dp"
                    android:layout_height="150dp"
                    android:layout_marginTop="12dp" />

                <TextView
                    android:id="@+id/tvAiStatus"
                    style="@style/Text.Faiza.Heading"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="12dp"
                    android:gravity="center"
                    android:text="Ready when you are" />

                <TextView
                    android:id="@+id/tvPersona"
                    style="@style/Text.Faiza.Caption"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:gravity="center"
                    android:text="Faiza · Friendly &amp; helpful" />

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btnToggleAI"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="22dp"
                    android:text="Start Faiza"
                    app:icon="@drawable/ic_play" />
            </LinearLayout>

            <!-- Quick chips (MAYA: Music · Study · Journal) -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="14dp"
                android:orientation="horizontal">
{chip("chipMusic", "ic_music", "Music", "start")}{chip("chipStudy", "ic_book", "Study", "mid")}{chip("chipJournal", "ic_edit", "Journal", "end")}
            </LinearLayout>

            <!-- Info cards (MAYA: Weather · Today · Mood) -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:orientation="horizontal">
{info_card("cardWeather", "ic_sun", "Weather", "tvWeather", "29°", "tvWeatherSub", "Partly cloudy", "start")}{info_card("cardToday", "ic_today", "Today", "tvToday", "7", "tvTodaySub", "Fri, Aug", "mid")}{info_card("cardMood", "ic_heart", "Mood", "tvMood", "Warm", "tvMoodSub", "All good", "end")}
            </LinearLayout>

            <!-- Ask box: she answers out loud -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="14dp"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <EditText
                    android:id="@+id/etAsk"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:background="@drawable/bg_composer"
                    android:hint="Ask Faiza anything…"
                    android:imeOptions="actionSend"
                    android:importantForAutofill="no"
                    android:inputType="textCapSentences"
                    android:minHeight="52dp"
                    android:paddingStart="20dp"
                    android:paddingEnd="20dp"
                    android:textColor="@color/text_primary"
                    android:textColorHint="@color/text_muted"
                    android:textSize="15sp" />

                <ImageButton
                    android:id="@+id/btnAsk"
                    android:layout_width="52dp"
                    android:layout_height="52dp"
                    android:layout_marginStart="8dp"
                    android:background="@drawable/bg_send_button"
                    android:contentDescription="Send to Faiza"
                    android:padding="15dp"
                    android:scaleType="fitCenter"
                    android:src="@drawable/ic_send"
                    app:tint="#FFFFFF" />
            </LinearLayout>

            <com.google.android.material.card.MaterialCardView
                android:id="@+id/cardUpdate"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="14dp"
                android:clickable="true"
                android:focusable="true"
                android:visibility="gone"
                app:strokeColor="@color/success">

                <LinearLayout style="@style/Widget.Faiza.Row">

{tile("ic_restore", "green", " " * 20)}
                    <LinearLayout style="@style/Widget.Faiza.RowText">

                        <TextView
                            android:id="@+id/tvUpdateTitle"
                            style="@style/Widget.Faiza.RowTitle"
                            android:text="Update available · 3.1.0" />

                        <TextView
                            style="@style/Widget.Faiza.RowSubtitle"
                            android:text="Tap to see what's new and download" />
                    </LinearLayout>

                    <ImageView style="@style/Widget.Faiza.Chevron" />
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>
{section("Shortcuts")}{tile_row(("cardRoutines", "ic_timer", "cyan", "Routines", "Reminders and alerts", "tvRoutineCount"),
          ("cardMacros", "ic_bolt", "orange", "Macros", "Record and replay tasks", "tvMacroCount"), first=True)}{tile_row(("cardPersonas", "ic_sparkle", "pink", "Personas", "Faiza, Friday, Venom"),
          ("cardSkills", "ic_star", "amber", "Skills", "What she can do"))}{tile_row(("cardSOS", "ic_shield", "red", "SOS", "Emergency alerts"),
          ("cardOrb", "ic_palette", "violet", "Floating orb", "Design and colors"))}{tile_row(("cardMemory", "ic_memory", "slate", "Memory", "Nothing yet", "tvMemoryCount"),
          ("cardProfile", "ic_person", "green", "Profile", "Name, contacts, music"))}{section("AI engine")}
            <com.google.android.material.card.MaterialCardView
                android:id="@+id/cardApiSetup"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:clickable="true"
                android:focusable="true">

                <LinearLayout style="@style/Widget.Faiza.Row">

{tile("ic_key", "amber", " " * 20)}
                    <LinearLayout style="@style/Widget.Faiza.RowText">

                        <TextView
                            style="@style/Widget.Faiza.RowTitle"
                            android:text="AI connection" />

                        <TextView
                            android:id="@+id/tvApiStatus"
                            style="@style/Widget.Faiza.RowSubtitle"
                            android:text="Add your free Gemini key"
                            android:textColor="@color/warning" />
                    </LinearLayout>

                    <ImageView style="@style/Widget.Faiza.Chevron" />
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>
            </LinearLayout>
        </ScrollView>

        <!-- Bottom navigation (MAYA: Home · Scan · mic · Memories · Chat) -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="68dp"
            android:layout_gravity="bottom"
            android:background="@drawable/bg_bottom_bar"
            android:orientation="horizontal"
            android:paddingStart="6dp"
            android:paddingEnd="6dp">
{nav_item("navHome", "ic_home", "Home", True)}{nav_item("navScan", "ic_camera", "Scan")}
            <Space
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1" />
{nav_item("navMemories", "ic_memory", "Memories")}{nav_item("navChat", "ic_chat", "Chat")}
        </LinearLayout>

        <ImageButton
            android:id="@+id/btnMic"
            android:layout_width="64dp"
            android:layout_height="64dp"
            android:layout_gravity="bottom|center_horizontal"
            android:layout_marginBottom="30dp"
            android:background="@drawable/bg_mic_fab"
            android:contentDescription="Talk to Faiza"
            android:elevation="8dp"
            android:padding="18dp"
            android:scaleType="fitCenter"
            android:src="@drawable/ic_mic"
            app:tint="#FFFFFF" />
    </FrameLayout>

    <!-- Side menu (MAYA ☰) -->
    <LinearLayout
        android:id="@+id/drawerPanel"
        android:layout_width="300dp"
        android:layout_height="match_parent"
        android:layout_gravity="start"
        android:background="@color/surface"
        android:clickable="true"
        android:focusable="true"
        android:orientation="vertical">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:background="@drawable/bg_hero"
            android:orientation="vertical"
            android:padding="20dp"
            android:paddingTop="36dp">

            <TextView
                android:id="@+id/drawerAvatar"
                android:layout_width="56dp"
                android:layout_height="56dp"
                android:background="@drawable/bg_avatar"
                android:fontFamily="sans-serif-medium"
                android:gravity="center"
                android:text="A"
                android:textColor="#FFFFFF"
                android:textSize="22sp" />

            <TextView
                android:id="@+id/drawerName"
                style="@style/Text.Faiza.Heading"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:text="Asif" />

            <TextView
                android:id="@+id/drawerPlan"
                style="@style/Text.Faiza.Caption"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Faiza AI · Pro" />
        </LinearLayout>

        <ScrollView
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1">

            <LinearLayout
                android:id="@+id/drawerMenu"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:paddingTop="8dp"
                android:paddingBottom="8dp" />
        </ScrollView>
    </LinearLayout>
</androidx.drawerlayout.widget.DrawerLayout>
"""

open(OUT, "w", encoding="utf-8").write(xml)
print("dashboard written")
