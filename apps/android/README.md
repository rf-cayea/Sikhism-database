# Shabad OS — Android

**This is the platform app.** It began 2026-09-01/02 as a store-retention
scaffold — just enough to keep the Play listing current while the real app was
designed. **Superseded 2026-09-04**: one app across three shells, mobile ships
first ([ADR-0014](../../docs/architecture/decisions/0014-one-app-three-shells.md),
[plan.md](../../docs/plan.md#step-1-in-detail--mobile)). Logic added here is no
longer a liability to be minimised.

See [`apps/README.md`](../README.md) for what both platforms share and how it is
generated. This file is Android-specific.

```
./apps/build.sh android --run  # build, boot an emulator, install, launch
./apps/build.sh android        # build only
```

`--run` installs to an already-attached device if there is one (a plugged-in phone
counts), otherwise boots the first AVD and waits for `sys.boot_completed` before
installing — `adb wait-for-device` returns well before the launcher is up, and
installing that early fails.

The SDK now has `platform-tools`, `emulator`, a `Medium_Phone` AVD, and an
`android-37.1` system image, so `--run` has everything it needs. What is still
missing is the Gradle wrapper — open the project in Android Studio once. The script
fails with a specific message per missing piece rather than a Gradle stack trace.

Not a bun workspace member — same as `apps/presenter`. Root `bun install` does not
touch it.

## Store identity

| | |
| --- | --- |
| Application id | `com.shabados.app` |
| Display name | Shabad OS |
| Icon | `brand/assets/lotus.svg`, via the legacy app's 1024px export |
| Data safety | **No data collected** — no permissions, no network, no SDKs |

Where the application id came from, why changing it forfeits the listing, the
Sentry/PostHog reduction, and telemetry's ADR status are the same on both platforms
— see [`apps/README.md#store-identity`](../README.md#store-identity).

## Before submitting — still outstanding

The signing and version-bump items are the same shape as
[`apps/README.md`](../README.md#store-identity) on both platforms. The Android
specifics:

1. **Signing** — see [Signing](#signing). Without the upload key, release builds
   are unsigned.
2. **Bump `versionCode`** on every upload. Play rejects any code it has already
   seen; the legacy app's highest was 86 (`0.2.0`), so this app starts at 87
   (`0.3.0`). **As of 2026-10-14 nothing with 87 has been uploaded** — Play
   Console's bundle list ends at 86, and Production still holds a draft of 73. A
   bundle 87 built 2026-10-10 from `0f3844403c` was never uploaded and is
   superseded: rebuild from the commit being released rather than uploading it.
3. **A proper adaptive-icon foreground.** The current one insets the square 1024px
   icon into the safe zone over an approximated gradient, which works but has a
   faint seam. The right fix is a transparent-background lotus exported from
   `brand/assets/lotus.svg`. Play also wants a separate 512×512 listing icon.
4. **Confirm `targetSdk 36` is still current** — Play's target-API requirement is
   the actual deprecation pressure here.

## Signing

Play App Signing is on: Google holds the key that signs what users install. We
hold only the **upload key**, which proves an upload came from us. Play rejects
any upload signed with a different key.

The upload key is `shabados-upload.jks`, alias `upload`, generated with `keytool`
and registered through Play Console's upload key reset in October 2026. It is not
the legacy Expo app's key: that one was never found in the `shabados` Expo
organization's credentials. Whoever holds the keystore and its password can
release. Keep it in a password manager, never in the repo (`*.jks` is gitignored).

**Custody.** The keystore and its password are held by @bhajneet and @harjot1singh.
To release, ask one of them for access.

To sign, add to `apps/android/local.properties`:

```
upload.storeFile=/absolute/path/to/shabados-upload.jks
upload.storePassword=…
upload.keyAlias=upload
upload.keyPassword=…
```

`app/build.gradle.kts` signs release builds with it when those keys are present.
Confirm with `keytool -printcert -jarfile app/build/outputs/bundle/release/app-release.aab`.
The SHA-1 must be `2E:FB:87:D0:5D:F2:76:77:E9:B8:70:79:9E:FA:91:10:01:69:8C:DE`, as
shown at Play Console → Protected with Play → Play Store protection → *Manage Play
app signing* → upload key certificate.

If the key is lost: generate a new one, export its certificate
(`keytool -export -rfc … -file upload_certificate.pem`), and use *Request upload
key reset* on that same page. Google approves it in a few days, and the new key
works from a date it states.

## Deliberately absent

- **Nothing of `packages/gurmukhi` beyond what the reader needs.** `Bani.kt` calls
  `isHeading`/`isMoolmantar`/`hasIkoankar`/`isColophon`, `vishraams`/`detect`/`remove`
  and `transcribe` through the generated `uniffi.gurmukhi` bindings; anything else the
  crate exposes is unused here so far. Building those bindings is a one-time local
  setup step (`mise run android` in `packages/gurmukhi`, see
  [`apps/README.md#packagesgurmukhi-in-the-apps`](../README.md#packagesgurmukhi-in-the-apps))
  — `jniLibs/` and `bindings/kotlin/lib/` are build outputs and are not committed, so a
  fresh clone must run it before Gradle will compile.
- **A shared core** — no session/search/navigation core on any platform yet
  ([ADR-0010](../../docs/architecture/decisions/0010-shared-core-across-platforms.md)
  Needs discussion; see [`apps/README.md`](../README.md#what-is-shared-and-how)).
- **ProGuard/R8 shrinking.** There is nothing to shrink, and an unverified
  ProGuard config is how you ship a release build that crashes where debug does not.

## Reader size and pinch

Ported from iOS 2026-10-14
([display-controls.md](../../docs/requirements/display-controls.md#zoom)). The
rules are in `ReaderSize.kt`, which has no Android types, so `src/test` can pin them
on the JVM. `BaniReaderView.kt` only measures and draws.

- **Sizes are dp, drawn as-is.** Reading text is rendered with the font scale
  removed (`ReaderText`), so `.sp` equals dp. Android's font scale picks only
  the starting size, once, at first launch (`MainActivity`, via
  `ReaderSize.saveStartingSizeOnce`). The toolbar and the Expand button are outside
  that wrapper and still follow the system setting.
- **Saved in SharedPreferences** (`ReaderPreferences.kt`), not DataStore, which
  would be a dependency for one float. The reader draws the saved size clamped to
  the device's range without rewriting it.
- **Tablet means `smallestScreenWidthDp >= 600`**, and gets the iPad maximum and
  starting size. Smallest width, so rotation and split screen do not change it.
- **The pinch moves in 1/6dp steps**, iOS's grid, about half a device pixel.
  Whole pixels (0.38dp) felt stepped on a Pixel (2026-10-14): a size step moves
  every glyph along the line in proportion, so a line's end jumps by far more than
  the step. 1/24 felt no smoother than 1/6 (2026-10-15) and costs four times the
  relayouts.
- **Reading text uses `TextMotion.Animated`** (`ReaderText`), so glyphs sit at their
  exact scaled positions. The default, `Static`, rounds them to whole pixels, which
  makes a pinch move text in jumps however fine the step. Always on, not only during
  a pinch, because switching on release could move line breaks under the anchor.
- **No scroll correction during the pinch; one on release.** Unlike iOS's, the
  release is exact: the point of the row that was under the fingers returns to the
  same height, because Compose reports each row's measured offset.
- **The pinch owns the gesture once a second finger lands.** It reads pointer
  events in the Initial pass and consumes them until every finger lifts. Otherwise
  the list scrolls under the pinch, and lifting the fingers counts as a tap on the
  row (seen on device 2026-10-14).
- **Taps keep the ripple; only the pinch anchor gets the toner wash**
  ([interaction.md](../../docs/interaction.md)).
- **One node per row carries every background** (`ReaderRow`): the card fill, the
  anchor's toner and the ripple, all clipped to one 12dp rounded shape. iOS draws its
  highlight bled outside the row, and doing that here left the ripple on the row's
  own square bounds, a different size and shape from the toner. So the text is inset
  instead: the list's side padding is 8dp short of the gutter and each row adds it
  back. Vertically a row reaches 6dp past its text (the gap it already owns) rather
  than iOS's 8, because two rows' tap targets cannot overlap.
- **The Keep Reading card's gradient reaches `toner` where the preview's veil ends**,
  and is solid below. Run on to the card's bottom edge, as on iOS, the veil's bottom
  showed as a faint rectangle inside the card.

**Not yet verified on a device.** The 17sp-in-dp column of
[tokens.md's Android table](../../packages/design/tokens.md#starting-size-on-android)
is interpolated from AOSP's font-scale tables, not measured.

## Reader toolbar

**Icon buttons, not text** (2026-10-15). The text buttons (Back, A-, A+, ⋯) scaled
with the system Font size. At the largest Font size and Display size they took most
of the width and left the bani title as "…". Icons are dp and do not scale:

- **Back and Controls** are vector drawables in `res/drawable/` (`ic_back`, Material
  Symbol `arrow_back`; `ic_controls`, Lucide `settings-2`). Where they came from and
  how they were imported is recorded in
  [icons.md](../../packages/design/icons.md#android-drawables). The toolbar uses
  `R.drawable.*`, not `AppIcons`' strings through `getIdentifier`, so a missing
  drawable fails the build instead of drawing a blank button. `ToolbarIconsTest`
  checks the names still match `AppIcons`.
- **Smaller and Larger are the letters "a" and "A"**, chosen by the author. They have
  no icons.md rows, because they go away when the Controls sidebar lands. Their
  size is 20dp converted to sp, so like the icons they ignore Font size.
- **Not `material-icons-extended`.** It is a large artifact holding the older Material
  Icons rather than the Material Symbols that icons.md names. Hand-imported
  drawables are what `AppIcons.kt` was generated for.

Content descriptions are "Back", "Smaller", "Larger", "Controls", matching the
iOS button labels.

**Verified on device 2026-10-15**, light and dark, at maximum Font size and
Display size: the bani title shows readable text.

## Tests

```
cd apps/android && ./gradlew :app:testDebugUnitTest
```

JVM unit tests only, JUnit 4 (test-only dependency). No instrumented tests:
SharedPreferences round-tripping across a restart is checked by hand, not by a
test. The same goes for [reader state across recreation](#reader-state-across-recreation).

## Reader state across recreation

Rotation, a theme change, a system Font size change and process death all destroy
and recreate `MainActivity`. **What the person had open comes back**: the open bani,
the scroll position, pauses, pronunciations, ratio and expanded continuations.
Font size, pauses, pronunciations and ratio come back because they are persisted
across launches, in the `reader` SharedPreferences under iOS's `@AppStorage` keys
(`ReaderSettings` in `ReaderPreferences.kt`; [Reader size and pinch](#reader-size-and-pinch)).
Each is written only when the person changes it, so an untouched setting keeps
following its default (`ReaderDefaults`, pinned by `ReaderDefaultsTest`).

- **`rememberSaveable`, not `android:configChanges`.** `configChanges` only covers
  the changes it lists, so process death would still lose state. It also leaves the
  app to handle each listed change itself, including the tablet and size logic,
  which reads `resources.configuration`.
- **The open bani is saved by id** (`App` in `MainActivity.kt`) and looked up with
  `List<Bani>.withId`. An id the corpus no longer has shows the list.
  `OpenBaniTest` covers the lookup. **A stopgap, for as long as the bani list is
  the home screen.** Once the viewer is what the app opens to and journeys keep
  the open tabs ([reading-shell.md](../../docs/requirements/reading-shell.md#four-surfaces-one-of-them-always-visible)),
  the open item comes back from the journey, and this saved id goes with the list.
  The scroll position does not: journeys record the line tapped, not how far the
  reader scrolled, so the saved list state below stays.
- **Scroll restores through `rememberLazyListState`**, which needs two things. The
  rows must have stable keys (`ReaderItem.id`), and the expanded continuations must
  be restored in the same first composition, so the saved index points into the
  expanded list. After a rotation, the same row is at the top. Its offset within
  that row is pixels, so it can shift when lines rewrap at the new width.
  **Verified on a phone 2026-10-15**: the top line stays put through repeated
  rotations in any order, whether it was tapped or scrolled to. iOS passes the
  same test since 2026-10-15 ([ios/README.md](../ios/README.md#state-of-play)).
- **Recreation must be cheap, because rotation waits for it.** Android starts the
  rotation only once the recreated activity has drawn its first frame. Parsing the
  corpus in `App()` held every rotation for 130–150 skipped frames on a phone
  (2026-10-15, `adb logcat -s Choreographer:I`), on the list and the reader alike.
  `Corpus.load` now parses once per process. Afterwards, rotations logged nothing:
  Choreographer reports only stalls of 30 frames or more. The cold launch still
  skips about 185. Measure with that same logcat line
  after any change to what `onCreate` or the first composition does.
- **Transient state stays plain `remember`**: the pinch anchor and its highlight,
  the live pinch size, and whether the Controls menu is open.
- **The open bani, scroll position and expanded continuations last for recreation
  only, not launches.** A cold launch opens the list, and each bani opens with its
  continuations collapsed. Whether a cold launch resumes the last bani belongs to
  the journeys work (docs/requirements/journeys.md), not here.
- **No Compose UI test.** `StateRestorationTester` needs
  `androidx.compose.ui:ui-test-junit4` plus instrumented or Robolectric tests, which
  is a dependency decision that has not been made. Checked by hand instead: with the
  reader scrolled partway, pauses toggled and a continuation expanded, (1) rotate
  both ways, (2) toggle dark theme, (3) change Font size, (4) with Developer options
  > "Don't keep activities" on, switch away and back, (5) `adb shell am kill
  com.shabados.app` while in the background, then reopen from Recents. All five
  passed on a phone 2026-10-15. **Scripted** as
  `bun apps/android/scripts/check-restore.ts`, which drives an attached device
  through adb alone and puts every setting back. Run it when a change touches the
  reader's state, the activity, or the first composition, not on every commit. It
  does not judge how quickly the screen rotates. 10/10 on a phone 2026-10-15.
- **Leave with Home, not Back.** Back closes the reader by design, and a second Back
  closes the app, so the list showing afterwards is correct.
- **Start the app from its icon before testing a return through the icon.**
  `apps/build.sh --run` launches with `am start -n`, which is not the intent the
  home-screen icon sends. In a task started that way, tapping the icon started a
  fresh activity on top, showing the list, while Recents restored correctly. Swipe
  the app away and open it from the icon first, and the icon restores too. Play
  installs are opened from the icon, so users never see this.
- **Swiping the app away in Recents discards saved state**, so it opens to the list.
  That is the cold launch this change leaves alone.

## Content

`app/src/main/assets/banis.json` is generated — regenerate with
`bun run database:export-bundled` from `database/`. Never hand-edit it; scripture
corrections go through the database component's citation-backed review.

Font version, licensing, and the rest of what's bundled and why:
[`apps/README.md#generators`](../README.md#generators).

## Status

Gradle sync **succeeds** (2026-09-01, Android Studio Quail, AGP 9.2.1 + Gradle
9.4.1). `gradlew` and the wrapper jar are generated and checked in, so
`./apps/build.sh android --run` works without Android Studio.

Not yet confirmed: that the APK assembles and runs on the emulator. Sync validates
configuration and compiles nothing.

**Two Android Studio suggestions are deliberately declined.** Do not action them
without reading this first:

- **AGP Upgrade Assistant.** Upgrading AGP requires updating `kotlin` in
  `libs.versions.toml` to whatever Kotlin the new AGP embeds (read
  `kotlin-gradle-plugin` from its POM), because that value is the Compose compiler
  plugin version. The Assistant does not know this and will leave the catalog
  inconsistent. There is also no benefit here: this project exists to keep the
  store listings current, not to track AGP.
- **Migrate to Gradle Daemon toolchain.** Reasonable in principle — Studio and
  `apps/build.sh` both run Gradle, and this stops them spawning separate daemons.
  Deferred because both already use Studio's bundled JBR, so it fixes nothing today
  while adding toolchain criteria and a possible JVM download.

## Versions: inferred where possible, pinned where it matters

`compileSdk` is **not hardcoded**. `app/build.gradle.kts` reads the SDK path from
`local.properties`/`ANDROID_HOME` and picks the newest installed platform, so the
project builds on any machine and survives an SDK update without an edit. This
matters because minor SDK releases exist: the platform here is `android-36.1`, and a
literal `compileSdk = 36` resolves to `platforms/android-36`, which is not installed.

`targetSdk` **is** pinned. It changes runtime behaviour, so inferring it would make
the shipped APK depend on which machine built it.

Jetpack Compose and Material 3, declared through a version catalog
(`gradle/libs.versions.toml`) — the layout Android Studio's wizard generates and
what [developer.android.com/develop/ui](https://developer.android.com/develop/ui)
recommends.

**No `kotlin-android` plugin.** AGP 9 has Kotlin built in and rejects it outright;
`org.jetbrains.kotlin.plugin.compose` is the only Kotlin plugin applied.

**`libs.versions.toml`'s `kotlin` must equal the Kotlin that AGP embeds**, not the
latest release. AGP 9.2.1's POM depends on `kotlin-gradle-plugin:2.2.10`, so the
Compose compiler plugin is pinned to 2.2.10 and the Compose BOM is from the same era
(2025.10.01). Bumping `kotlin` past AGP's own is a compile-time failure.

JSON is parsed with `org.json` from the platform rather than kotlinx-serialization,
which would add a dependency and a second Kotlin-versioned plugin for about twenty
lines of parsing.

**AGP declares a minimum Gradle version, and enforces it.** AGP 9.2.1 requires
Gradle **≥ 9.4.1**, which is what `gradle/wrapper/gradle-wrapper.properties` pins.
Android Studio installed 9.3.0 by default, which no AGP 9.x accepts.

Do not try to infer this from the jars. Checking whether an AGP release references
newer Gradle APIs (`org/gradle/features/binding/*`) looks like it should answer the
question and does not — AGP compares version numbers and refuses regardless. When
changing `agp` in the catalog, let the sync report the minimum.

The Gradle wrapper **is** checked in, pinned to 9.4.1. `apps/build.sh` prefers it
and falls back to system `gradle` only if it goes missing.
