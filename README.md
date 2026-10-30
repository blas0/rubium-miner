# Rubium Miner

Helps you mine Rubium in the Charred Dungeon (Sailing 60 to reach): which rock
to mine next, how many splinters it still guarantees, when depleted rocks come
back, and when the deposit runs out.

## Rocks

- Outlines the active Rubium rock nearest you, or every active rock
  (setting: Rock highlight)
- Above the rock you're mining, how many splinters are still guaranteed. Every
  rock gives 2 before it can deplete, then has about a 1 in 6 chance per
  splinter, so the label reads "2 left", "1 left", then "1 in 6"
- "Fresh" on rocks you saw respawn and haven't mined yet
- Depleted rocks count down to their respawn in seconds, starting at "36s",
  using the game's own respawn timer

The splinter count only covers your own mining. If someone else mined the same
rock, the real count may be lower.

## Deposits

- Outlines the active deposit and labels the other one "Next deposit"
- Shows when the active deposit should deplete, for example "Depletes in
  2:50-4:00". It's a range because the client can't see when another player
  started mining it

## Session box

Three lines, each with an hourly rate:

- Splinters
- Mining XP
- GP, at the Grand Exchange price of the splinters

Rates count active time only, so banking trips and breaks don't drag them down.
The session resets after 5 idle minutes (setting: Session timeout) or when you
type `::rubium reset`.

## Notifications

Uses RuneLite's notification settings.

- Deposit depleted (on by default)
- Your rock depleted (off)
- Inventory full (off)

## Core Mining plugin

RuneLite's built-in Mining plugin draws its own session box, so Rubium Miner
turns it off when you switch Rubium Miner on. Turning Mining back on turns
Rubium Miner off. If both are on when RuneLite starts, Mining starts first and
wins; switch Rubium Miner on once from the plugin list and Mining stays off.

## What it doesn't do

Rubium Miner only draws on screen and sends notifications. It doesn't click,
add or change menu options, or change click areas.

Rubium facts (yields, respawn times, deposit timing) are from the
[OSRS Wiki](https://oldschool.runescape.wiki/w/Rubium_rocks).
