p = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main\java\com\faiza\ai\ai\Persona.kt'
s = open(p, encoding='utf-8').read()


def rep(a, b, cnt=1):
    global s
    assert s.count(a) == cnt, (a[:70], s.count(a))
    s = s.replace(a, b)


rep('''val MALE_VOICES = listOf("Charon", "Fenrir", "Puck", "Orus", "Enceladus", "Iapetus", "Umbriel", "Algieba", "Algenib", "Rasalgethi", "Alnilam", "Schedar", "Achird", "Zubenelgenubi", "Sadachbia", "Sadaltager")''',
    '''val MALE_VOICES = listOf("Algenib", "Charon", "Fenrir", "Puck", "Orus", "Enceladus", "Iapetus", "Umbriel", "Algieba", "Rasalgethi", "Alnilam", "Schedar", "Achird", "Zubenelgenubi", "Sadachbia", "Sadaltager", "Achernar")''')
rep('''        liveVoice = "Charon",
        liveVoiceChoices = MALE_VOICES
    );''', '''        liveVoice = "Charon",
        liveVoiceChoices = MALE_VOICES
    ),
    JARVIS(
        "jarvis", "Jarvis", "🎩", "Calm British butler AI",
        pitch = 0.9f, speechRate = 1.0f, isPro = true,
        style = """
            PERSONA: Jarvis
            - Calm, polished, witty AI butler (like Tony Stark's J.A.R.V.I.S.)
            - Dry British humour, unflappable, quietly brilliant; calls the user "sir" (or "ma'am")
            - Phrases like "As you wish, sir.", "Right away.", "Might I suggest…", "Done, and rather elegantly."
        """.trimIndent(),
        liveVoice = "Sadachbia",
        liveVoiceChoices = MALE_VOICES
    );''')
rep('''            Persona.VENOM -> "WE are awake, host."
        }''', '''            Persona.VENOM -> "WE are awake, host."
            Persona.JARVIS -> "Good to see you, sir. $name at your service."
        }''')
rep('''            Persona.VENOM -> "আমরা জেগে উঠেছি।"
        }''', '''            Persona.VENOM -> "আমরা জেগে উঠেছি।"
            Persona.JARVIS -> "স্বাগতম স্যার। $name আপনার সেবায় হাজির।"
        }''')
rep('''            Persona.VENOM -> "हम जाग गए हैं।"
        }''', '''            Persona.VENOM -> "हम जाग गए हैं।"
            Persona.JARVIS -> "स्वागत है सर। $name आपकी सेवा में।"
        }''')
rep('''            Persona.VENOM -> "WE are listening, host."
        }''', '''            Persona.VENOM -> "WE are listening, host."
            Persona.JARVIS -> "Yes, sir?"
        }''')
rep('''            Persona.VENOM -> "আমরা শুনছি।"
        }''', '''            Persona.VENOM -> "আমরা শুনছি।"
            Persona.JARVIS -> "জি স্যার, বলুন।"
        }''')
rep('''            Persona.VENOM -> "हम सुन रहे हैं।"
        }''', '''            Persona.VENOM -> "हम सुन रहे हैं।"
            Persona.JARVIS -> "जी सर, बोलिए।"
        }''')
open(p, 'w', encoding='utf-8').write(s)
print("ok")
