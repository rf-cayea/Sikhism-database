# Design tokens

**THE source of truth for colour and type in the native apps.** Edit the tables here,
then run `node packages/design/scripts/generate-tokens.mjs`. It emits:

- `apps/ios/ShabadOS/DesignTokens.swift`
- `apps/android/app/src/main/java/com/shabados/android/DesignTokens.kt`
- `apps/android/app/src/main/res/values/design_tokens.xml`
- `apps/android/app/src/main/res/values-night/design_tokens.xml`

**Never edit those four** — they are generated and will be overwritten.
`apps/build.sh` regenerates them on every build, so a stale file cannot reach a
build.

## Why markdown rather than JSON

The values are a handful of numbers; the *reasons* are most of what matters, and JSON
has nowhere to put them. A `$comment` array is a workaround, not a home. Here a token
sits next to the sentence explaining it, and the file reads as documentation whether
or not you are about to change a value.

**The generator parses the tables below**, so their shape is load-bearing: a
three-column colour table keyed by token name, and two-column tables for the rest.
Prose around them is free.

## Colour

Light and dark pairs, matching CSS `light-dark()`. **Both platforms resolve them from
the system appearance automatically** — no theme switching code.

Values mirror `apps/web/src/global.css`, which is where this palette was designed —
in OKLCH, then converted, and the `oklch()` comments there are the real provenance.
**The web app is not yet generated from this file, so the two can still drift.**
Closing that means storing OKLCH here, computing hex in the generator, and emitting
web's custom properties too.

| Token | Light | Dark |
| --- | --- | --- |
| background | `#f4f1ee` | `#000000` |
| backgroundBase | `#ffffff` | `#222222` |
| foreground | `#23211f` | `#ffffff` |
| foregroundMuted | `#575552` | `#bebebe` |
| ui | `#2381e0` | `#3c96f7` |
| vishraamHeavy | `#8a472a` | `#ffd493` |
| vishraamMedium | `#13662b` | `#c4eda8` |
| vishraamLight | `#5e438e` | `#decbff` |

### The vishraam colours

**Three weights of pause, coloured by which marker ends the word** — verified against
`apps/web/src/components/line/line.tsx` and the corpus:

| Marker | Weight | Reads as |
| --- | --- | --- |
| `;` | heavy | orange |
| `,` | medium | green |
| `.` | light | purple |

**The markers themselves are always stripped**, whether or not pause colouring is on
— they are editorial notation, not scripture
([display-controls.md](../docs/requirements/display-controls.md#pauses)).

**These were the last display colours defined only in `global.css`.** Moving them here
is what lets a pause render the same in the apps as on the web.

## Opacity

| Token | Value |
| --- | --- |
| toner | `0.12` |
| tonerFaint | `0.04` |

`toner` is a neutral wash: the page background blended this far toward the text
colour. The apps use it for the tap and pinch highlight. `tonerFaint` is the same
wash, lighter. The "Keep reading" card is a gradient from `tonerFaint` at its top to
`toner` at its bottom; tapped, it fills with `toner` and fades back to the gradient,
like a tapped line.

It started as the web's `--toner` (`global.css`, 0.08), was halved to 0.04 on
2026-09-19 because a tapped line read as a filled band, and was raised to 0.12 on
2026-10-10 because at 0.04 the highlight was too faint to follow, especially in
dark mode. **The web is still at 0.08**, so the two now differ.

## Weight

Sant Lipi's `wght` axis runs 100–900, so these are exact axis values rather than the
nine named weights a system font offers. Gurmukhi's thin horizontal strokes and
stacked matras disappear at text weights that suit Latin, which is why the primary
line sits well above regular.

**Secondary fields are lighter than the primary but not by much** — they are still
scripture-adjacent, and dropping them to a true light weight makes them look
disabled rather than subordinate.

| Token | Value |
| --- | --- |
| weightPrimary | `550` |
| weightLatin | `475` |
| weightSecondary | `440` |

`weightLatin` is heavier than `weightSecondary` because Latin at the same axis value
reads lighter than Gurmukhi does — different scripts, different apparent weight.

## Type

`ratioDefault` is the secondary-field size as a fraction of the Gurmukhi size — the
`Ratio` control's starting point, within its 0.4–1.0 range
([display-controls.md](../docs/requirements/display-controls.md#ratio)).

`lineHeightRatio` is the total line box as a multiple of the font size. It is stored
as a ratio, not an absolute, so it holds at every zoom. 1.6 matches the web app's
`line-height: 1.6`. Sant Lipi's own line box is 1.662 × its size, and iOS can only
add space on top of a font's line box, never remove it, so iOS renders 1.662 and is
slightly taller than Android. That difference is accepted.

`defaultSize` is the reader's Gurmukhi size on a phone at the system's default text
size: the L row of [Starting size](#starting-size), which the generator checks.
`listSize` is the bani list's Gurmukhi size. It is separate so that changing the
reader default does not resize the list.

**The zoom range is per device** (2026-10-10). `minSize` applies everywhere.
`phoneMaxSize` is the iPhone's maximum and `padMaxSize` the iPad's: an iPad's wider
screen fits larger text before lines shrink to a word or two. The apps pick the
maximum for the device they run on. Android does not yet tell phones from tablets,
so it uses `phoneMaxSize`.

| Token | Value |
| --- | --- |
| ratioDefault | `0.6` |
| defaultSize | `28` |
| listSize | `20` |
| lineHeightRatio | `1.6` |
| minSize | `16` |
| phoneMaxSize | `88` |
| padMaxSize | `132` |
| startingSizeFactor | `1.65` |
| padStartingSize | `40` |

## Starting size

The reader's size the first time the app opens
([display-controls.md](../docs/requirements/display-controls.md#starting-size)).

**On a phone: `startingSizeFactor` × the system's Body text size, rounded to the
nearest even number, kept within `minSize`–`phoneMaxSize`.** Even, because A± moves in
steps of 2, so the starting size sits on the same grid as every tap after it. The
app reads the Body size from iOS at first launch rather than from this table, so a
change Apple makes to its sizes flows through without an edit. **On iPad:
`padStartingSize`, whatever the setting.** Generated for iOS only for now. Android
does not read these yet.

The table shows what the rule gives. *iOS body text* was measured on an iPhone
(2026-10-10) with `UIFont.preferredFont(forTextStyle: .body, compatibleWith:)`.
*Size* is derived from it, and the generator fails if any row disagrees with the
rule, so edit the factor and then the table, not the table alone.

| Setting | iOS body text | Size |
| --- | --- | --- |
| xS | 14 | `24` |
| S | 15 | `24` |
| M | 16 | `26` |
| L (default) | 17 | `28` |
| XL | 19 | `32` |
| XXL | 21 | `34` |
| XXXL | 23 | `38` |
| AX1 | 28 | `46` |
| AX2 | 33 | `54` |
| AX3 | 40 | `66` |
| AX4 | 47 | `78` |
| AX5 | 53 | `88` |
