<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# Google Play listing

Everything the Play listing shows comes from the repository, in every language, and is uploaded
with one command:
- **the text**: title, short description and full description, in every language the app has;
- **the eight phone screenshots**: one continuous panorama, 8640 × 1920 px, cut into 1080 × 1920
  frames. Sheets of paper fall across the seams, joined by an Ink thread, and the headlines
  alternate between the top and the bottom of the frame;
- **the feature graphic**, 1024 × 500: the promise on the left, a stretch of the same paper trail
  on the right.

The graphics' design is the *Store screenshots* page of the Figma file, and the reasoning is in
*Panoramic Screenshots 0.3*. These scripts are what produce the files: Figma cannot be driven from
a build.

## The source: `store_listing.xml`

All the copy lives in `app/src/main/res/values*/store_listing.xml`, so it is translated with the
rest of the app. The app itself never reads these strings.

| Strings | Used for | Limit |
|---|---|---|
| `store_title` | Title | 30 characters |
| `store_short_description` | Short description | 80 characters |
| `store_full_description` | Full description, `\n` between lines | 4000 characters |
| `store_screenshot_NN_headline`, `store_screenshot_NN_supporting` | Screenshot NN | Must fit two lines |
| `store_feature_headline`, `store_feature_supporting` | Feature graphic | Must fit two lines |

The screenshot strings have the same names as the Figma variables' Android code syntax.

`locales.json` maps each `values-*` folder to its Play locale, and says which languages get their
own graphics: only those written in the Latin alphabet, the only one the brand's fonts cover.
Google Play shows the default language's graphics to the rest.

## Writing the files

```sh
cd scripts/store
npm install
npx playwright install chromium   # once, for render.mjs

node listing.mjs                  # the text, every language
node listing.mjs --check          # only check the limits
node render.mjs                   # the graphics, every language marked "graphics"
node render.mjs --locale es,de    # only these
node render.mjs --preview         # also the whole panorama and the feature graphic in build/store-screenshots
```

Both write fastlane's layout, under `fastlane/metadata/android/<play locale>/`:
- `title.txt`, `short_description.txt`, `full_description.txt`;
- `images/phoneScreenshots/01.png … 08.png` and `images/featureGraphic.png`, 24-bit PNG without
  transparency, as Play requires.

`--out <dir>` writes somewhere else. `CHROMIUM_PATH` points Playwright at a browser already
installed.

### What render.mjs reads besides the copy

| Input | Where | Notes |
|---|---|---|
| Layout | `layout.json` | Positions, angles, sizes and colors. Phone `x` is inside its own frame; sheets and the thread are in canvas pixels; angles are clockwise. `featureGraphic` holds the banner's own layout, and reuses frames 01 and 04's captures. |
| Fonts | `app/src/main/res/font` | DM Serif Display for headlines, DM Sans for the rest: the app's own files. |
| Captures | `captures/<language>/NN.png` | The app's screen for each frame, portrait, any resolution (it fills the screen from the top). |

| Frame | Text | Screen |
|---|---|---|
| 01 | Top | Home |
| 02 | Bottom | Scanner |
| 03 | Top | Viewer, a clean scanned page |
| 04 | Bottom | Search results, with page and snippet |
| 05 | Top | A folder, with colors and icons |
| 06 | Bottom | Viewer in night mode, text selected |
| 07 | Top | A link's preview |
| 08 | Bottom | Document details |

A frame with no capture in its language uses the English one, and with no English one, a labelled
placeholder. The script says which frames fell back.

## Uploading

From the repository's root, with Ruby and Bundler:

```sh
bundle install
bundle exec fastlane store_text   # writes and uploads the text only
bundle exec fastlane store        # writes and uploads the text, screenshots and feature graphic
```

Neither touches a release: no app bundle and no release notes are uploaded. Screenshots are only
re-uploaded when they changed.

Uploading needs a Google Play service account with access to the app, and its JSON key. Point
`PLAY_STORE_JSON_KEY` at the key, or save it as `fastlane/play-store-key.json`, which git ignores.

Languages are only ever added or updated, never removed: a language in Play that the repository
does not have keeps what it had. So do graphics: if a non-Latin language still shows screenshots
from an older listing, delete them once in Play Console and it falls back to English.

## Rules the scripts enforce

Each script exits with 1 when something is wrong, so a bad listing cannot reach Play unnoticed.
- **Text must fit Play's limits**, counted in characters. A language with only some of its listing
  strings is an error; one with none is skipped. English must have them.
- **Copy must fit the graphics.** A screenshot headline gets two lines at 96 px and may shrink to
  80 px; its supporting line two lines at 40 px, down to 34 px. The feature graphic's headline gets
  two lines at 60 px, down to 48 px, one sentence per line when each fits. A language whose copy
  still does not fit is not drawn.
- **A language without `store_listing.xml` is skipped**, and one with missing graphics strings is
  an error.

## Changing the design

Change Figma first, then the matching numbers in `layout.json`. The variables in Figma's
*Store copy* collection are a preview of the English strings; `store_listing.xml` is the source.
