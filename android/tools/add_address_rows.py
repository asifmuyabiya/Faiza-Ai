from settings_rows import row, LAYOUT

DIV = '\n                <View style="@style/Widget.Faiza.Divider" />\n'

rows = [
    row("yourName", "ic_person", "blue", "Your name", "What she knows you as", "nav"),
    row("gender", "ic_person", "violet", "You are", "Male or female", "nav"),
    row("address", "ic_heart", "pink", "She calls you", "Boss, Jaan, Jaanu, Sir…", "nav"),
]
# row() starts with a divider; the first row of a card must not.
first = rows[0]
first = first[first.index('<LinearLayout'):]
block = '\n                ' + first + ''.join(rows[1:]) + DIV + '\n'

s = open(LAYOUT, encoding='utf-8').read()
anchor = '                <LinearLayout\n                    android:id="@+id/rowContacts"'
assert s.count(anchor) == 1
i = s.index(anchor)
s = s[:i] + block.lstrip('\n') + s[i:]
open(LAYOUT, 'w', encoding='utf-8').write(s)
print("ok")
