"""Icons and shapes for the MAYA-style home (bottom bar, mic button, chips, info cards)."""
import os

D = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "drawable")

ICONS = {
    "ic_menu": "M3,18h18v-2H3v2zM3,13h18v-2H3v2zM3,6v2h18V6H3z",
    "ic_home": "M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z",
    "ic_moon": "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9 9,-4.03 9,-9c0,-0.46 -0.04,-0.92 -0.1,-1.36 -0.98,1.37 -2.58,2.26 -4.4,2.26 -2.98,0 -5.4,-2.42 -5.4,-5.4 0,-1.81 0.89,-3.42 2.26,-4.4C12.92,3.04 12.46,3 12,3z",
    "ic_book": "M18,2H6c-1.1,0 -2,0.9 -2,2v16c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM6,4h5v8l-2.5,-1.5L6,12V4z",
    "ic_sun": "M6.76,4.84l-1.8,-1.79 -1.41,1.41 1.79,1.79 1.42,-1.41zM4,10.5H1v2h3v-2zM13,0.55h-2V3.5h2V0.55zM20.45,4.46l-1.41,-1.41 -1.79,1.79 1.41,1.41 1.79,-1.79zM17.24,18.16l1.79,1.8 1.41,-1.41 -1.8,-1.79 -1.4,1.4zM20,10.5v2h3v-2h-3zM12,5.5c-3.31,0 -6,2.69 -6,6s2.69,6 6,6 6,-2.69 6,-6 -2.69,-6 -6,-6zM11,22.45h2V19.5h-2v2.95zM3.55,18.54l1.41,1.41 1.79,-1.8 -1.41,-1.41 -1.79,1.8z",
    "ic_today": "M19,4h-1V2h-2v2H8V2H6v2H5c-1.11,0 -1.99,0.9 -1.99,2L3,20c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2zM19,20H5V9h14v11zM7,11h5v5H7z",
}
for name, path in ICONS.items():
    open(os.path.join(D, name + ".xml"), "w", encoding="utf-8").write(f"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="{path}" />
</vector>
""")

SHAPES = {
    "bg_bottom_bar": """<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="rectangle">
            <solid android:color="@color/stroke" />
            <corners android:topLeftRadius="24dp" android:topRightRadius="24dp" />
        </shape>
    </item>
    <item android:top="1dp">
        <shape android:shape="rectangle">
            <solid android:color="@color/surface" />
            <corners android:topLeftRadius="24dp" android:topRightRadius="24dp" />
        </shape>
    </item>
</layer-list>""",
    "bg_mic_fab": """<ripple xmlns:android="http://schemas.android.com/apk/res/android" android:color="#33FFFFFF">
    <item>
        <shape android:shape="oval">
            <gradient android:angle="315" android:startColor="#A78BFA" android:endColor="#6D28D9" />
            <stroke android:width="4dp" android:color="@color/bg" />
        </shape>
    </item>
</ripple>""",
    "bg_chip_button": """<ripple xmlns:android="http://schemas.android.com/apk/res/android" android:color="#22FFFFFF">
    <item>
        <shape android:shape="rectangle">
            <solid android:color="@color/surface" />
            <stroke android:width="1dp" android:color="@color/stroke" />
            <corners android:radius="14dp" />
        </shape>
    </item>
</ripple>""",
    "bg_dot_orange": """<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <solid android:color="#FB923C" />
    <stroke android:width="1.5dp" android:color="@color/bg" />
</shape>""",
}
for name, body in SHAPES.items():
    open(os.path.join(D, name + ".xml"), "w", encoding="utf-8").write('<?xml version="1.0" encoding="utf-8"?>\n' + body + "\n")
print("design assets written")
