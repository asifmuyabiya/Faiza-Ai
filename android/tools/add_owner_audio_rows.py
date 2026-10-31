from settings_rows import row, apply
apply("rowLouderVoice", [
    row("callSpeaker", "ic_phone", "green", "Talk on the call speaker",
        "Her voice is separate from videos and music, like a WhatsApp call", "switch"),
    row("softVoice", "ic_mic", "blue", "Hear soft voices", "Soft voices", "nav"),
])
apply("rowVoiceGuardian", [
    row("ownerLock", "ic_lock", "red", "Owner lock (secret code)",
        "Strangers must say your code · photo + lock", "nav"),
])
