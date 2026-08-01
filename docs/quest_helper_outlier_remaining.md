# Quest Helper generic-group outlier audit

Report only. No changes were made to `quest_cards.json`.

## Why the first audit missed these

The first audit matched Quest Helper display labels directly to TCG card names. Generic labels such as `An axe`, `Any pickaxe`, or `A light source` therefore stayed unresolved even though the pinned export already contained their RuneLite ItemID constants. This pass resolves both sources and compares the whole alternative group against the current schema-2 quest data.

## Baseline

- Quest Helper `5ea99d5ea9ba3fb096ebe7b5ed02d80883e9819d` (version `4.16.1`).
- Exact quest-title entries compared: 177.
- Candidate groups: 1 across 1 quests.
- Fully absent groups: 1.
- Partially represented alternative groups: 0.
- Still unresolved labels: 172.

A candidate is not automatically a correction. Food, combat supplies, optional routes, quest-obtainable tools, and broad equipment recommendations need a mechanics ruling before generation.

## Category summary

- **Weapon/ammo groups:** 1 groups across 1 quests (1 missing, 0 partial).

## Candidate groups

### Weapon/ammo groups

- **Rag and Bone Man II — Ranged weapon for killing vultures** (missing): missing Crossbow, Magic shortbow, Magic longbow, Yew shortbow, Yew longbow, Maple shortbow, Maple longbow, Willow shortbow, Willow longbow, Oak shortbow, Oak longbow, Shortbow, Longbow.

## Still unresolved after constant matching

- **A Kingdom Divided — Combat gear**: no exported constants.
- **A Kingdom Divided — Decent food**: no exported constants.
- **A Kingdom Divided — Melee combat gear to fight Judge of Yama**: no exported constants.
- **A Kingdom Divided — Melee or range gear to fight Xamphur.**: no exported constants.
- **A Night at the Theatre — Combat gear**: no exported constants.
- **A Night at the Theatre — Ghostspeak amulet**: AMULET_OF_GHOSTSPEAK, AMULET_OF_GHOSTSPEAK_ENCHANTED, MORYTANIA_LEGS_MEDIUM, MORYTANIA_LEGS_HARD, MORYTANIA_LEGS_ELITE.
- **A Night at the Theatre — Ivandis/Blisterwood flail**: IVANDIS_FLAIL, BLISTERWOOD_FLAIL.
- **A Porcine of Interest — Combat gear**: no exported constants.
- **A Soul's Bane — Combat gear + food**: no exported constants.
- **A Tail of Two Cats — Catspeak amulet**: ICS_LITTLE_AMULET_OF_CATSPEAK.
- **A Taste of Hope — Combat gear**: no exported constants.
- **A Taste of Hope — Rod of Ivandis**: BURGH_ROD_COMMAND_FINAL_10, BURGH_ROD_COMMAND_FINAL_9, BURGH_ROD_COMMAND_FINAL_8, BURGH_ROD_COMMAND_FINAL_7, BURGH_ROD_COMMAND_FINAL_6, BURGH_ROD_COMMAND_FINAL_5, BURGH_ROD_COMMAND_FINAL_4, BURGH_ROD_COMMAND_FINAL_3, BURGH_ROD_COMMAND_FINAL_2, BURGH_ROD_COMMAND_FINAL_1.
- **Animal Magnetism — Ghostspeak amulet**: AMULET_OF_GHOSTSPEAK.
- **Animal Magnetism — Holy Symbol**: BLESSEDSTAR.
- **Animal Magnetism — Polished Buttons**: ANMA_P_BUTTONS.
- **Another Slice of H.A.M. — Magic or ranged combat gear**: no exported constants.
- **Biohazard — Gas mask**: GASMASK.
- **Contact! — Combat gear, preferably magic/ranged**: no exported constants.
- **Contact! — Prayer potions**: _4DOSEPRAYERRESTORE, _3DOSEPRAYERRESTORE, _2DOSEPRAYERRESTORE, _1DOSEPRAYERRESTORE, _4DOSE2RESTORE, _3DOSE2RESTORE, _2DOSE2RESTORE, _1DOSE2RESTORE, HUNTER_MIX_MOONMOTH_2DOSE, HUNTER_MIX_MOONMOTH_1DOSE, BUTTERFLY_JAR_MOONMOTH.
- **Creature of Fenkenstrain — Armour and weapons defeat a level 51 monster and run past level 72 monsters**: no exported constants.
- **Creature of Fenkenstrain — Ghostspeak amulet**: AMULET_OF_GHOSTSPEAK, AMULET_OF_GHOSTSPEAK_ENCHANTED, MORYTANIA_LEGS_MEDIUM, MORYTANIA_LEGS_HARD, MORYTANIA_LEGS_ELITE.
- **Current Affairs — Charcoal**: CHARCOAL.
- **Death to the Dorgeshuun — Magic or melee combat gear**: no exported constants.
- **Defender of Varrock — Combat gear and food**: no exported constants.
- **Demon Slayer — Armour**: no exported constants.
- **Desert Treasure I — Charcoal**: CHARCOAL.
- **Desert Treasure I — Climbing boots**: DEATH_CLIMBINGBOOTS, CLIMBING_BOOTS_G.
- **Desert Treasure I — Ice gloves/smiths gloves(i)**: ICE_GLOVES, SMITHING_UNIFORM_GLOVES_ICE.
- **Desert Treasure I — Spiked boots**: DEATH_SPIKEDBOOTS.
- **Desert Treasure II - The Fallen Empire — Combat gear**: no exported constants.
- **Desert Treasure II - The Fallen Empire — Ring of visibility**: FD_RING_VISIBILITY.
- **Dream Mentor — a third type of food**: no exported constants.
- **Dream Mentor — Combat gear**: no exported constants.
- **Dream Mentor — Goutweed**: EADGAR_GOUTWEED_HERB.
- **Dream Mentor — Seal of passage**: LUNAR_SEAL_OF_PASSAGE.
- **Dream Mentor — some other type of food**: no exported constants.
- **Dream Mentor — some type of food**: no exported constants.
- **Eadgar's Ruse — Climbing boots**: DEATH_CLIMBINGBOOTS, CLIMBING_BOOTS_G.
- **Elemental Workshop II — Battered Key**: ELEMENTAL_WORKSHOP_KEY.
- **Enakhra's Lament — 52 kg of sandstone**: no exported constants.
- **Enakhra's Lament — Granite (5kg)**: ENAKH_GRANITE_MEDIUM.
- **Fairytale I - Growing Pains — Dramen or lunar staff**: DRAMEN_STAFF, LUNAR_MOONCLAN_LIMINAL_STAFF.
- **Fairytale I - Growing Pains — Draynor skull**: FAIRY_SKULL.
- **Fairytale I - Growing Pains — Ghostspeak amulet**: AMULET_OF_GHOSTSPEAK, AMULET_OF_GHOSTSPEAK_ENCHANTED, MORYTANIA_LEGS_MEDIUM, MORYTANIA_LEGS_HARD, MORYTANIA_LEGS_ELITE.
- **Fairytale II - Cure a Queen — Dramen or lunar staff**: DRAMEN_STAFF, LUNAR_MOONCLAN_LIMINAL_STAFF.
- **Fishing Contest — Red Vine Worm**: RED_VINE_WORM.
- **Forgettable Tale... — A random item per player**: no exported constants.
- **Garden of Tranquillity — Normal/Super/Ultra compost**: BUCKET_COMPOST, BUCKET_SUPERCOMPOST, BUCKET_ULTRACOMPOST.
- **Garden of Tranquillity — Ring of Charos**: RING_OF_CHAROS.
- **Getting Ahead — You can get all the required items during the quest.**: no exported constants.
- **Ghosts Ahoy — Ghostspeak amulet**: AMULET_OF_GHOSTSPEAK.
- **Ghosts Ahoy — Nettle tea**: BOWL_NETTLETEA.
- **Goblin Diplomacy — Orange dye**: ORANGEDYE.
- **Grim Tales — Combat gear and food**: no exported constants.
- **Grim Tales — Door key**: WITCHES_DOORKEY.
- **Haunted Mine — Combat gear**: no exported constants.
- **Heroes' Quest — A ranged or magic attack method**: no exported constants.
- **Holy Grail — Excalibur**: EXCALIBUR.
- **Icthlarin's Little Helper — Bucket of sap**: ICS_LITTLE_SAP_BUCKET.
- **Icthlarin's Little Helper — Linen**: ICS_LITTLE_LINEN.
- **Icthlarin's Little Helper — Waterskin(4), bring a few to avoid drinking it**: WATER_SKIN4.
- **Imp Catcher — Black bead**: BLACK_BEAD.
- **Imp Catcher — Red bead**: RED_BEAD.
- **Imp Catcher — White bead**: WHITE_BEAD.
- **Imp Catcher — Yellow bead**: YELLOW_BEAD.
- **In Search of the Myreque — Charges in a druid pouch**: DRUID_POUCH.
- **In Search of the Myreque — Ring of Charos (a)**: RING_OF_CHAROS_UNLOCKED.
- **King's Ransom — Animate rock scroll**: FAVOUR_ANIMATE_ROCK.
- **King's Ransom — Any granite**: ENAKH_GRANITE_SMALL, ENAKH_GRANITE_MEDIUM, ENAKH_GRANITE_TINY.
- **Land of the Goblins — Combat gear**: no exported constants.
- **Land of the Goblins — Orange dye**: ORANGEDYE.
- **Legends' Quest — Ardrigal**: ARDRIGAL.
- **Legends' Quest — Charcoal**: CHARCOAL.
- **Legends' Quest — Combat gear, food and potions**: no exported constants.
- **Legends' Quest — Fire runes**: FIRERUNE.
- **Legends' Quest — Radimus notes**: THKARAMJAMAP, THKARAMJAMAPCOMP.
- **Legends' Quest — Snake weed**: SNAKE_WEED.
- **Lunar Diplomacy — Dramen staff**: DRAMEN_STAFF.
- **Making Friends with My Arm — Cadava berries**: CADAVABERRIES.
- **Making Friends with My Arm — Combat gear, preferably ranged or melee**: no exported constants.
- **Making History — Ghostspeak amulet**: AMULET_OF_GHOSTSPEAK, AMULET_OF_GHOSTSPEAK_ENCHANTED, MORYTANIA_LEGS_MEDIUM, MORYTANIA_LEGS_HARD, MORYTANIA_LEGS_ELITE.
- **Merlin's Crystal — Bucket of wax**: BUCKET_WAX.
- **Merlin's Crystal — Combat gear + food for Sir Mordred (level 39)**: no exported constants.
- **Monkey Madness I — Monkey bones or corpse**: MM_NORMAL_MONKEY_BONES, TBWT_MONKEY_CORPSE.
- **Monkey Madness II — M'speak amulet**: MM_AMULET_OF_MONKEY_SPEAK.
- **Monkey Madness II — Monkey talisman**: MM_MONKEY_TALISMAN.
- **Monkey Madness II — Ninja greegree**: MM_MONKEY_GREEGREE_FOR_SMALL_NINJA_MONKEY, MM_MONKEY_GREEGREE_FOR_MEDIUM_NINJA_MONKEY.
- **Monkey Madness II — Translation book**: GRANDTREE_TRANSLATIONBOOK.
- **Mourning's End Part I — Barrel of coal tar**: REGICIDE_BARREL_TAR.
- **Mourning's End Part I — Barrel of naphtha**: REGICIDE_BARREL_NAPHTHA.
- **Mourning's End Part I — Ogre bellows**: EMPTY_OGRE_BELLOWS, FILLED_OGRE_BELLOW1, FILLED_OGRE_BELLOW2, FILLED_OGRE_BELLOW3.
- **Mourning's End Part I — Rotten apple**: ROTTENAPPLES.
- **Mourning's End Part II — Gas mask**: GASMASK.
- **Mourning's End Part II — Mourner boots**: MOURNING_MOURNER_BOOTS.
- **Mourning's End Part II — Mourner cloak**: MOURNING_MOURNER_CLOAK.
- **Mourning's End Part II — Mourner gloves**: MOURNING_MOURNER_GLOVES.
- **Mourning's End Part II — Mourner top**: MOURNING_MOURNER_TOP.
- **Mourning's End Part II — Mourner trousers**: MOURNING_MOURNER_LEGS.
- **My Arm's Big Adventure — Climbing boots**: DEATH_CLIMBINGBOOTS, CLIMBING_BOOTS_G.
- **My Arm's Big Adventure — Ugthanki dung**: FEUD_CAMEL_POOH_BUCKET.
- **Nature Spirit — Ghostspeak amulet**: AMULET_OF_GHOSTSPEAK.
- **Perilous Moons — Combat armour with high defensive bonuses**: no exported constants.
- **Plague City — Dwellberries**: DWELLBERRIES.
- **Rag and Bone Man II — Ice coolers**: SLAYER_ICY_WATER.
- **Ratcatchers — A non-overgrown cat**: WILEYCATOBJECT_HELL, WILEYCATOBJECT_LIGHT, WILEYCATOBJECT, WILEYCATOBJECT_BROWN, WILEYCATOBJECT_BLACK, WILEYCATOBJECT_BROWNGREY, WILEYCATOBJECT_BLUEGREY, LAZYCATOBJECT_HELL, LAZYCATOBJECT_LIGHT, LAZYCATOBJECT, LAZYCATOBJECT_BROWN, LAZYCATOBJECT_BLACK, LAZYCATOBJECT_BROWNGREY, LAZYCATOBJECT_BLUEGREY, GROWNCATOBJECT_HELL, GROWNCATOBJECT, GROWNCATOBJECT_LIGHT, GROWNCATOBJECT_BROWN, GROWNCATOBJECT_BLACK, GROWNCATOBJECT_BROWNGREY, GROWNCATOBJECT_BLUEGREY, KITTENOBJECT_HELL, KITTENOBJECT, KITTENOBJECT_LIGHT, KITTENOBJECT_BROWN, KITTENOBJECT_BLACK, KITTENOBJECT_BROWNGREY, KITTENOBJECT_BLUEGREY.
- **Ratcatchers — Catspeak amulet**: ICS_LITTLE_AMULET_OF_CATSPEAK, TWOCATS_AMULETOFCATSPEAK.
- **Ratcatchers — Empty vial**: VIAL_EMPTY.
- **Ratcatchers — Pot of weeds**: RATCATCHERS_WEEDPOT.
- **Ratcatchers — Snake charm**: SNAKE_FLUTE.
- **Romeo & Juliet — Cadava berries**: CADAVABERRIES.
- **Roving Elves — Glarial's pebble (obtainable in quest)**: GLARIALS_PEBBLE_WATERFALL_QUEST.
- **Roving Elves — Key (obtainable in quest)**: GOLRIE_KEY_WATERFALL_QUEST.
- **Royal Trouble — Combat gear**: no exported constants.
- **Rum Deal — Combat gear**: no exported constants.
- **Scrambled! — Combat gear**: no exported constants.
- **Secrets of the North — Combat Gear**: no exported constants.
- **Shadow of the Storm — Silverlight**: SILVERLIGHT, AGRITH_SILVERLIGHT_DYED.
- **Sins of the Father — Combat gear + food**: no exported constants.
- **Sins of the Father — Ivandis flail**: IVANDIS_FLAIL.
- **Song of the Elves — Gas mask**: GASMASK.
- **Song of the Elves — Mourner boots**: MOURNING_MOURNER_BOOTS.
- **Song of the Elves — Mourner cloak**: MOURNING_MOURNER_CLOAK.
- **Song of the Elves — Mourner gloves**: MOURNING_MOURNER_GLOVES.
- **Song of the Elves — Mourner top**: MOURNING_MOURNER_TOP.
- **Song of the Elves — Mourner trousers**: MOURNING_MOURNER_LEGS.
- **Spirits of the Elid — Crush Weapon Style**: no exported constants.
- **Spirits of the Elid — Slash Weapon Style**: no exported constants.
- **Spirits of the Elid — Stab Weapon Style**: no exported constants.
- **Swan Song — Pot lid**: POTLID.
- **Tai Bwo Wannai Trio — Ranged or Magic equipment to kill a level 3 monkey**: no exported constants.
- **Tale of the Righteous — Any ranged weapon + ammo**: no exported constants.
- **Tale of the Righteous — Runes for a few casts of a combat spell**: no exported constants.
- **The Corsair Curse — Combat gear + food to defeat Ithoi (level 34), who uses magic**: no exported constants.
- **The Curse of Arrav — Dwellberries**: DWELLBERRIES.
- **The Dig Site — Charcoal**: CHARCOAL.
- **The Eyes of Glouphrie — Bucket of sap**: ICS_LITTLE_SAP_BUCKET.
- **The Feud — Combat Gear bring Range or Mage Gear if safe spotting.**: no exported constants.
- **The Final Dawn — Melee Combat gear**: no exported constants.
- **The Fremennik Exiles — Combat gear**: no exported constants.
- **The Fremennik Exiles — Pet rock**: VT_USELESS_ROCK.
- **The Fremennik Exiles — Seal of passage**: LUNAR_SEAL_OF_PASSAGE.
- **The Giant Dwarf — Various ores and bars**: no exported constants.
- **The Great Brain Robbery — Diving apparatus**: HUNDRED_PIRATE_DIVING_BACKPACK.
- **The Great Brain Robbery — Fishbowl helmet**: HUNDRED_PIRATE_DIVING_HELMET.
- **The Great Brain Robbery — No pet following you or in your inventory**: no exported constants.
- **The Great Brain Robbery — Ring of Charos**: RING_OF_CHAROS, RING_OF_CHAROS_UNLOCKED.
- **The Great Brain Robbery — Wooden cat**: BRAIN_INV_WOODEN_CAT.
- **The Heart of Darkness — Combat gear**: no exported constants.
- **The Heart of Darkness — Prayer potions**: _4DOSEPRAYERRESTORE, _3DOSEPRAYERRESTORE, _2DOSEPRAYERRESTORE, _1DOSEPRAYERRESTORE, _4DOSE2RESTORE, _3DOSE2RESTORE, _2DOSE2RESTORE, _1DOSE2RESTORE, HUNTER_MIX_MOONMOTH_2DOSE, HUNTER_MIX_MOONMOTH_1DOSE, BUTTERFLY_JAR_MOONMOTH.
- **The Path of Glouphrie — Combat equipment**: no exported constants.
- **The Path of Glouphrie — Prayer potions**: _4DOSEPRAYERRESTORE, _3DOSEPRAYERRESTORE, _2DOSEPRAYERRESTORE, _1DOSEPRAYERRESTORE, _4DOSE2RESTORE, _3DOSE2RESTORE, _2DOSE2RESTORE, _1DOSE2RESTORE, HUNTER_MIX_MOONMOTH_2DOSE, HUNTER_MIX_MOONMOTH_1DOSE, BUTTERFLY_JAR_MOONMOTH.
- **The Path of Glouphrie — Tree Gnome Village dungeon key**: GOLRIE_KEY_WATERFALL_QUEST.
- **The Red Reef — Combat gear**: no exported constants.
- **The Red Reef — Ranged/Mage combat gear to skip boat combat, or a boat with cannons to deal with pirates**: no exported constants.
- **The Slug Menace — Commorb (can get another from Sir Tiffy)**: WANTED_CRYSTAL_BALL, SLUG2_CRYSTAL_BALL.
- **Throne of Miscellania — Flowers**: FLOWERS_WATERFALL_QUEST_RED, FLOWERS_WATERFALL_QUEST_YELLOW, FLOWERS_WATERFALL_QUEST_PURPLE, FLOWERS_WATERFALL_QUEST_ORANGE, FLOWERS_WATERFALL_QUEST_MIXED, FLOWERS_WATERFALL_QUEST, FLOWERS_WATERFALL_QUEST_BLACK, FLOWERS_WATERFALL_QUEST_WHITE, BREW_RED_FLOWER, BREW_BLUE_FLOWER.
- **Tree Gnome Village — Combat gear (magic is best)**: no exported constants.
- **Troll Romance — Bucket of wax**: BUCKET_WAX.
- **Troll Romance — Climbing boots**: DEATH_CLIMBINGBOOTS, CLIMBING_BOOTS_G.
- **Troll Romance — Combat gear, food, and potions**: no exported constants.
- **Troll Stronghold — Climbing boots**: DEATH_CLIMBINGBOOTS, CLIMBING_BOOTS_G.
- **Troubled Tortugans — Combat gear**: no exported constants.
- **Troubled Tortugans — Prayer restore**: _4DOSEPRAYERRESTORE, _3DOSEPRAYERRESTORE, _2DOSEPRAYERRESTORE, _1DOSEPRAYERRESTORE, _4DOSE2RESTORE, _3DOSE2RESTORE, _2DOSE2RESTORE, _1DOSE2RESTORE, HUNTER_MIX_MOONMOTH_2DOSE, HUNTER_MIX_MOONMOTH_1DOSE, BUTTERFLY_JAR_MOONMOTH.
- **Twilight's Promise — Two combat styles**: no exported constants.
- **Underground Pass — Combat Equipment**: no exported constants.
- **Vampyre Slayer — Combat gear + food to defeat Count Draynor**: no exported constants.
- **Wanted! — Combat gear**: no exported constants.
- **While Guthix Sleeps — Lit sapphire lantern**: TOG_SAPPHIRE_LANTERN_LIT.
- **While Guthix Sleeps — Magic weapon**: no exported constants.
- **While Guthix Sleeps — Melee weapon**: no exported constants.
- **While Guthix Sleeps — Ranged weapon**: no exported constants.
- **Witch's House — Combat gear and food for monsters up to level 53**: no exported constants.
