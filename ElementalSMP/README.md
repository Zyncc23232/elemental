# ElementalSMP

A Paper plugin for an elemental SMP: Fire, Water, Earth, Wind and Lightning.

## Build

You need Java 21 and Maven.

```
mvn package
```

The plugin jar will be at `target/ElementalSMP-1.0.0.jar`. Drop it in your server's `plugins/` folder and restart.

Targets Paper 1.21.x. If your server is a different version, change the `paper-api` version in `pom.xml`.

## How it works

- Every player gets a random element the first time they join.
- Press **F** (swap hands) for your first ability and **Shift+F** for your second.
- Cooldowns show on the action bar.
- Element triangle: Water > Fire > Wind > Earth > Lightning > Water. Winning matchup = +25% ability damage, losing = -20%.

## Commands

- `/element` shows your element and powers
- `/element list` shows all elements
- `/element info <element>` shows one element
- `/element set <player> <element>` changes someone's element (needs `elemental.admin`, ops have it by default)

## Ideas to add next

- Element reroll item, crafted from rare materials
- Elemental armor and weapons that boost your element
- A leveling system that unlocks a third ability
- Elemental "domains" or arenas for duels
- A config.yml for damage and cooldowns
