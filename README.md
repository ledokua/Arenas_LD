Arenas_LD is a mod designed for creating custom arenas, dungeons, and raid encounters. It provides advanced tools for map makers to control mob spawning, manage battle progression, and distribute rewards.

Key Features

1. Advanced Spawners
The mod introduces two types of highly configurable block entities to manage encounters:
•
Mob Spawner:
◦
Trigger System: Activates when a player enters a configurable radius.
◦
Battle Logic: Spawns a specific number of mobs. The battle is "won" only when all mobs are defeated. It resets if players leave the battle area.
◦
Customization: Fully editable via GUI. You can set the Mob ID, mob count, spawn spread, and respawn cooldown.
◦
Attributes & Equipment: Includes dedicated screens to configure:
▪
Attributes: Max Health, Attack Damage, etc.
▪
Equipment: Armor (Head, Chest, Legs, Feet) and Hand items (Main/Offhand), plus drop chances.
◦
Grouping: Can be assigned a Group ID. This links multiple spawners together to control Phase Blocks and sets "team" for mobs.
•
Boss Spawner:
◦
Raid Mechanics: Designed for single, powerful bosses. Requires a minimum number of players to start and broadcasts a server-wide message upon activation.
◦
Portal Integration:
▪
Spawns an Enter Portal when the boss is ready to fight.
▪
Spawns an Exit Portal (for a limited time) when the boss is defeated.
◦
Rewards: In addition to standard drops, it can distribute a Loot Bundle item to every participating player, ensuring everyone gets a reward.

2. Phase Blocks (Gating System)
•
Dynamic Obstacles: These blocks look like normal blocks but can switch between Solid (impassable) and Unsolid (passable) states.
•
Group Progression: They are linked to Mob Spawners via a Group ID.
•
Logic: A group of Phase Blocks will only open (become unsolid) when ALL Mob Spawners with the same Group ID have been defeated. They remain open until the spawners' cooldowns expire and they reset.
•
Persistence: The "Won" state of spawners is saved, so doors won't accidentally close if the server restarts while spawners are on cooldown.

3. Custom Items
•
Loot Bundle: A sack/bundle item given to players after a boss raid. When used (Right-Click), it generates items from a configured "Per Player Loot Table".
•
Linker: A tool used to manage or view Group IDs (based on the data components).

4. Portals
•
Enter & Exit Portals: Teleportation blocks used to move players into and out of arenas.

5. Configuration
•
In-Game GUI: Almost every aspect of the spawners (radius, timers, loot tables, mob stats) can be edited directly in-game using a custom user interface.

<img width="1381" height="686" alt="image" src="https://github.com/user-attachments/assets/8b9cd912-27aa-49f3-af74-32ad0fec1964" />
<img width="661" height="958" alt="image" src="https://github.com/user-attachments/assets/4b2c0bb4-fe4e-41b9-9a4f-3abd61c172d5" />
<img width="657" height="949" alt="image" src="https://github.com/user-attachments/assets/680c5c96-667f-4d78-a797-d461c807df39" />
<img width="656" height="938" alt="image" src="https://github.com/user-attachments/assets/c0be8167-3b91-40e7-a88a-8a8b22021d83" />
<img width="351" height="587" alt="image" src="https://github.com/user-attachments/assets/e872772b-f957-4ae3-919c-8de6e193b3c3" />

