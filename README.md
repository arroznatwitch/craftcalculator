<p align="left">
  <img src="https://raw.githubusercontent.com/arroznatwitch/CraftCalculator/master/src/main/resources/assets/craft_calculator_icon/icon.png" width="64" alt="CraftCalculator icon">
</p>

# CraftCalculator

A simple mod that calculates the crafting materials required for any item and amount.

## Usage

```
/cc <item> <amount>
/craftc <item> <amount>
/craftcalculator <item> <amount>
```

Works in Portuguese and Portuguese-Brazilian, follows the game's language automatically.

## Build and Run

```bash
git clone https://github.com/arroznatwitch/CraftCalculator.git
cd CraftCalculator
./gradlew runClient   # opens a client with the mod loaded
./gradlew build        # jar ends up in build/libs/
```

Entry point: `com.craftcalculator.CraftCalculatorMod`

Has its own `ItemArgumentType` for handling namespaced IDs (`mod:item`) and a custom recipe index.

## Stack

Java 21+, Fabric Loader, Loom 1.16
