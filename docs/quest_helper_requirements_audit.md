# Quest Helper requirement audit

Report-only comparison. No changes were made to `quest_cards.json`.

## Baseline

- Quest Helper `5ea99d5ea9ba3fb096ebe7b5ed02d80883e9819d` (build version `4.16.1`): 189 full-quest helper entries.
- Bronzeman: 180 full quests.
- Exact title matches compared: 177.
- Quest Helper splits `Recipe for Disaster` into ten helpers and `Shield of Arrav` into two route helpers; Bronzeman stores one entry for each quest.
- `The Blood Moon Rises` exists only in Bronzeman in this comparison; the pinned Quest Helper revision has no matching helper.

## Interpretation

A Quest Helper-only item is a review candidate, not an automatic correction. Quest Helper models what its guide wants available, while Bronzeman models card-backed requirements. Bronzeman-only items are not removal candidates: several came from the existing walkthrough-mining pass and can represent interactions Quest Helper does not list in its top-level item requirements.

Only Quest Helper labels that resolve uniquely to an existing TCG Resource card are included as candidates. Unresolved generic labels and ID alternatives remain in the JSON artifact for manual inspection; no names are guessed.

## Summary

- 84 Quest Helper-only mandatory card candidates across 39 quests.
- 28 additional Quest Helper-only cards appear inside OR/alternative structures across 13 quests.
- 266 Bronzeman-only item cards across 95 quests.
- 386 distinct-per-quest Quest Helper labels could not be safely mapped to one TCG card.

## Priority review: Quest Helper-only mandatory candidates

- **Another Slice of H.A.M.**: Rope, Tinderbox
- **Below Ice Mountain**: Coins
- **Big Chompy Bird Hunting**: Hammer
- **Contact!**: Prayer potion
- **Creature of Fenkenstrain**: Hammer
- **Death Plateau**: Premade blurb' sp.
- **Death to the Dorgeshuun**: Tinderbox
- **Desert Treasure I**: Cake
- **Desert Treasure II - The Fallen Empire**: Air rune, Blood rune, Chaos rune, Death rune, Fire rune, Soul rune, Water rune
- **Dragon Slayer II**: Chisel
- **Eadgar's Ruse**: Tinderbox
- **Elemental Workshop II**: Coal
- **Enlightened Journey**: Logs, Tinderbox
- **Ethically Acquired Antiquities**: Coins
- **Forgettable Tale...**: Bucket of water, Dwarven stout
- **Garden of Tranquillity**: Fishing rod, Gardening trowel, Rake, Secateurs, Seed dibber, Spade, Watering can
- **Ghosts Ahoy**: Blue dye, Red dye, Yellow dye
- **Icthlarin's Little Helper**: Willow logs
- **In Aid of the Myreque**: Cosmic rune, Water rune
- **King's Ransom**: Black full helm, Black platebody, Black platelegs
- **Land of the Goblins**: Pestle and mortar, Vial
- **Legends' Quest**: Cosmic rune, Diamond, Emerald, Jade, Opal, Red topaz, Ruby, Sapphire
- **My Arm's Big Adventure**: Rake, Seed dibber, Spade, Supercompost
- **One Small Favour**: Bowl of hot water, Empty cup, Guam leaf, Harralander, Marrentill
- **Plague City**: Bucket of milk, Chocolate dust, Snape grass
- **Prince Ali Rescue**: Ball of wool
- **Rag and Bone Man II**: Coins
- **Ratcatchers**: Red spiders' eggs
- **Rum Deal**: Slayer gloves
- **Spirits of the Elid**: Air rune, Law rune
- **Tai Bwo Wannai Trio**: Coins, Hammer, Raw karambwan
- **Tears of Guthix**: Tinderbox
- **The Fremennik Trials**: Knife
- **The Giant Dwarf**: Air rune, Law rune
- **The Heart of Darkness**: Prayer potion
- **The Path of Glouphrie**: Prayer potion
- **Troll Romance**: Cake tin, Swamp tar
- **Underground Pass**: Plank
- **While Guthix Sleeps**: Astral rune, Coins, Cosmic rune, Logs

## Secondary review: cards inside Quest Helper alternatives

These must be reviewed as whole OR groups; no individual card below is necessarily mandatory.

- **A Taste of Hope**: Air rune, Cosmic rune
- **At First Light**: Box trap
- **Dragon Slayer II**: Air rune, Blood rune, Fire rune, Wrath rune
- **Eadgar's Ruse**: Coins
- **Icthlarin's Little Helper**: Coins
- **King's Ransom**: Air rune, Law rune, Lockpick
- **Legends' Quest**: Air rune, Fire rune, Water rune
- **Monkey Madness II**: Coins
- **Sins of the Father**: Coins, Cosmic rune, Fire rune, Vyrewatch legs, Vyrewatch shoes, Vyrewatch top
- **The Fremennik Exiles**: Coins, Keg of beer
- **The Great Brain Robbery**: Fur
- **Vampyre Slayer**: Coins
- **Wanted!**: Enchanted gem, Molten glass

## Bronzeman-only items (do not remove automatically)

- **A Kingdom Divided**: Chisel, Defence potion
- **A Porcine of Interest**: Knife
- **A Tail of Two Cats**: Cat, Druid's robe, Druid's robe top, Kitten
- **A Taste of Hope**: Knife, Pestle and mortar
- **At First Light**: Imcando hammer, Toy mouse
- **Below Ice Mountain**: Beer
- **Between a Rock...**: Coins
- **Big Chompy Bird Hunting**: Cabbage, Equa leaves, Onion, Potato, Tomato
- **Black Knights' Fortress**: Cabbage
- **Clock Tower**: Bucket of water
- **Cold War**: Clockwork, Oak plank, Plank, Steel bar
- **Contact!**: Coins
- **Death Plateau**: Asgarnian ale, Bread, Trout
- **Defender of Varrock**: Chaos core
- **Demon Slayer**: Bones, Coins
- **Desert Treasure I**: Chocolate bar, Cooking apple, Facemask, Lockpick, Magic logs, Molten glass, Pineapple pizza, Slayer helmet, Steel bar
- **Desert Treasure II - The Fallen Empire**: Pestle and mortar, Tinderbox
- **Devious Minds**: Colossal pouch, Large pouch
- **Doric's Quest**: Clay, Copper ore, Iron ore
- **Dragon Slayer I**: Air rune, Law rune
- **Dragon Slayer II**: Machete
- **Dream Mentor**: Shark
- **Enakhra's Lament**: Baked potato, Bread, Cake, Chocolate cake, Sandstone
- **Enlightened Journey**: Candle, Potatoes
- **Ernest the Chicken**: Spade
- **Family Crest**: Adamant pickaxe, Rune pickaxe
- **Fishing Contest**: Pearl fishing rod
- **Garden of Tranquillity**: Cabbage seed, Compost, Onion seed, Pure essence, Rune essence, Supercompost, Ultracompost
- **Getting Ahead**: Amy's saw, Costume needle, Fur, Grey wolf fur, Imcando hammer, Saw
- **Ghosts Ahoy**: Coins, Ecto-token
- **Goblin Diplomacy**: Red dye, Yellow dye
- **Grim Tales**: Leather gloves, Tarromin, Watering can
- **Haunted Mine**: Chisel
- **Heroes' Quest**: Black full helm, Black platebody, Black platelegs, Dusty key, Knife
- **Icthlarin's Little Helper**: Bag of salt, Bucket, Cat, Hellcat, Kitten, Overgrown cat, Pharaoh's sceptre, Waterskin
- **In Aid of the Myreque**: Bucket
- **In Search of the Myreque**: Imcando hammer
- **King's Ransom**: Granite
- **Legends' Quest**: Dragon axe, Imcando hammer, Infernal axe, Lockpick, Rune axe
- **Lunar Diplomacy**: Coins, Swamp tar
- **Making Friends with My Arm**: Bucket of water, Rope
- **Misthalin Mystery**: Bucket, Knife, Tinderbox
- **Monk's Friend**: Plank
- **Monkey Madness I**: Monkey bones
- **Monkey Madness II**: Chisel, Hammer
- **Mountain Daughter**: Plank, Staff
- **Mourning's End Part I**: Coal, Premade t'd crunch, Toad crunchies
- **Mourning's End Part II**: Catalytic talisman, Catalytic tiara, Death tiara
- **Murder Mystery**: Pot of flour
- **Olaf's Quest**: Rope
- **One Small Favour**: Imcando hammer, Soft clay
- **Pirate's Treasure**: Banana, White apron
- **Plague City**: Bucket of water
- **Priest in Peril**: Pure essence, Rune essence
- **Prince Ali Rescue**: Coins, Jug of water, Onion
- **Prying Times**: Imcando hammer
- **Ratcatchers**: Bottomless milk bucket, Cat, Hellcat
- **Regicide**: Limestone brick, Roast rabbit
- **Royal Trouble**: Plank, Rope
- **Rum Deal**: Bucket
- **Rune Mysteries**: Air talisman
- **Sea Slug**: Torch
- **Shades of Mort'ton**: Coins, Hammer, Logs, Tarromin
- **Sheep Shearer**: Wool
- **Shilo Village**: Candle, Torch
- **Sins of the Father**: Enchant ruby or topaz
- **Sleeping Giants**: Bucket of water, Imcando hammer
- **Song of the Elves**: Adamant seeds, Black dagger, Black knife, Irit leaf, Mithril seeds, Ode to eternity, Wine of zamorak, Zamorak brew
- **Swan Song**: Brown apron, Hammer, Logs, Small fishing net
- **Tai Bwo Wannai Trio**: Adamant spear, Dragon spear, Iron spear, Jogre bones, Logs, Mithril spear, Poison karambwan, Rune spear, Seaweed, Steel spear
- **Temple of Ikov**: Limpwurt root
- **The Corsair Curse**: Spade, Tinderbox
- **The Eyes of Glouphrie**: Bucket
- **The Feud**: Barrows gloves, Beer, Bucket, Kharidian headpiece, Slayer gloves
- **The Final Dawn**: Beer, Bones, Cooked meat, Knife
- **The Forsaken Tower**: Tinderbox
- **The Fremennik Exiles**: Fly fishing rod
- **The Fremennik Isles**: Coal, Coins, Knife, Mithril ore, Neitiznot shield, Raw tuna, Rope, Tin ore
- **The Fremennik Trials**: Beer, Beer tankard, Raw manta ray, Raw sea turtle, Raw shark
- **The Garden of Death**: Secateurs
- **The Giant Dwarf**: Sapphire
- **The Golem**: Pharaoh's sceptre
- **The Grand Tree**: Coins
- **The Hand in the Sand**: Beer, Coins
- **The Lost Tribe**: 3rd age pickaxe, Abyssal lantern, Adamant pickaxe, Black pickaxe, Bronze pickaxe, Bruma torch, Bullseye lantern, Candle lantern, Crystal pickaxe, Dragon pickaxe, Gilded pickaxe, Infernal pickaxe, Iron pickaxe, Lit candle, Mining helmet, Mithril pickaxe, Oil lamp, Oil lantern, Rune pickaxe, Steel pickaxe, Torch
- **The Ribbiting Tale of a Lily Pad Labour Dispute**: 3rd age axe, Adamant axe, Black axe, Bronze axe, Crystal axe, Dragon axe, Gilded axe, Infernal axe, Iron axe, Mithril axe, Rune axe, Steel axe
- **The Slug Menace**: Pure essence, Rune essence
- **Troll Romance**: Maple logs, Yew logs
- **Twilight's Promise**: Quetzal feed
- **Underground Pass**: Rope
- **Wanted!**: Coins, Pure essence, Rune essence
- **Watchtower**: Tinderbox
- **While Guthix Sleeps**: Death rune, Law rune, Mind rune, Mort myre fungus, Papyrus, Restore potion, Seed dibber, Snapdragon seed
- **Witch's House**: Cheese
- **Zogre Flesh Eaters**: Comp ogre bow, Knife

## Catalogue title differences

Quest Helper-only titles:

- RFD - Dwarf
- RFD - Evil Dave
- RFD - Finale
- RFD - Lumbridge Guide
- RFD - Monkey Ambassador
- RFD - Pirate Pete
- RFD - Sir Amik Varze
- RFD - Skrach Uglogwee
- RFD - Start
- RFD - Wartface & Bentnoze
- Shield of Arrav - Black Arm Gang
- Shield of Arrav - Phoenix Gang

Bronzeman-only titles:

- Recipe for Disaster
- Shield of Arrav
- The Blood Moon Rises
