# Maikura Enchant Manager

A slot-based enchantment management mod for Minecraft Fabric, created by **MAIKURA KOBO**.

## v1.0.1

## Overview

Maikura Enchant Manager adds an **Enchant Manager Terminal** block that lets you apply enchantments to items through a dedicated GUI.

It is designed for survival, creative, and development environments, providing a clean enchantment management interface with search, category filters, suitable enchantment filtering, and high-level enchantment support.

## Features

* Block-based **Enchant Manager Terminal**
* Apply enchantments to an item placed in the GUI slot
* Displays suitable enchantments for the selected item
* Search support
* Category filter support
* High-level enchantment level support
* Vanilla enchantment compatibility support

  * Vanilla conflicts are respected by default
  * Conflicting enchantments are shown in the GUI
* Add and remove enchantments from the GUI
* Enchanted book support
* Books are converted into enchanted books when applying enchantments
* Mod Menu configuration support
* Japanese and English language support

## v1.0.1 Changes

* Removed support for applying non-suitable enchantments.
* Removed the Mod Menu toggle for allowing all enchantments.
* Removed the old `allowAllEnchantments` config option.
* The “All” category is now enabled only for books and enchanted books.
* For non-book items, the “All” category is shown as disabled and cannot be clicked.
* Single and bulk enchantment application now only apply suitable enchantments to normal items.
* Books are converted into enchanted books when applying all enchantments.

## Usage

1. Craft or obtain the **Enchant Manager Terminal**.
2. Place the terminal block.
3. Right-click the terminal to open the GUI.
4. Place an item into the input slot.
5. Select an enchantment from the list.
6. Adjust the enchantment level if needed.
7. Apply the enchantment.

For books, enchantments can be applied and the item will become an enchanted book.

## Enchantment Filtering

For normal items, only suitable enchantments can be applied.

For books and enchanted books, the **All** category is available, allowing enchantments to be applied to books.

For non-book items, the **All** category is shown as disabled and cannot be clicked.

## Configuration

This mod supports Mod Menu configuration.

Available settings may include GUI display options and vanilla conflict handling.

The old `allowAllEnchantments` option has been removed in v1.0.1.

## Important Notice

If you used v1.0.0 and could not log in, please update to v1.0.1 or later.

If the issue still occurs, delete the Maikura Enchant Manager config file and restart the game.

## Requirements

* Minecraft 1.21.11
* Fabric Loader
* Fabric API

## Optional

* Mod Menu

## License

This project is licensed under the MIT License.

## Author

MAIKURA KOBO / 舞倉工房
