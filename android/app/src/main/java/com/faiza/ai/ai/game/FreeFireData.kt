package com.faiza.ai.ai.game

import com.faiza.ai.ai.provider.ParamType
import com.faiza.ai.ai.provider.ToolParam
import com.faiza.ai.ai.skills.Skill
import com.faiza.ai.ai.skills.require

/**
 * Faiza's Free Fire encyclopedia (owner: "সব গান, সব ইকুইপমেন্ট, সব ইনফরমেশন ফাইজার কাছে থাকুক"). Kept OUT of the live
 * prompt (that would slow every call) and looked up with the ff_info tool when the player asks. Roles and tactics, not
 * exact numbers — Garena changes stats every update.
 */
object FreeFireData {

    data class Entry(val name: String, val kind: String, val text: String, val aliases: List<String> = emptyList())

    private fun e(name: String, kind: String, text: String, vararg aliases: String) = Entry(name, kind, text, aliases.toList())

    val ENTRIES: List<Entry> = listOf(
        // ---------------- Assault rifles ----------------
        e("AK47", "AR", "High damage per bullet, strong recoil. Best mid range with tap/burst fire; spray only close. Good with muzzle + stock.", "ak", "ak-47"),
        e("M4A1", "AR", "Easy recoil, accurate, all-rounder for mid range; great beginner AR. Damage lower than AK/SCAR.", "m4"),
        e("SCAR", "AR", "Very stable, good damage, long-ish range; one of the safest ARs. Excellent with 2x/4x scope.", "scar l"),
        e("Groza", "AR", "Top AR (often from airdrops): high damage AND fire rate, armour-piercing feel. Take it when safe.", "groza"),
        e("AUG", "AR", "Good damage and accuracy at range; comes scoped in some modes. Strong mid-long range.", "aug"),
        e("FAMAS", "AR", "Burst-fire AR: 3-round bursts, deadly if bursts land on the head; weak if you miss.", "famas"),
        e("XM8", "AR", "Fast, light AR with a built-in scope in many versions; good mobility mid range.", "xm8"),
        e("Parafal", "AR", "High damage AR, decent at range, slower fire rate — tap at the head.", "parafal"),
        e("AN94", "AR", "Accurate AR, good for mid-long range spray control.", "an94", "an-94"),
        e("M14", "AR/marksman", "Semi-auto heavy-hitting rifle; precise single shots, good vs armour at range.", "m14"),
        e("G36", "AR", "Balanced AR, often scoped; reliable mid range.", "g36"),
        e("Kingfisher", "AR", "Fast-firing AR with good handling; close-mid range spray.", "kingfisher"),
        e("Plasma", "Special", "Energy gun: charges shots, no reload in the usual way; strong close-mid. Overheats if spammed.", "plasma gun"),
        // ---------------- SMGs ----------------
        e("MP40", "SMG", "Insane fire rate, melts close range (one of the fastest TTKs). Small mag — reload after every fight.", "mp 40"),
        e("UMP", "SMG", "Armour-piercing, stable SMG; good vs Lv3/Lv4 armour. Great pair with a sniper or AR.", "ump45"),
        e("MP5", "SMG", "Accurate SMG, good for mid-close and moving fights.", "mp-5"),
        e("Thompson", "SMG", "Big magazine, steady spray; good for holding a house.", "thomson", "tommy"),
        e("Vector", "SMG", "Very fast fire, short range; deadly in houses, low range.", "vector"),
        e("Bizon", "SMG", "Large magazine SMG; keep spraying through gloo walls and pushes.", "bizon"),
        e("MAC10", "SMG", "Extremely fast close-range SMG; weak at distance.", "mac 10", "mac-10"),
        e("P90", "SMG", "Huge magazine and good movement speed; great for rushes.", "p 90"),
        e("Mini Uzi", "SMG", "Tiny fast SMG/pistol-class, close range only.", "uzi"),
        e("VSS", "Marksman SMG", "Silenced scoped rifle-SMG: quiet mid-range damage, good for surprise.", "vss"),
        // ---------------- Shotguns ----------------
        e("M1887", "Shotgun", "Double-barrel lever shotgun: 2 quick big shots, deadly up close (Clash Squad favourite). Reload is slow — gloo first.", "m 1887", "double barrel"),
        e("M1014", "Shotgun", "Semi-auto shotgun, high damage close; good for house fights.", "m1014"),
        e("SPAS12", "Shotgun", "Pump shotgun with massive one-shot damage at very close range.", "spas", "spas-12"),
        e("MAG-7", "Shotgun", "Box-mag shotgun: more shells, fast follow-ups close range.", "mag7", "mag 7"),
        e("M590", "Shotgun", "Pump shotgun, strong close range, good spread.", "m590"),
        e("Charge Buster", "Shotgun", "Shotgun that can charge a stronger shot; great vs gloo walls close.", "charge buster"),
        e("Trogon", "Shotgun/AR", "Hybrid gun with two fire modes (shotgun + rifle style) in some versions; flexible.", "trogon"),
        // ---------------- Snipers & marksman ----------------
        e("AWM", "Sniper", "Strongest sniper: heavy one-shot damage, headshot kills even with a helmet usually. Slow bolt — hide after each shot.", "awm"),
        e("M82B", "Sniper", "Anti-material sniper: shots go THROUGH gloo walls — perfect vs campers behind walls.", "m82", "barrett"),
        e("Kar98k", "Sniper", "Classic bolt sniper, accurate and strong; headshots win.", "kar98", "kar 98", "kar"),
        e("M24", "Sniper", "Bolt-action sniper, good damage, easier to handle than AWM.", "m24"),
        e("SVD", "Marksman", "Semi-auto marksman rifle: multiple quick long-range shots.", "dragunov"),
        e("Woodpecker", "Marksman", "Armour-piercing marksman rifle; great vs vests, strong pair with a shotgun (M1887 + Woodpecker).", "woodpecker", "wood pecker"),
        e("SKS", "Marksman", "Semi-auto marksman, mid-long range taps.", "sks"),
        e("AC80", "Marksman", "Marksman rifle, good mid-long precision.", "ac80", "ac 80"),
        e("Treatment Sniper", "Support", "Shoots healing at teammates from range (and damages enemies).", "treatment sniper"),
        // ---------------- LMG ----------------
        e("M60", "LMG", "Big mag, steady damage; hold angles, slower movement.", "m60"),
        e("M249", "LMG", "Huge magazine, strong suppression in final circles.", "m249"),
        e("Kord", "LMG", "Very high damage LMG, heavy; deadly when set up.", "kord"),
        e("Gatling", "LMG", "Spins up then shreds; very slow to move — use only when holding a spot.", "gatling"),
        // ---------------- Pistols ----------------
        e("Desert Eagle", "Pistol", "Hard-hitting pistol; headshots are deadly. Best backup.", "deagle", "desert eagle"),
        e("G18", "Pistol", "Fast-firing pistol; fine early-game backup.", "g18"),
        e("USP", "Pistol", "Basic pistol, accurate; early game only.", "usp"),
        e("M500", "Pistol", "Revolver-style hand cannon with a scope; big damage per shot.", "m500"),
        e("M1873", "Pistol", "Revolver/lever pistol with strong single shots.", "m1873"),
        // ---------------- Launchers & special ----------------
        e("M79", "Launcher", "Grenade launcher: area damage, breaks gloo walls, good vs campers in houses.", "m79", "grenade launcher"),
        e("RGS50", "Launcher", "Grenade launcher variant; explosive damage vs groups and walls.", "rgs50"),
        e("Crossbow", "Special", "Silent bolt weapon, strong single shots.", "crossbow"),
        // ---------------- Melee ----------------
        e("Melee (Pan, Machete, Katana, Bat, Scythe)", "Melee", "Only for the first seconds after landing or for fun; the Pan blocks bullets on your back in some versions.", "pan", "machete", "katana", "bat", "scythe", "melee"),
        // ---------------- Throwables ----------------
        e("Gloo Wall grenade", "Throwable", "THE key item: instant ice wall cover. Throw when shot in the open, to heal, to revive, to block a door, to cross open ground. Carry 3+ (5+ in late game).", "gloo", "gloo wall", "gloowall", "ice wall"),
        e("Frag grenade", "Throwable", "Explosive: flush campers out of houses/behind walls, finish knocked groups.", "grenade", "frag", "bomb"),
        e("Flashbang", "Throwable", "Blinds enemies — throw then rush immediately.", "flash", "flashbang", "stun"),
        e("Smoke grenade", "Throwable", "Blocks vision: cover a revive, a zone crossing or a retreat.", "smoke"),
        e("Decoy grenade", "Throwable", "Fakes your presence to confuse enemies (when available).", "decoy"),
        // ---------------- Healing & EP ----------------
        e("Medkit", "Heal", "Restores HP over a few seconds; you can't shoot while using — gloo first.", "med kit", "medkit", "first aid"),
        e("Treatment Gun", "Heal", "Heals yourself or teammates in bursts; great for squads.", "treatment gun"),
        e("Inhaler", "EP", "Gives EP (energy) that slowly turns into HP.", "inhaler"),
        e("Mushroom", "EP", "Gives EP; eat between fights.", "mushroom"),
        e("Bonfire", "Heal", "Placed item that heals/restores EP for players standing near it; use in safe spots.", "bonfire"),
        // ---------------- Armour & bags ----------------
        e("Vest", "Armour", "Lv1–Lv4 body armour: higher level = more damage reduction; durability drops when hit — swap to a fresher/higher one.", "armour", "armor", "vest"),
        e("Helmet", "Armour", "Lv1–Lv4: protects from headshots; Lv3/Lv4 helmets make one-tap headshots harder.", "helmet"),
        e("Backpack", "Gear", "Lv1–Lv3: more space for ammo, heals and gloo walls.", "bag", "backpack"),
        // ---------------- Attachments ----------------
        e("Attachments", "Attachment", "Muzzle/silencer (recoil, hides sound), foregrip (recoil), stock (stability), magazine (extended mag = fewer reloads), scopes 2x/4x/8x (range). Always take a better one you see.", "attachment", "scope", "muzzle", "silencer", "foregrip", "stock", "magazine", "mag"),
        // ---------------- Characters (abilities; numbers change by level/patch) ----------------
        e("Alok", "Character (active)", "Drop the Beat: aura that heals and boosts movement speed for you and nearby teammates.", "alok", "dj alok"),
        e("Chrono", "Character (active)", "Time Turner: force-field that blocks enemy damage for a few seconds, plus speed — instant cover in open fights.", "chrono"),
        e("K", "Character (active)", "Master of All: switches between EP-regen and EP-to-HP conversion modes; great sustain.", "k", "captain booyah"),
        e("Skyler", "Character (active)", "Riptide Rhythm: sends a sonic wave that breaks enemy Gloo Walls in front; strong for pushing.", "skyler"),
        e("Wukong", "Character (active)", "Camouflage: turns into a bush for a while — surprise or escape; breaks when you attack.", "wukong"),
        e("Dimitri", "Character (active)", "Healing Heartbeat: healing zone; knocked players inside can self-revive.", "dimitri"),
        e("Kelly", "Character (passive)", "Faster sprint speed — rushes and rotations.", "kelly"),
        e("Moco", "Character (passive)", "Hacker's Eye: tags enemies you shoot so the team can see their position briefly.", "moco"),
        e("Hayato", "Character (passive)", "Bushido: more armour penetration as your HP drops.", "hayato"),
        e("Jota", "Character (passive)", "Restores HP when you hit/knock with SMGs or shotguns (version-dependent).", "jota"),
        e("Jai", "Character (passive)", "Reloads part of the magazine automatically when you knock an enemy.", "jai"),
        e("Kenta", "Character (active)", "Projects a shield wall in front that reduces frontal damage.", "kenta"),
        e("Homer", "Character (active)", "Sends a drone that damages and slows an enemy.", "homer"),
        e("Tatsuya", "Character (active)", "Quick dash forward — dodge, rush or take cover fast.", "tatsuya"),
        e("Clu", "Character (active)", "Tracks the location of nearby enemies who aren't crouching/prone for a short time.", "clu"),
        e("Xayne", "Character (active)", "Extra temporary HP and bonus damage against Gloo Walls and shields — great rusher.", "xayne"),
        e("Andrew", "Character (passive)", "Armour loses durability more slowly.", "andrew"),
        e("Laura", "Character (passive)", "More accuracy while scoped.", "laura"),
        e("Ford", "Character (passive)", "Less damage while outside the safe zone.", "ford"),
        e("Olivia", "Character (passive)", "Revived teammates get extra HP.", "olivia"),
        e("Luqueta", "Character (passive)", "Max HP rises with each kill (up to a limit).", "luqueta"),
        e("Maxim", "Character (passive)", "Eats mushrooms and uses medkits faster.", "maxim"),
        e("Antonio", "Character (passive)", "Starts each round with extra HP shield.", "antonio"),
        e("Kla", "Character (passive)", "Much stronger fist damage.", "kla"),
        // ---------------- Pets ----------------
        e("Falco", "Pet", "Faster gliding/diving after jumping from the plane — land first.", "falco"),
        e("Ottero", "Pet", "Converts some EP into HP when using treatment items.", "ottero"),
        e("Mr. Waggor", "Pet", "Produces Gloo Wall grenades over time.", "waggor", "mr waggor"),
        e("Detective Panda", "Pet", "Restores HP when you kill an enemy.", "panda", "detective panda"),
        e("Robo", "Pet", "Gives a small shield when deploying a Gloo Wall.", "robo"),
        e("Rockie", "Pet", "Reduces your active skill cooldown.", "rockie"),
        // ---------------- Vehicles ----------------
        e("Vehicles", "Vehicle", "Jeep (sturdy), sports car (fast, fragile), monster truck (tough), motorbike (fastest, no cover), pickup, amphibious van (water). Fast rotation but loud — enemies spray vehicles; jump out behind cover.", "car", "jeep", "bike", "vehicle", "truck"),
        // ---------------- Modes, maps, ranks ----------------
        e("Battle Royale", "Mode", "50 players, drop → loot → zone shrinks → last alive wins BOOYAH. Rank points from placement + kills.", "br", "battle royale", "classic"),
        e("Clash Squad", "Mode", "4v4 rounds; buy phase with coins (armour → gun → gloo/heals), save coins, play rounds; first to the needed wins.", "cs", "clash squad"),
        e("Lone Wolf", "Mode", "1v1 / 2v2 duels with fixed loadouts; pure aim and gloo skills.", "lone wolf"),
        e("Maps", "Map", "Bermuda (classic, mixed towns/fields), Purgatory (rivers & bridges — avoid open bridges), Kalahari (desert, long range, use rocks), Alpine, NeXTerra (newer). Hot drops = crowded named towns.", "bermuda", "purgatory", "kalahari", "alpine", "nexterra", "map"),
        e("Ranks", "Rank", "Bronze → Silver → Gold → Platinum → Diamond → Heroic → Grandmaster (Master). Placement matters more than kills in BR rank.", "rank", "grandmaster", "heroic"),
        e("Airdrop", "Loot", "Supply crate with top guns (Groza, AWM…) and armour; draws many enemies — take it only after checking around.", "airdrop", "supply", "crate"),
        e("Vending machine", "Loot", "In some maps/modes: spend in-match tokens for items. Use when safe.", "vending", "vending machine"),
        // ---------------- Tactics glossary ----------------
        e("Gloo + peek", "Tactic", "Wall, then shoot from the wall's edge (left peek is often safer on mobile); never peek the same side twice in a row.", "peek", "gloo peek"),
        e("Rush", "Tactic", "Push when the enemy reloads, heals, or is knocked; flash/grenade first; shotgun/SMG; go together with your team.", "rush", "push"),
        e("Rotation", "Tactic", "Move to the next zone EARLY along the edge with cover; avoid the middle of open fields; vehicles for long distances.", "rotate", "rotation", "zone"),
        e("Third party", "Tactic", "After a loud fight, others come. Heal/reload and move or hide fast; also: attack two teams fighting each other.", "third party", "3rd party"),
        e("Headshot tips", "Tactic", "Crosshair at head height while moving, drag up slightly when shooting, use the right sensitivity, tap at range, spray only close.", "headshot", "one tap", "drag"),
        e("Final circle", "Tactic", "Hold cover inside the circle, count alive, let others fight first, keep 3+ gloo, crouch in grass, move with the edge.", "final circle", "last zone", "end game")
    )

    /** Best matches for a question ("MP40 vs UMP", "কোন ক্যারেক্টার ভালো", "gloo"). */
    fun lookup(query: String, max: Int = 6): String {
        val q = query.lowercase()
        val words = q.split(Regex("[^a-z0-9.\\-]+")).filter { it.length >= 2 }
        fun score(en: Entry): Int {
            val names = (en.aliases + en.name.lowercase())
            var s = 0
            if (names.any { q.contains(it) && it.length >= 2 }) s += 10
            words.forEach { w -> if (names.any { it == w }) s += 6; if (en.kind.lowercase().contains(w)) s += 3; if (en.text.lowercase().contains(w)) s += 1 }
            return s
        }
        // Category questions in Bangla/English.
        val kindHint = when {
            Regex("শটগান|shotgun").containsMatchIn(q) -> "Shotgun"
            Regex("স্নাইপার|sniper|marksman").containsMatchIn(q) -> "Sniper"
            Regex("এসএমজি|smg").containsMatchIn(q) -> "SMG"
            Regex("এআর|\\bar\\b|assault|rifle|রাইফেল").containsMatchIn(q) -> "AR"
            Regex("ক্যারেক্টার|character|চরিত্র").containsMatchIn(q) -> "Character"
            Regex("পেট|\\bpet").containsMatchIn(q) -> "Pet"
            Regex("পিস্তল|pistol").containsMatchIn(q) -> "Pistol"
            Regex("গ্রেনেড|throw").containsMatchIn(q) -> "Throwable"
            else -> null
        }
        val hits = ENTRIES.map { it to score(it) + if (kindHint != null && it.kind.contains(kindHint, true)) 5 else 0 }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(max).map { it.first }
        if (hits.isEmpty()) return "No exact entry. Answer from your own Free Fire knowledge, and say stats change with updates."
        return hits.joinToString("\n") { "• ${it.name} [${it.kind}]: ${it.text}" }
    }

    fun skill() = Skill(
        "ff_info", "🔥", "Free Fire guide", "\"MP40 না UMP?\" · \"কোন ক্যারেক্টার ভালো?\"",
        "Faiza's Free Fire encyclopedia: guns (AR, SMG, shotgun, sniper, LMG, pistol, launcher), throwables, heals, armour, " +
                "attachments, characters & abilities, pets, vehicles, modes, maps, ranks, tactics. Use ONLY when the user " +
                "asks about these (it takes a moment) — not for normal live calls.",
        listOf(ToolParam("query", ParamType.STRING, "gun/item/character/topic names or the user's question"))
    ) { _, args -> lookup(args.require("query")) }
}
