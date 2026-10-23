# Play Console answers

## Data safety (App content → Data safety)

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | All data stays on the device; the app has no INTERNET permission. Google's guidance: data processed only on the device is not "collected". |
| Is all user data encrypted in transit? | Not applicable (nothing is transmitted) | |
| Do you provide a way for users to request that their data is deleted? | **Yes** | Settings → Security & app lock → Erase all data, or uninstall. |

Play Billing: Google handles the purchase; per Play's guidance, data handled by Google Play
billing on Google's behalf doesn't need to be declared by the developer.

Verify after each release: the merged manifest must have **no `android.permission.INTERNET`**
(`app/src/main/AndroidManifest.xml` removes the one Play Billing's telemetry library adds).
If INTERNET ever comes back, these answers must be revisited.

## Content rating (App content → Content rating)

IARC questionnaire, category **Utility, Productivity, Communication, or Other**:
violence, sexual content, profanity, drugs, gambling — all **No**.
Users interact or share content with each other — **No** (sharing is via the user's own apps).
Shares the user's location — **No**. Digital purchases — **Yes** (Pro).
Expected rating: **Everyone / 3+**.

## Target audience (App content → Target audience)

**18 and over** (tutors and coaching-centre owners). Not designed for children.
Note for review: the app stores minors' names (students) entered by their tutor, on the tutor's
device only; nothing is collected by the developer.

## Ads

**No**, the app does not contain ads.

## Permissions declarations

- `POST_NOTIFICATIONS` — daily overdue-fee reminder, opt-in.
- `USE_BIOMETRIC` — optional fingerprint unlock.
- `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `ACCESS_NETWORK_STATE` — added by
  WorkManager for the daily reminder. No declaration form needed.
- `com.android.vending.BILLING` — Pro purchase.

No SMS, call log, contacts, location or storage permissions (none needing a declaration form).
