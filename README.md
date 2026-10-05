# Codenames (two phones)

The word-guessing party game **Codenames**, played with **two phones** instead of cards:

- the **spymaster phone** creates the game and shows the secret key (which of the 25 words are red, blue,
  bystanders or the assassin) to the two spymasters;
- the **table phone** lies in the middle and shows the 25 words to everybody. The team taps a word,
  confirms, and the phone only then learns that card's colour.

The phones talk to each other live, so a guess made on the table phone shows up on the spymaster phone
at once, together with whose turn it is, how many agents are left and the clue the spymaster typed.

Native Android app (Kotlin, Jetpack Compose). English, Portuguese and Spanish, each with its own
list of about 400 words. Colour-blind friendly: red cards have stripes, blue cards have dots.

## How the phones connect

| Mode | Needs | Notes |
|---|---|---|
| **Nearby** (default) | Bluetooth and Wi-Fi Direct, Google Play services | No internet and no shared Wi-Fi. Both phones show the same 4-digit code and the spymaster phone accepts. |
| **Same Wi-Fi** | Both phones on one network (a phone hotspot works) | Type the address and the 6-digit PIN shown on the spymaster phone into the table phone. A plain TCP socket, one JSON message per line; five wrong PINs lock that address out for a minute. |

The **spymaster phone is the only place that knows the key.** The table phone is only sent the words and the
colours of cards that have already been uncovered; the whole key is sent once the game is over.
If the connection drops, the table phone reconnects by itself and gets the current state. The spymaster
phone saves the game, so closing the app does not lose it (use **Resume game**).

> **Status:** the game rules, the protocol, the Wi-Fi link and all screens are covered by unit tests and were
> tested end to end on two emulators. The **Nearby** link follows Google's Nearby Connections documentation
> but could only be tested for "does not crash" without Bluetooth hardware, so please try it on two real phones.

## Rules implemented

25 words; the team that starts has 9 agents, the other team 8, plus 7 bystanders and 1 assassin.
A clue is one word and a number, typed on the spymaster phone (it cannot be a word still on the board); the table
phone lets the team guess only once there is a clue. The team may make one guess more than the number (a 0 or
infinity clue means unlimited guesses). Own agent: keep guessing. Bystander or the other team's agent: the turn ends.
Assassin: the team that picked it loses. First team to uncover all its agents wins.
The rules are also inside the app (the **?** button).

## Build

Requirements: JDK 17 or newer and the Android SDK (platform 35).

```sh
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # path to your Android SDK
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Layout

| Path | What |
|---|---|
| `app/src/main/java/com/zepinto/codenames/Game.kt` | Cards, teams, the rules engine, word picking (plain Kotlin, unit tested) |
| `.../Protocol.kt`, `Session.kt` | Messages between the phones, host and table sessions, which sound to play |
| `.../Link.kt`, `LanLink.kt`, `NearbyLink.kt` | The two transports behind one interface |
| `.../AppViewModel.kt` | Wires game, sessions and links together; reconnection |
| `.../Screens.kt`, `Theme.kt`, `Strings.kt`, `Sfx.kt` | UI, look, texts (three languages, rules page), synthesised sounds |
| `app/src/main/assets/words_{en,pt,es}.txt` | One word per line |

## Testing two phones on a PC

Two emulators can play together over the Wi-Fi mode: run both with `-read-only`, forward a host port to the
spymaster emulator (`adb -s emulator-5554 forward tcp:18765 tcp:8765`) and connect the table emulator to
`10.0.2.2:18765` with the PIN shown on the spymaster screen.

## Known limits

- The **Nearby** link has not been verified on real hardware yet (see Status above). On Android 12 and older the
  phone's location switch may have to be on for Nearby to find the other phone.
- There is no foreground service: keep the app open on both phones (the screen stays on by itself while it is).
- The Wi-Fi link is not encrypted; it is meant for a game among friends on a network you trust.

## Disclaimer

This is an unofficial, non-commercial fan implementation. Codenames is a game by Vlaada Chvátil published by
Czech Games Edition; this project is not affiliated with or endorsed by them. The word lists are original.
