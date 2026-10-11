# Shabad OS — iOS

**This is the platform app.** It began 2026-09-01 as a store-retention scaffold —
just enough to keep the App Store listing current while the real app was designed.
**Superseded 2026-09-04**: one app across three shells, mobile ships first
([ADR-0014](../../docs/architecture/decisions/0014-one-app-three-shells.md),
[plan.md](../../docs/plan.md#step-1-in-detail--mobile)). Logic added here is no
longer a liability to be minimised, and `packages/gurmukhi` belongs in it rather
than being kept out.

See [`apps/README.md`](../README.md) for what both platforms share and how it is
generated. This file is iOS-specific.

```
./apps/build.sh ios --run      # build, boot a Simulator, install, launch
./apps/build.sh ios            # build only
open apps/ios/ShabadOS.xcodeproj   # then ⌘R
```

`--run` picks the first available iPhone simulator, boots it, opens Simulator.app,
then installs and launches. Nothing is published or signed — it is a local preview.
**Run it from a real terminal, not Claude Code's `!` prefix** — `xcrun` needs a
cache it cannot write inside the agent sandbox.

No package manager beyond Swift Package Manager (for the local `packages/gurmukhi`
dependency), no network, no permissions, no telemetry. Sources are picked up by a
file-system-synchronized group, so adding a `.swift` file under `ShabadOS/` needs no
project edit.

## Store identity

| | |
| --- | --- |
| Bundle id | `com.shabados.app` |
| Display name | Shabad OS |
| Icon | `brand/assets/lotus.svg`, via the legacy app's 1024px export |
| App Privacy | **Data Not Collected** — true: no network, no SDKs |

Where the bundle id came from, why changing it forfeits the listing, the
Sentry/PostHog reduction, and telemetry's ADR status are the same on both platforms
— see [`apps/README.md#store-identity`](../README.md#store-identity).

App Store Connect app id `1510358282`. It had never been released to the App
Store before this app: the legacy Expo app reached TestFlight only (versions
0.1.0–0.2.0, builds up to 71, all expired), so replacing it stranded no users. The
legacy beta-channel listing, *Shabad OS (Next)* (`6497795055`,
`com.shabados.next.app`), was likewise never released and is unused.

## Versions

`MARKETING_VERSION` is what users see; `CURRENT_PROJECT_VERSION` (the build number)
is a counter App Store Connect uses to tell uploads apart. Both are in
`project.pbxproj`, which cannot hold a comment Xcode will keep, so the reasoning is
here:

- **Started at `0.3.0` (72).** The legacy app's TestFlight builds reached `0.2.0`
  build 71. App Store Connect rejects a version that is not higher than one it has
  approved, and a build number already used under the same version. Continuing from
  there avoids both, and `0.3.0` is also Android's first production version.
- **Every upload needs a higher build number**; every App Store release a higher
  version. Bump them in Xcode (target → General → Identity) or in both build
  configurations of `project.pbxproj`.
- The version in App Store Connect's *Prepare for Submission* entry must equal
  `MARKETING_VERSION`, or the uploaded build cannot be selected for it.

## Signing

Signing is Xcode's **automatic signing with a cloud-managed distribution
certificate**. The certificate that proves a build came from this team stays with
Apple, and Xcode signs with it on behalf of whoever is signed in. There is no key
file or password to keep, unlike Android's upload keystore. Being able to release
comes down to holding the right role in the team.

| | |
| --- | --- |
| Team ID | `VN2W54FQE6`, set in `project.pbxproj`. Not a secret: it is in every signed build |
| Team name | "Shabad OS Team" in App Store Connect; Xcode lists it by its entity name, "Bhajneet Kohli" |
| Enrolled as | Organization |
| Membership renews | 2027-03-08 — if it lapses, the app is removed from sale |

**Custody.** Uploading with a cloud-managed certificate needs the **Admin** (or
Account Holder) role. @bhajneet and @harjot1singh hold Admin. To release, ask one of
them for access, or for the Admin role. The **Account Holder** is the Apple Account
team@shabados.com, held by @bhajneet and @harjot1singh. It is the only account that
can renew the membership and accept Apple's updated agreements. Until a new
agreement is accepted, Certificates, Identifiers & Profiles is locked for everyone
(last accepted 2026-10-10). Sign in to Xcode with a personal Admin account, not this
one. That keeps the team's most powerful account off build machines, and the
procedure is then the same for every Admin.

## Releasing

1. **Rebuild `packages/gurmukhi`** from a real terminal:
   `cd packages/gurmukhi && mise run apple`. `apps/build.sh` only builds it when the
   xcframework is missing, not when it is out of date. A stale one fails to compile
   once the app calls a function added since, or silently ships old behaviour.
2. **Bump the version and build number** (see [Versions](#versions)).
3. **Check for warnings, and deal with each one before archiving.** A warning is
   how the blank controls button was found, after it had been showing for weeks.
   Look in three places:
   - the output of `./apps/build.sh ios` (the icon generator prints `WARNING:` for a
     Lucide icon with no imageset — it draws blank);
   - Xcode's issue navigator (⌘5, or click the ⚠ count in the toolbar) after a
     build;
   - the run console (⇧⌘C) while using the app on a device — e.g. `No symbol named
     '…' found in system symbol set` is a blank icon.
4. In Xcode, sign in under Settings → Apple Accounts with an Admin's Apple Account.
   Then choose *Any iOS Device* as the destination, and Product → Archive.
5. In the Organizer that opens: Distribute App → App Store Connect → Upload.
6. In App Store Connect, open the version in *Prepare for Submission*, select the
   build once processing finishes, and Add for Review.

`ITSAppUsesNonExemptEncryption = NO` is set, so uploads skip the export-compliance
question. That is true while the app makes no network connections. Revisit it when
anything networked lands.

`ShabadOS/PrivacyInfo.xcprivacy` declares the one "required reason" API the app uses
(UserDefaults, via `@AppStorage`) and no collected data. Keep it in step with the
App Privacy answers in App Store Connect (**Data Not Collected**).

## `packages/gurmukhi` in this app

**Wired in and in active use** — pause colouring and both pronunciation schemes go
through it, not a local reimplementation. See `Bani.swift` (`detect`/`remove` for
vishraam ranges) and `Transliteration` (`transcribe`, cached per line/scheme, only
computed on demand). The Xcode project already carries a local Swift package
reference to `../../packages/gurmukhi` — nothing further to add there.

**Simulator builds are Apple Silicon only.** The xcframework has no Intel (x86_64)
Simulator slice, so the project sets `EXCLUDED_ARCHS[sdk=iphonesimulator*] =
x86_64`. Without it, any build for "any iOS Simulator" (including `apps/build.sh ios`
without `--run`) tries to link x86_64 and fails. Device builds and App Store
archives are arm64 only and are unaffected. To support Intel Macs, add
`x86_64-apple-ios` to the gurmukhi Apple build rather than removing this setting.

Building it is not part of `./apps/build.sh` yet when the xcframework is missing;
see [`apps/README.md`](../README.md#packagesgurmukhi-in-the-apps) for the
`mise run apple` command and why it cannot run inside an agent sandbox.

## Scope

**Step 1** ([plan.md](../../docs/plan.md#step-1-in-detail--mobile)) is: the reader
in Classic, Saral, and Reader modes; the full Variorum; zoom, ratio, weight; tabs
and journeys; the Library; bookmarks, tracking, and goals; the Tracker.
Search, Presenter mode, presets, the year in review, and anything networked are out
for now.

**Built so far:** a flat bani list, one reader mode (not yet Classic/Saral/Reader —
[reading-shell.md](../../docs/requirements/reading-shell.md) is not yet read against
this view), pause colouring (on by default), both pronunciation schemes as toggles
(off by default, per the Variorum defaults), the Ratio control, and font sizing by
toolbar buttons (2pt a tap) or a two-finger pinch. The starting size is chosen once,
on first launch, from the device and its Text Size setting
([display-controls.md](../../docs/requirements/display-controls.md#starting-size)),
and the zoom range is per device (16–88 iPhone, 16–132 iPad; both in
`packages/design/tokens.md`). Font size persists across launches (`@AppStorage`);
pause and pronunciation
toggles live in a menu stand-in for the Controls sidebar
([reading-shell.md](../../docs/requirements/reading-shell.md#the-controls-sidebar)),
which does not exist yet and should be deleted once that surface lands.

**Not yet built:** tabs and journeys, the Library, bookmarks, tracking, goals, the
Tracker, and the mode/weight controls.

## Deliberately absent

- **A shared core** — no session/search/navigation core on any platform yet
  ([ADR-0010](../../docs/architecture/decisions/0010-shared-core-across-platforms.md)
  Needs discussion; see [`apps/README.md`](../README.md#what-is-shared-and-how)).
  This app talks to `packages/gurmukhi` directly.
- **`NSLocalNetworkUsageDescription`.** Connect will need it
  ([ADR-0011](../../docs/architecture/decisions/0011-distribution-channels.md), also
  Needs discussion), but declaring a permission before a feature uses it invites
  review questions.

Neither ADR blocks Step 1 — mobile's in-scope list above has no networking and no
shared core in it. Don't build against either ADR's options ahead of a decision.

## Content

`ShabadOS/Resources/banis.json` is generated — regenerate with
`bun run database:export-bundled` from `database/`. Never hand-edit it; scripture
corrections go through the database component's citation-backed review process.

Font version, licensing, and the rest of what's bundled and why:
[`apps/README.md#generators`](../README.md#generators).

## State of play

**Verified working (2026-09-07):** builds, installs, and runs in Simulator. Bani
list, reader, Sant Lipi rendering, light/dark, pause colouring, both pronunciation
schemes, toolbar A−/A+ sizing.

**Verified on a real iPhone (2026-10-10):** Release configuration, version 0.3.0
(72), signed with the team's development certificate. No problems found. This is the
first device run; everything before it was Simulator only.

**Pinch-to-zoom, reworked and verified on iPhone and iPad (2026-10-10).** The font
size changes live, in 1/6pt steps, at sensitivity 0.75 (the text grows about 1.68×
when the fingers spread to twice their distance). The row under the fingers is
highlighted, and is put back where it was **once, when the fingers lift**. A pinch
that starts between rows anchors to the one nearer the middle of the screen.

**Do not correct the scroll position during the pinch.** It used to be corrected on
every step, and at large sizes the reader jittered badly: each step re-wrapped lines
and the lazy list re-estimated the heights of rows off screen, so every correction
was computed from guesses and the position bounced. Removing the per-step
correction removed the jitter entirely. Previewing by drawing the reader scaled
(`scaleEffect`) and applying the size on release was also tried: no jitter, but the
size change was too hard to judge while pinching. The reasoning is also in the doc
comment on `pinchToZoom`.

**Known limitation:** the row's return on release is accurate to within **one line
height**, because `scrollTo(_:anchor:)` aligns a row's own anchor with the viewport's
and cannot express "40% into this row at 25% down the screen". Exact positioning
needs iOS 18's `ScrollPosition` with offsets — available at this app's deployment
target (26.0, [interaction.md](../../docs/interaction.md#platform-targets)) but not
yet used.

**Known inconsistency to resolve:** this app persists reader font size
(`@AppStorage`); Android does not (in-memory only). Neither is obviously wrong but
they should agree — likely persist on both, since an operator who sets a comfortable
size should not have to redo it.
