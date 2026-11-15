# Play Store setup: Msg Prioritizer

Copy-paste answers for every Play Console form. Upload file: `release/MsgPrioritizer-1.0.0.aab`.

---

## Create app

| Field | Answer |
|---|---|
| App name | Msg Prioritizer |
| Default language | English (United Kingdom) – en-GB |
| App or game | App |
| Free or paid | Free |
| Declarations | Tick both (Developer Program Policies, US export laws) |

---

## Store listing

**App name** (30 max)
```
Msg Prioritizer
```

**Short description** (80 max, this is exactly 80)
```
Your most important messages first. Works with WhatsApp, Telegram, Signal & SMS.
```

**Full description**
```
Too many messages? Msg Prioritizer reads your message notifications and sorts them by importance, so the ones that need you come first and the noise sinks to the bottom.

HOW IT RANKS
Every message is marked HIGH, MEDIUM or LOW, with a plain explanation of why:
• Missed calls and urgent words like "urgent", "asap", "emergency", "call me"
• Messages from your VIP contacts
• Questions, deadlines, times and money
• Direct messages above busy group chats
• Someone sending several messages in a row
• Small talk, stickers and "good morning" forwards go to LOW

IT LEARNS FROM YOU
Tap a notification and that person moves up. Swipe them away and they move down. Settings shows exactly what it has learned, with one button to forget it.

ONLY BUZZES WHEN IT MATTERS
Get an alert for HIGH messages only.

PRIVATE BY DESIGN
• No internet permission: your messages physically cannot leave your phone
• No account, no ads, no tracking
• Messages are deleted automatically after 7 days
• Never sends, deletes or changes anything in your messaging apps

EASY TO READ
White text on black, with priority shown in words and border thickness instead of colour, so it works for colour-blind users too.

WORKS WITH
WhatsApp, WhatsApp Business, Telegram, Telegram X, Signal, Google Messages and Samsung Messages.

Msg Prioritizer is an independent app and is not affiliated with WhatsApp, Meta, Telegram, Signal or Google.
```

**Graphics needed**
- App icon 512×512 PNG: `play-assets/icon-512.png`
- Feature graphic 1024×500 PNG: `play-assets/feature-1024x500.png`
- Phone screenshots (at least 2): `play-assets/screenshot-*.png`

**Category:** Communication
**Contact email:** your new public email
**Privacy policy URL:** the published link to `PRIVACY.md`

---

## App content (Policy → App content)

| Section | Answer |
|---|---|
| Privacy policy | Paste the privacy policy URL |
| Ads | No, my app does not contain ads |
| App access | All functionality is available without special access |
| Content rating | See below |
| Target audience | 18 and over (keeps it out of the Families programme) |
| News app | No |
| Data safety | See below |
| Government app | No |
| Financial features | My app doesn't provide any financial features |
| Health | My app does not have any health features |

**Content rating questionnaire**
- Category: **All Other App Types**
- Every question (violence, sexual content, language, controlled substances, gambling): **No**
- Does the app allow users to interact or exchange content? **No** (it only reads your own notifications; users can't contact each other through it)
- Shares user location? **No**
- Allows purchases of digital goods? **No**

**Data safety form**
- Does your app collect or share any of the required user data types? **No**
  (Play counts data as "collected" only when it is sent off the device. This app has no internet permission, so nothing is sent.)
- Is all user data encrypted in transit? Not applicable (no data leaves the device)
- Do you provide a way for users to request that their data is deleted? **Yes**: Settings → "Forget what it learned", or uninstall; messages auto-delete after 7 days.

---

## Internal testing (install without warnings, up to 100 people)

1. Testing → **Internal testing** → **Create new release**
2. App integrity: keep **Google Play App Signing** (default). Our `keys/release.jks` becomes the *upload key*.
3. Upload `release/MsgPrioritizer-1.0.0.aab`
4. Release name: `1.0.0`. Release notes:
   ```
   First release: ranks WhatsApp, Telegram, Signal and SMS messages by importance, learns from your taps and swipes, fully on-device.
   ```
5. **Testers** tab → Create email list → add the Gmail addresses of people who should get the app
6. **Save → Review release → Start rollout to Internal testing**
7. Copy the **"Join on the web"** link from the Testers tab and send it to your testers. They open it, tap **Accept invite**, then **Download it on Google Play**.

## Going public later
New personal developer accounts must run a **closed test with at least 12 testers for 14 days in a row** before applying for production access. After that: Production → Create release → upload the same AAB.
