# SimpleCurrencies

A lightweight Paper (1.21+) plugin that supports **multiple independent currency systems** within a single plugin. Each `.yml` file inside `plugins/SimpleCurrencies/` defines its own currency, custom command, rules, and balances.

---

## Features

- **Unlimited Custom Currencies** — Create a new `.yml` file in `plugins/SimpleCurrencies/` (e.g., `coins.yml`, `gems.yml`) and get a dedicated `/<command>` automatically.
- **Decimals & Balance Limits** — Per-currency toggle for decimal support, starting balances, default max balances, and permission-based max balance overrides.
- **Player Payments (`/pay`)** — Let players send currency to each other, with a per-currency `pay-enabled` toggle.
- **Withdraw to Item (`/withdraw` & `/deposit`)** — Withdraw currency into customizable physical banknote items (`item`, `display-name`, `lore`) secured with `PersistentDataContainer` metadata. Redeem by right-clicking the item or running `/<command> deposit`.
- **Exchange Rates & Currency Transfers** — Define conversion rates between currencies in YAML or via `/sc rate`, and migrate balances between currencies with `/<command> transfer`.
- **Vault Economy Import** — Import balances from any Vault-compatible economy (e.g., EssentialsX, CMI) into a SimpleCurrencies currency with optional exchange rates and Vault balance clearing.
- **PlaceholderAPI Support** — Built-in expansion for player, formatted, global, and max balances.

---

## Commands

### Main Commands (`/simplecurrencies`, `/sc`, `/simplecurrencys`)

| Command | Permission | Default | Description |
| :--- | :--- | :--- | :--- |
| `/sc help` | — | Everyone | Show plugin command overview |
| `/sc reload <currency\|all>` | `simplecurrencies.reload` | `op` | Reload one or all currency configurations |
| `/sc rate set <from> <to> <rate>` | `simplecurrencies.rate` | `op` | Define an exchange rate (`1 <from> = <rate> <to>`) |
| `/sc rate get <from> <to>` | `simplecurrencies.rate` | `op` | View the configured exchange rate between two currencies |
| `/sc rate remove <from> <to>` | `simplecurrencies.rate` | `op` | Remove a configured exchange rate |
| `/sc rate list [currency]` | `simplecurrencies.rate` | `op` | List configured exchange rates |
| `/sc import Vault <currency> [rate] [clearVault]` | `simplecurrencies.import` | `op` | Import player balances from Vault into `<currency>` |

### Per-Currency Commands (`/<command>`)

Each currency registers the command configured under `command:` in its `.yml` file:

| Command | Permission | Default | Description |
| :--- | :--- | :--- | :--- |
| `/<command>` | — | Everyone | View your own formatted balance |
| `/<command> balance [player]` | — | Everyone | View your own or another player's balance |
| `/<command> pay <player> <amount>` | `simplecurrencies.pay` | `true` | Pay another player (requires `pay-enabled: true`) |
| `/<command> withdraw <amount>` | `simplecurrencies.withdraw` | `true` | Withdraw currency into a banknote item (requires `withdraw.enabled: true`) |
| `/<command> deposit` | `simplecurrencies.deposit` | `true` | Deposit the banknote item held in hand (or right-click the item) |
| `/<command> give <player> <amount>` | `simplecurrencies.give` | `op` | Add currency to a player's balance |
| `/<command> remove <player> <amount>` | `simplecurrencies.remove` | `op` | Remove currency from a player's balance |
| `/<command> wipe` | `simplecurrencies.wipe` | `op` | Wipe all player balances for this currency |
| `/<command> transfer <targetCurrency> [rate]` | `simplecurrencies.transfer` | `op` | Transfer all balances into `<targetCurrency>` using the configured or provided exchange rate, then wipe the source currency |

---

## Configuration (`plugins/SimpleCurrencies/<id>.yml`)

On first startup (when no `.yml` files exist), `default.yml` is generated automatically:

```yaml
# Display name of this currency
name: "Default Currency"

# Command that will be registered for this currency
command: "default"

# Should this currency support decimals?
decimals: false

# Can players pay each other using this currency?
pay-enabled: true

# Withdraw currency into a physical item (banknote)
# Placeholders for display-name and lore: %amount%, %currency%, %currency_id%, %player%
withdraw:
  enabled: false
  item: "PAPER"
  display-name: "&a%amount% %currency%"
  lore:
    - "&7Value: &e%amount% %currency%"
    - "&7Withdrawn by: &f%player%"
    - "&7Right-click to redeem"

# Balance new players start with
start-balance: 0

# Maximum balance for players (<= 0 means unlimited)
max-balance: 0

# Custom maximum balances by permission
# If a player has multiple permissions, the highest value wins
max-balance-permissions:
  simplecurrencies.maxbalance.premium: 10000

# Worlds where this currency is disabled
disabled-worlds:
  - example_world

# Exchange rates when transferring this currency to another currency
# Format: <target_currency_id>: <rate> (1 unit of this currency = <rate> units of target currency)
exchange-rates:
  points: 0.5

balances:
  00000000-0000-0000-0000-000000000000:
    name: "Player"
    balance: 0
```

---

## PlaceholderAPI Placeholders

Requires [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/). Replace `<id>` with the currency's filename (without `.yml`):

| Placeholder | Description |
| :--- | :--- |
| `%simplecurrencies_<id>_balance%` | Player's numeric balance |
| `%simplecurrencies_<id>_balance_formatted%` | Player's balance with the currency display name |
| `%simplecurrencies_<id>_global_balance%` | Sum of all player balances in `<id>` |
| `%simplecurrencies_<id>_max_balance%` | Default maximum balance configured for `<id>` |

---