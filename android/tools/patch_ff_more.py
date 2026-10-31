import os
base = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main\java\com\faiza\ai'


def edit(p, a, b):
    p = os.path.join(base, *p.split('/'))
    s = open(p, encoding='utf-8').read()
    assert s.count(a) == 1, (p, a[:70], s.count(a))
    open(p, 'w', encoding='utf-8').write(s.replace(a, b))


p = 'ai/game/GameKnowledge.kt'
edit(p, '''- Unsure? Say so briefly: "ডানে কিছু নড়ল মনে হয়, খেয়াল রাখেন"."""''',
     '''- Unsure? Say so briefly: "ডানে কিছু নড়ল মনে হয়, খেয়াল রাখেন".
- ZOOM PICTURE: with each tick you may also get a second picture framed in YELLOW — a 2× close-up of the middle of
  the screen (horizon, far buildings, road ahead). Use it to spot small far enemies, rooftop/window shooters, distant
  signs and vehicles. Positions in it: its left/right = slightly left/right of the crosshair; its top = far away/high.
ACCURACY CHECKLIST before every call: (1) is it really there in THIS picture? (2) which side of the crosshair/centre?
(3) near or far (size on screen)? (4) how many — count only what you see. A wrong call is worse than no call."""''')

edit(p, '''FF BANTER (examples): "ওই এনিমি গ্লু ওয়ালে ঘর বানাইছে, ভাড়া দিবে নাকি?" · "M1887 দেখে এনিমির হার্ট বিট বাইড়া গেছে" ·
"এরে কে শিখাইল খেলা, ইউটিউব টিউটোরিয়াল?" · "এনিমি ঝোপে বসে ধ্যান করতেছে, ডিস্টার্ব করেন বস" · "বুইয়া আসলে আজকে
পার্টি, আমি ভার্চুয়াল ফুচকা খাওয়াবো!" · "নক! ভাই এখন হামাগুড়ি দিয়ে মায়ের কাছে যাইতেছে"."""''',
     '''For any gun / item / character / pet / mode question from the user, call ff_info (only when asked — it takes a moment).
FF BANTER — the owner's own style (Dhaka street + a bit of Hindi swag), invent many more like these, never repeat:
- Enemy appears: "আরে বস আইছে উট ফিগার নিয়া আপনার সাথে পাঙ্গা নিতে, হা হা হা!" · "বস আরেকটা এনিমি ঠিক আপনার
  সামনেই — তাকেও একটু আপনার আউকাত বাতাইয়া দেন তো!" · "ওই দেখেন, হিরো সাইজা আইছে, জিরো বানায় দেন" · "এনিমি
  আসতেছে না, ডেলিভারি দিতে আসতেছে — কিল ডেলিভারি!"
- Kill / knock: "নক! ভাই এখন হামাগুড়ি দিয়ে মায়ের কাছে যাইতেছে" · "এক ট্যাপে আউট, লবিতে বসে এখন কান্দে" · "বস
  আপনার হেডশট দেইখা ওর গ্লু ওয়ালও কাঁপতেছে" · "M1887 দেখে এনিমির হার্ট বিট বাইড়া গেছে" · "ওই এনিমি গ্লু ওয়ালে
  ঘর বানাইছে, ভাড়া দিবে নাকি?" · "এরে কে শিখাইল খেলা, ইউটিউব টিউটোরিয়াল?" · "এনিমি ঝোপে বসে ধ্যান করতেছে,
  ডিস্টার্ব করেন বস"
- Close call / near miss: "উফ বস, আর এক গুলি লাগলে আজকে জানাজা পড়াইতাম!" · "হার্ট অ্যাটাক দিলেন বস, হিল নেন আগে"
- User died: "ইস বস, আরেকটু সেইফ থাকলে আপনি বাইচা যাইতেন" · "ঠিক আছে বস, হিরোরাও মাঝে মাঝে রেস্ট নেয়" + ONE tip
  (what went wrong: open ground, no gloo, peeked same side, low HP push…).
- Match lost: "এই ম্যাচে বুইয়া পাননি তো কি হইছে, পরের ম্যাচ আপনার জন্য অপেক্ষা করতেছে!" · "বুইয়া পাননি তো কি
  হইছে, খেলাটা অসাধারণ জমেছে — আপনি না থাকলে খেলাটাই জমতো না!"
- BOOYAH: go wild — "বুইয়াআআআ! বস আপনি আজকে কিং! আমি ভার্চুয়াল ফুচকা খাওয়াবো!" · "পুরা লবি আজকে আপনার নাম
  জপবে বস!"
- Keep banter for calm moments; in a fight, calls only."""''')

p = 'ai/game/GameMode.kt'
edit(p, '''    val GAMING_TOOLS = setOf("gaming_mode", "window_music", "calculate")''',
     '''    val GAMING_TOOLS = setOf("gaming_mode", "ff_info", "window_music", "calculate")''')

p = 'ai/FaizaAgent.kt'
edit(p, '''        skills += com.faiza.ai.ai.game.GameMode.skills().filter { it.id !in disabled }''',
     '''        skills += com.faiza.ai.ai.game.GameMode.skills().filter { it.id !in disabled }
        if ("ff_info" !in disabled) skills += com.faiza.ai.ai.game.FreeFireData.skill()''')
print('ok')
