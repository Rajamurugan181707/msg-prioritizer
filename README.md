# Msg Prioritizer

An Android app that ranks incoming messages by importance, so the ones that need you come first and the noise sinks to the bottom.

Works with **WhatsApp, WhatsApp Business, Telegram, Telegram X, Signal, Google Messages and Samsung Messages**.

- **Safe for your accounts.** It only reads the notifications Android already shows you. It never logs in to or automates any messaging app, so there is no ban risk.
- **Private.** All scoring happens on the phone. The app has **no internet permission**, so messages cannot leave the device. They are deleted after 7 days.
- **Explains itself.** Every message shows *why* it was ranked where it is.
- **Readable for everyone.** It uses white text on black, and priority is shown with words and border thickness, never colour alone.

## What makes it different (version 2.0)

- **Understands Tamil and Tanglish:** *seekiram, avasaram, call pannu, inniku, naalaiku, fees kattanum*, and Tamil script such as அவசரம், உடனே, தேர்வு, நாளை.
- **Finds deadlines:** "Assignment due tomorrow 5pm" shows **Due: tomorrow 5:00 pm · in 26 h**, appears in a *Deadlines coming up* list, and gets a reminder 1 hour before (or at 8 am for "due tomorrow").
- **Suggests rules you approve:** "Make Divya a VIP? You opened 5 of their messages, usually within 1 min." Nothing changes until you tap Accept.
- **Measures its own accuracy:** every message asks "Is this rank right?"; Settings → Accuracy shows the results per level and shares them as text for a report.

## Two editions

| Edition | Reads messages | Install from a link? |
|---|---|---|
| **Msg Prioritizer** (`full`) | Live, from notifications | Blocked in some countries (India included) when sideloaded, because it uses notification access. Install via Play Store or USB. |
| **Msg Prioritizer Lite** (`lite`) | Chats you share to it (WhatsApp → Export chat → Without media), import from a file, or paste | Yes, it has no sensitive permissions |

Both are built from the same code; `BuildConfig.LIVE` switches the live-only parts, and `app/src/lite/AndroidManifest.xml` removes the notification listener.

## Install the finished app

The signed release files are in `release/`:

| File | Use it for |
|---|---|
| `MsgPrioritizer-Lite-1.0.0.apk` | Send to anyone, installs from a link or WhatsApp. |
| `MsgPrioritizer-1.0.0.apk` | Full live edition. Send to anyone. They open it on their Android phone to install it (they may need to allow "Install unknown apps"). |
| `MsgPrioritizer-1.0.0.aab` | Upload to the Google Play Console. |

Rebuild them with `./gradlew assembleRelease bundleRelease`.

> **Back up your signing key.** `keys/release.jks` and `keystore.properties` prove that updates come from you. Copy both somewhere safe, like a password manager or an encrypted USB drive. If you lose them, you can't update the APK version people already installed. They are kept out of Git on purpose; never share them.

## Run it from source

1. Install [Android Studio](https://developer.android.com/studio).
2. **File → Open** and choose this `msg-prioritizer` folder. Let Gradle sync (the first time takes a few minutes).
3. Plug in an Android phone with USB debugging on, or start an emulator, then press **Run ▶**.
4. In the app, tap **Turn on notification access**, switch on *Msg Prioritizer*, and go back.
5. Open **Settings** and add your name and your VIP contacts.

Run the scoring tests from **app/src/test → PriorityScorerTest → Run**, or from the command line with `./gradlew test`.

> **Sharing the APK on Android 13+:** if someone installs your APK file directly instead of from the Play Store, Android blocks notification access at first. They need to open **Settings → Apps → Msg Prioritizer → ⋮ → Allow restricted settings**, then turn access on.

## How messages are scored

Every message starts at 20 points. Rules add or remove points:

| Rule | Points |
|---|---|
| VIP contact (from Settings) | +40 |
| VIP group | +20 |
| Missed voice or video call | +35 |
| Direct message / group chat | +15 / −10 |
| Mentions your name | +20 |
| Urgent words (urgent, asap, call me, help… plus Tanglish *seekiram, avasaram, call pannu* and Tamil அவசரம், உடனே…) | +25 |
| College words (exam, internals, mid-sem, viva, hall ticket, arrear, CGPA, attendance, assignment, fees, placement, internship, hackathon…) | +40 |
| Time words (today, tonight, 6pm…) | +10 |
| Money words (pay, rent, ₹, bill…) | +10 |
| Asks a question | +10 |
| 3 or more messages in a row within 5 min | up to +20 |
| Small talk or media only (ok, lol, gm, Sticker…) | −20 |
| **Learned:** you usually open their messages / swipe them away | up to +20 / down to −20 |
| **Learned:** you usually open their messages within 5 min | +10 |
| Muted contact or group | always LOW |

"Learned" rules start after 3 opens or swipes for a chat. Tapping a WhatsApp notification, reading the chat in WhatsApp, or pressing **Open WhatsApp** in the app counts as an open. Swiping the notification away counts as ignoring it. Settings shows what has been learned and has a button to forget it.

**60 or more → HIGH**, which buzzes you. **30–59 → MEDIUM.** **Below 30 → LOW.**

The rules live in `app/src/main/java/com/msgprioritizer/scoring/PriorityScorer.kt`.

## Project layout

```
scoring/PriorityScorer.kt     the rules (plain Kotlin, unit tested)
service/MessageListener.kt    receives message notifications, scores them, learns from taps and swipes
service/SupportedApps.kt      which messaging apps are read
service/NotificationParser.kt pulls the sender, chat and text out of a notification
service/Alerts.kt             the "Important" buzz for HIGH messages
data/Database.kt              on-device message storage (Room)
data/Prefs.kt                 your settings
ui/                           inbox, settings and setup screens
```

## Known limits

- **Android only.** iPhones don't let apps read other apps' notifications.
- Only sees messages that show a notification. Chats muted inside the messaging app won't appear, and it can't read old chat history.
- VIP and muted names must match how the contact appears in WhatsApp.
- Changing settings doesn't re-score messages that are already in the inbox.

## Ideas for next versions

- **Optional AI scoring** (off by default, with clear consent): send only MEDIUM messages to a model for a smarter second opinion.
- **More apps:** add the app's package name to `service/SupportedApps.kt` and to `<queries>` in `AndroidManifest.xml`.
- A home-screen widget showing the HIGH count.
