# Simple Currencies – Fully Customizable Multi-Currency System with Dynamic Commands

Simple Currencies is a lightweight yet powerful Minecraft currency plugin designed for servers that need multiple independent currencies, each with its own configuration, commands, storage, and placeholders.

Create unlimited currency systems such as coins, gems, tokens, or credits — all fully configurable via simple YAML files, with automatic command generation and PlaceholderAPI support.

Perfect for:
- Survival
- RPG
- Minigames
- Economy-based servers
- Network servers

## ✨ Features
**🔹 Unlimited Currency Systems**

Create as many currencies as you want by simply adding new .yml files.

Each currency has:
- its own command (/coins, /gems, /tokens, …)
- its own balance storage
- per-currency permissions
- optional decimal support (int or double)
- disabled worlds
- full UUID-based data storage

**🔹 Dynamic Commands**

Commands are generated automatically based on the currency file name.

Example for coins.yml:

- /coins give <player> <amount>
- /coins remove <player> <amount>
- /coins balance [player]
- /coins wipe
- /coins transfer <targetCurrency>


No manual command registration required.

**🔹 Decimal or Integer Currencies**

Choose whether a currency should support decimals or only full values.

```
settings:
  decimals: true
```

**🔹 UUID-Based Player Storage**

All balances are stored using UUIDs, with player names tracked for readability.

```
balances:
  1c3a4c9e-xxxx-xxxx-xxxx-xxxxxxxxxxxx:
    name: Steve
    balance: 1250
```

- ✔ Safe across name changes
- ✔ No data loss
- ✔ Server-wide compatible

**🔹 Currency Transfer System**

Easily migrate balances between currencies.
```
/coins transfer gems
```

Moves all player balances from one currency to another. Perfect for economy resets or reworks.

**🔹 PlaceholderAPI Support**

Use currency values anywhere on your server:
- TAB
- Scoreboards
- Chat
- Holograms
- Bossbars
- Other plugins

**Placeholders:**

- %simplecurrencies_<currency>_balance% - Displays the player’s balance
- %simplecurrencies_<currency>_global_balance% - Displays the server-wide total balance of that currency

**🔹 Admin Management Commands**

Administrators can fully control currencies:
```
/coins give <player> <amount>
/coins remove <player> <amount>
/coins balance [player]
/coins wipe
/coins transfer <currency>
/sc reload <currency|all>
```

**🔹 World Restrictions**

Disable currencies in specific worlds:
```
disabled-worlds:
  - world_nether
  - dungeon_world
```

Players cannot use the currency commands in those worlds.

**🔹 Modern & Version-Safe Architecture**
- CommandAPI-based command system
- No NMS usage
- No Paper-only APIs
- Fully compatible with Spigot / Paper / Purpur
- Supports Minecraft 1.16.1 → 1.21.x
- Java 17 compatible

## 📦 Requirements

- Spigot / Paper / Purpur 1.16.1+
- Java 17
- PlaceholderAPI (optional, for placeholders)

## 🧩 Support

Found a bug or want a new feature?
Contact me through the [support Discord](https://discord.gg/mE9zbXkWyv).