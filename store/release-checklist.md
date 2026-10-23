# Release checklist

Steps only the account owner can do are marked **(you)**.

## 1. One-time setup

- [ ] **(you)** Google Play developer account (one-time US$25), with identity verification.
      New personal accounts must run a **closed test with at least 12 testers for 14 days**
      before production — recruit tutors from local teacher WhatsApp groups early.
- [ ] **(you)** Create an upload key — keep it and its passwords safe, and outside the repo:
  ```
  keytool -genkeypair -v -keystore keys/upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
  ```
- [ ] **(you)** Create `keystore.properties` in the project root (git-ignored):
  ```
  storeFile=keys/upload.jks
  storePassword=…
  keyAlias=upload
  keyPassword=…
  ```
- [ ] **(you)** In Play Console, enrol in **Play App Signing** (default for new apps).
- [ ] **(you)** Privacy policy URL: repo Settings → Pages → Deploy from branch `main`, folder `/docs`.
      Then fill in the support email in `docs/privacy-policy.html` and use
      `https://softandtech.github.io/tuition-manager/privacy-policy.html`.

## 2. Pro product

- [ ] **(you)** Play Console → Monetize → In-app products → Create product
  - Product ID: **`pro_unlock`** (must match `ProRepository.PRO_PRODUCT_ID`)
  - Name: Tuition Manager Pro · Description: Unlimited students and batches
  - Price: suggested **₹399** (Play converts for other countries)
- [ ] **(you)** Add your Gmail as a **license tester** (Settings → License testing) to buy Pro
      with test cards at no cost.

## 3. Each release

- [ ] Bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts`.
- [ ] `./gradlew testDebugUnitTest` — all pass.
- [ ] On a device/emulator: `./gradlew connectedDebugAndroidTest` — all pass.
- [ ] `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`
- [ ] Check the merged manifest still has **no INTERNET permission**
      (`app/build/intermediates/merged_manifest/release/…/AndroidManifest.xml`) — see `store/data-safety.md`.
- [ ] Smoke-test the release build (R8 can break things debug builds don't show):
      lock/unlock, add student, collect fee, share receipt, backup + restore.
- [ ] **(you)** Upload the `.aab` to the closed-testing track, then production.

## 4. Store listing

Texts: `store/listing.md` (English + Hindi). Graphics: `store/graphics/`.
Data safety, content rating, target audience: `store/data-safety.md`.
