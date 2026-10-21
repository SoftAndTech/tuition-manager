# Tuition Manager — Implementation Plan

> Working name: **Tuition Manager** (final Play Store name TBD)
> Owner: Soft And Tech Solutions
> Platform: Android (Kotlin + Jetpack Compose)
> Status: **Planning — awaiting approval before coding starts**

---

## 1. Problem & Target Users

Home tutors, small coaching centres, music/dance/art teachers and sports coaches
(5–300 students) track fees and attendance in notebooks or Excel. Existing options are
either heavy school ERPs (expensive, complex, cloud-only) or ad-filled apps that force
login and upload student data to unknown servers.

**Our promise:** *Simple, offline, private. Your students' data never leaves your phone.*

## 2. Database Decision (MySQL vs Local)

| Option | Verdict |
|---|---|
| **MySQL** | ❌ Not used in v1. MySQL is a *server* database — it cannot run inside an Android phone. Using it means every teacher's student data sits on our server: we'd need hosting, security, a privacy policy for stored personal data of minors, and ongoing server cost. That contradicts the privacy promise. |
| **SQLite via Room (on-device) + SQLCipher encryption** | ✅ **Chosen.** Data lives only on the teacher's phone, works 100% offline, zero server cost, and the whole database file is AES-256 encrypted. |

MySQL (or similar) can be added **later and optionally** only if we build a paid
multi-device / web-dashboard feature (Phase 6), with explicit user opt-in.

## 3. Privacy & Protection Design

1. **Encrypted database** — Room + SQLCipher (`net.zetetic:sqlcipher-android`).
   A random 256-bit DB key is generated on first launch, wrapped by an
   **Android Keystore** key, and never leaves the device in plain form.
2. **App lock** — PIN + fingerprint/face unlock (`androidx.biometric`), auto-lock after
   N minutes in background. Optional `FLAG_SECURE` to block screenshots/recents preview.
3. **No account, no login, no internet permission in core app.**
   (Internet only added later if Play Billing / optional features require it.)
4. **Encrypted backup & restore** — user exports a `.tmbak` file encrypted with a
   backup passphrase (AES-GCM, key derived via PBKDF2/Argon2). Saved via Android's file
   picker (SAF) — to Google Drive, WhatsApp-to-self, pen drive, anywhere the user chooses.
   We never see it.
5. **Android auto-backup disabled** for the DB (`allowBackup=false` / data-extraction
   rules), because Keystore keys can't be restored on a new phone — our own encrypted
   backup handles migration instead.
6. **No SMS / call-log / contacts-read permissions** (avoids Play Store policy trouble).
   Parent reminders go through the share sheet / `wa.me` links — the teacher taps Send.
7. **Play Store Data Safety:** "No data collected, no data shared."

## 4. Tech Stack

Matches our existing WifiGadget project for consistency:

- Kotlin 2.x, Jetpack Compose + Material 3, Navigation Compose
- Room 2.8 + SQLCipher, Kotlin Coroutines/Flow
- Hilt (DI), KSP
- DataStore (settings), WorkManager (daily due-fee reminders)
- androidx.biometric, Android Keystore
- PDF receipts: Android `PdfDocument` (no third-party lib)
- Google Play Billing (Pro unlock)
- minSdk 24, target/compileSdk 36
- Package: `com.softandtech.tuitionmanager`

Architecture: single-module MVVM to start (`data/`, `domain/`, `ui/` packages),
split into modules only if it grows.

## 5. Data Model (v1)

| Table | Key fields |
|---|---|
| `Batch` | id, name, subject, days (bitmask), startTime, endTime, defaultMonthlyFee, active |
| `Student` | id, name, phone, parentName, parentPhone, className/school, joinDate, photoUri?, notes, active |
| `Enrollment` | id, studentId, batchId, feeOverride?, joinedOn, leftOn? |
| `FeePlan` | id, enrollmentId, type (MONTHLY / ONE_TIME / PER_CLASS), amount, dueDay |
| `FeeDue` | id, studentId, period (yyyy-MM), amount, dueDate, status (DUE / PARTIAL / PAID / WAIVED) |
| `Payment` | id, studentId, feeDueId?, amount, mode (CASH / UPI / BANK / OTHER), paidOn, receiptNo, note |
| `Attendance` | id, batchId, studentId, date, status (PRESENT / ABSENT / LEAVE) |
| `Expense` *(Pro)* | id, category, amount, date, note |
| `Settings` (DataStore) | tutor name, institute name, logo, UPI ID, currency, receipt prefix, lock options |

Monthly `FeeDue` rows are generated automatically (WorkManager on the 1st + on app open).

## 6. Screens

1. **Onboarding** — tutor/institute name, UPI ID, set PIN.
2. **Dashboard** — today's batches, collected this month vs. pending, overdue list.
3. **Students** — list/search/filter by batch & due status; add/edit student.
4. **Student detail** — profile, fee history, attendance %, "Send reminder", "Collect fee".
5. **Batches** — list, add/edit, students in batch.
6. **Attendance** — pick batch → tap-to-mark grid (all present by default) → save.
7. **Collect fee** — amount, mode, auto receipt number → share PDF receipt on WhatsApp.
8. **Reports** — monthly collection, pending dues, attendance summary; export CSV/PDF *(Pro)*.
9. **Settings** — app lock, backup/restore, receipt template, language, Pro upgrade.

## 7. Monetization

| Free | Pro (one-time ₹299–499 **or** ₹99/month — A/B test) |
|---|---|
| Up to 15 students, 2 batches | Unlimited students & batches |
| Attendance, fee tracking | PDF receipts with logo, CSV/PDF reports |
| WhatsApp reminders (text) | Bulk reminders, expense tracking |
| Manual encrypted backup | Scheduled backup reminders, multiple themes |

No ads (a selling point vs. competitors).

## 8. Phased Delivery

| Phase | Scope | Est. |
|---|---|---|
| **0. Setup** | Project skeleton, Gradle catalog, Hilt, Room+SQLCipher, CI-ready structure, git | 2 days |
| **1. Core data** | Entities, DAOs, repositories, unit tests for fee-due generation | 4 days |
| **2. Students & batches** | CRUD screens, search, enrollment | 4 days |
| **3. Fees** | Due generation, collect payment, PDF receipt, WhatsApp share, overdue list | 5 days |
| **4. Attendance & dashboard** | Mark attendance, dashboard, basic reports | 4 days |
| **5. Security & backup** | PIN/biometric lock, encrypted backup/restore, auto-lock | 3 days |
| **6. Monetization & release** | Play Billing, free-tier limits, Hindi/Tamil/other strings, icon, store listing, privacy policy, closed testing (12 testers × 14 days rule) | 5 days |
| *Later* | Optional parent app/link, multi-device sync (opt-in, server DB), web dashboard | — |

≈ **4 weeks** to closed testing.

## 9. Testing

- Unit tests: fee-due generation (month boundaries, mid-month joins, fee overrides),
  partial payments, attendance %.
- Room migration tests from v1 onward.
- Instrumented tests: encrypted DB opens only with correct key; backup → restore round-trip.
- Manual: low-end device (2 GB RAM, Android 7), offline mode, app-lock edge cases.

## 10. Risks

| Risk | Mitigation |
|---|---|
| User loses phone → loses data | Prominent backup reminders; encrypted backup to their own Drive |
| User forgets backup passphrase | Clear warning at setup; we cannot recover (by design) |
| Play Store new-developer testing rule | Recruit 12+ tutor testers early (local teacher WhatsApp groups) |
| Competitors (Vyapar-style apps) | Focus on tutor-specific UX, offline, privacy, no ads |

## 11. Open Questions for Owner

1. Final app name and brand colours?
2. Pricing model: one-time vs. subscription?
3. Which regional languages first (Hindi, Tamil, Malayalam, Telugu…)?
4. GitHub repo name/visibility (proposed: `tuition-manager`, **private**).
