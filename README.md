# ARISE

A discipline-leveling Android app styled after the System in *Solo Leveling*. You start at level 1 and level up
by actually living disciplined days. The System tracks:

| Quest | Source | Stat |
|---|---|---|
| Calories (limit) and protein (goal) | Yazio (typed in by hand) | VIT |
| Steps and active calories | Yazio | STR |
| Rubber bands (hours) and tooth brushing (count) | you | AGI |
| Evening skincare | you | CHA |
| Screen time and distraction % | AppBlock (typed in by hand) | PER |
| Self-development time (bonus only) | you | INT |
| Custom quests (yes/no, at least, at most) | you | any |

## Rules

- **Nothing can be reset.** There is no reset button, and Android's *Clear storage* is replaced by a notice.
  Data is included in Android backup, so it comes back if you reinstall.
- **A day locks at midnight.** Screen time and distraction can still be reported until **12:00 the next day**
  (or seal the day earlier). The day is scored at that moment.
- **Absent = worst.** A day with no report counts as the worst possible result for everything.
- **Levels can drop.** Every 5th level (5, 10, 15, …) is a safe zone you can never fall below. Losses are multiplied
  by your level (×1.7 at level 15, ×3.95 at level 60), so mistakes hurt more the higher you climb.
- **Calories are exponential.** +200 kcal is a scratch, +600 hurts, +1000 at level 18 drops you to 15.
- **Penalty Quests are corrective.** Failing the Daily Quest tightens only what went wrong the next day
  (less calories, lower screen limit, more steps…), always within reachable bounds.
- **Weekday targets.** Every target can differ per day of the week (e.g. 8,000 steps Monday–Friday, 500 on Saturday),
  set in SYSTEM → Targets.
- **Vacation mode** makes quests lenient and halves all gains and losses. The share of time you spend on vacation is
  always shown on the Status screen. Vacation, goal changes and new custom quests take effect from the next day.
- **Clock tampering** (turning the time back) locks the app until the clock is correct.

Ranks: E → D (10) → C (20) → B (35) → A (50) → S (70, about a year of good days) → S+ (80) → Mythical (90) →
Grandmaster (100) → Challenger (110). Max level 120.

Items drop rarely: Streak Freeze (also every 7-day streak), XP Booster, Random Box (also at every safe zone), Penalty
Shield and the legendary Skip Day Ticket.

## Gold, runes and equipment

- Every reported day earns **gold**: the XP you kept, plus 10 for a cleared Daily Quest. Absent days earn nothing.
- **Runes** are passive perks unlocked by playing (streaks, S-grade days, penalty clears…). The rune page has one keystone
  (from level 3) and up to three minor runes (levels 5, 12, 25).
- **Equipment** unlocks the same way and is bought with gold in the Armory. You get one item slot per rank (E 1 … S 6).
- Consumables (except Skip Day) can also be bought with gold.
- Loadout changes apply from the next day, and gear does nothing on a day you didn't report.
- Names are inspired by MOBA runes and items, not copied.

## Trials

- **3-Day Trial** (in blocks from your awakening day) and **Weekly Raid** (Monday–Sunday, two objectives) give extra XP
  and gold; clearing both raid objectives drops a Random Box.
- Objectives: Daily Quest clears, protein, calories, steps, rubber bands, brushing, skincare, screen time, S grades.
  Self-development is never an objective. Trials only reward; failing costs nothing.

## Notifications

Notifications fire from exact alarms (Doze-safe) and are re-armed after reboots, app updates and clock or time zone
changes. An hourly check delivers a missed morning, evening or final notification late instead of never.
SYSTEM → Notifications shows whether anything (permission, exact alarms, battery restrictions) could block them and can
send a test notification.

## Install

Every push builds a signed APK in GitHub Actions and publishes it under
[Releases](https://github.com/Kiskujab/Arise/releases). Download the newest `arise-v1.0.N.apk` on your phone and open it
(allow installing from your browser when asked). Updates install over the old version and keep your data.

From v1.0.3 on, Arise checks for new releases itself: a banner appears in the app (and a notification in the
background), and INSTALL downloads the APK and opens the system installer. The first time, Android asks you to allow
Arise to install apps.

> All builds are signed with `app/arise.jks`, committed to this public repo, so updates always match the installed app.
> Anyone could sign an APK with that key, so only install Arise builds from this repository's Releases page.

## Project layout

- `core/` – the game rules as plain Kotlin with unit tests (`ARISE_CORE_ONLY=1 ./gradlew :core:test`).
- `app/` – the Android app: Room database, WorkManager notifications, Jetpack Compose UI.
