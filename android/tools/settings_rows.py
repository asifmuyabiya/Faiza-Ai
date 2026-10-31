"""Helpers to add rows to the hand-edited Settings XML in the same style as the original generator."""
import re

LAYOUT = r"C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main\res\layout\activity_settings.xml"


def row(key, icon, color, title, sub, kind="nav", ind=16):
    i = " " * ind
    k = key[0].upper() + key[1:]
    end = (f"""
{i}    <com.google.android.material.switchmaterial.SwitchMaterial
{i}        android:id="@+id/sw{k}"
{i}        android:layout_width="wrap_content"
{i}        android:layout_height="wrap_content" />
""" if kind == "switch" else f"""
{i}    <ImageView style="@style/Widget.Faiza.Chevron" />
""")
    return f"""
{i}<View style="@style/Widget.Faiza.Divider" />

{i}<LinearLayout
{i}    android:id="@+id/row{k}"
{i}    style="@style/Widget.Faiza.Row">

{i}    <ImageView
{i}        style="@style/Widget.Faiza.IconTile"
{i}        android:background="@drawable/bg_tile_{color}"
{i}        android:src="@drawable/{icon}"
{i}        app:tint="@color/tile_{color}" />

{i}    <LinearLayout style="@style/Widget.Faiza.RowText">

{i}        <TextView
{i}            style="@style/Widget.Faiza.RowTitle"
{i}            android:text="{title}" />

{i}        <TextView
{i}            android:id="@+id/sub{k}"
{i}            style="@style/Widget.Faiza.RowSubtitle"
{i}            android:text="{sub}" />
{i}    </LinearLayout>
{end}{i}</LinearLayout>
"""


def insert_after_row(xml, row_id, new_rows):
    """Inserts [new_rows] right after the row LinearLayout with android:id=row_id."""
    start = xml.index(f'android:id="@+id/{row_id}"')
    # The row ends at the first "</LinearLayout>" at the row's own indentation (16 spaces).
    end = xml.index("\n                </LinearLayout>\n", start) + len("\n                </LinearLayout>\n")
    return xml[:end] + new_rows + xml[end:]


def apply(after_row, rows):
    s = open(LAYOUT, encoding="utf-8").read()
    s = insert_after_row(s, after_row, "".join(rows))
    open(LAYOUT, "w", encoding="utf-8").write(s)
