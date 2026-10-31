from settings_rows import row, apply
# Touch Guard right after Owner lock (Safety section)
apply("rowOwnerLock", [
    row("touchGuard", "ic_shield", "red", "Touch Guard",
        "Watch the phone while you're away · photo + siren", "nav"),
])
# Event triggers after Calls (Announcements section)
apply("rowCalls", [
    row("eventTriggers", "ic_campaign", "orange", "Event triggers",
        "What she announces on her own", "nav"),
])
