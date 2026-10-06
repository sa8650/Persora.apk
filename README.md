# Persora for Android

A **native** Kotlin + Jetpack Compose client for [Persora](https://persora.pages.dev) — your private space for documents, subscriptions, contacts, medical records, tasks, reminders, alarms, business cards and more.

- **Same account, same vault.** The app talks to the existing Cloudflare Pages Functions API at `https://persora.pages.dev/api`, which means the same Supabase database, the same R2 file storage and the same session cookies as the website. Nothing is duplicated and no Supabase/R2 keys ship in the APK.
- **Not a WebView.** Every screen is Compose. The website's theme tokens (`src/index.css`, `src/persora-theme.css`) are ported to `ui/theme`, and the boot animation is a faithful Compose port of `PersoraBootScreen.tsx` (three orbiting rings of icons around the ShieldCheck core).
- **Phone + tablet.** Bottom navigation on phones, a navigation rail on medium widths, and a permanent sidebar (the website's left nav) on expanded widths.
- **Member features only.** The admin console stays on the website — admins still sign in here as regular members.

## Project layout

```
app/src/main/java/app/persora/android/
├── PersoraApp.kt              Application + AppContainer (prefs, cookie jar, API, repository, alarm scheduler, Coil loader)
├── MainActivity.kt            Single FragmentActivity (needed for BiometricPrompt) hosting PersoraRoot
├── core/
│   ├── network/               OkHttp ApiClient, persistent encrypted cookie jar (persora_session), ApiException
│   ├── storage/               EncryptedSharedPreferences wrapper, JSON disk cache for offline reads
│   └── util/                  Dates (schedule math mirrors Workspace.tsx), Files, Qr (ZXing), Cards (masking)
├── data/
│   ├── model/Models.kt        Kotlin mirrors of src/types.ts (snake_case rows → camelCase models)
│   ├── model/Sections.kt      SECTION_DEFINITIONS + NAV_GROUPS from src/data.ts (single source for forms, previews, icons)
│   ├── api/PersoraApi.kt      Every /api route the member web app uses (auth, vault, folders, upload, contacts, cards,
│   │                          medical, timeline, shares, comments, notifications, billing, smart-scan, ringtones, export)
│   └── repository/            SessionManager (boot → onboarding → auth → lock → signed-in) and VaultRepository (StateFlows,
│                              cache, optimistic updates, alarm re-sync)
├── alarms/                    AlarmManager scheduling for reminders/alarms (exact + boot/timezone re-schedule),
│                              full-screen ringing activity, foreground sound service streaming the chosen ringtone
└── ui/
    ├── boot/                  Boot screen (orbiting rings, breathing core, "Preparing your personal vault…")
    ├── onboarding/            4-page intro carousel → personalise → protect (notifications, exact alarms, app lock)
    ├── auth/                  Sign in (email or 7-digit Persora ID) / create account, biometric lock screen
    ├── navigation/            Routes, adaptive WorkspaceShell (top bar, nav, polling, deep links), tablet Sidebar
    ├── components/            Cards, pills, buttons, inputs, date/time pickers, QR dialog
    ├── dashboard/             Home (greeting, metrics, coming-up, recently updated, favourites, getting-started checklist),
    │                          Spaces grid, More menu, Notifications
    ├── vault/                 SectionScreen (folders, search, filters, notes tabs, wallet-card visuals, finance totals),
    │                          ItemDetailScreen (file preview/open/share, QR, sharing), ItemEditorScreen (dynamic form from
    │                          Sections, Smart Scan, file/camera upload with progress, card masking, reminder/alarm editors)
    ├── contacts/              List with duplicate detection + merge, detail (call/SMS/email/save to phone, vCard QR, share), editor with photo
    ├── medical/               Records list with follow-ups, editor with file upload, linked records, optional reminder
    ├── timeline/              Life timeline (auto + manual milestones, attachments)
    ├── shared/                Shared with me / by me, permissions, comments
    ├── businesscards/         Card manager, 4 styles, editor, public card viewer with QR/report
    ├── billing/               Storage usage, plans, manual payment checkout, history
    ├── settings/              Profile, app lock, password, Android permissions, export, legal, sign out / delete
    └── search/                Global search across records, people, medical, cards, timeline
```

## Building

Requirements: **Android Studio Ladybug (2024.2) or newer**, JDK 17, Android SDK 35.

```bash
git clone <this repo> PersoraAndroid
cd PersoraAndroid
# Open in Android Studio → let Gradle sync → Run ▶ on a device/emulator (API 26+)
# or from the command line:
./gradlew :app:assembleDebug
```

> The sandbox this project was authored in has no Android SDK, so the code has not yet been compiled. Expect a handful of
> small compiler nits (an import or a named-argument tweak) on the first sync — the architecture, API contract and
> screens are complete. Fix-forward in Android Studio; nothing structural should need to change.

### Configuration

Both values live in `app/build.gradle.kts` as `buildConfigField`s:

| Field | Default | Purpose |
|---|---|---|
| `API_BASE_URL` | `https://persora.pages.dev/api` | Pages Functions API (same Supabase + R2 as the website) |
| `WEB_ORIGIN`   | `https://persora.pages.dev`     | Used for public card links and legal pages |

Point them at a preview deployment to test against a staging stack. Release builds should also enable `minifyEnabled`
and add a signing config.

### Server-side notes

The API already does everything the app needs, but two things are worth knowing:

1. **Cookie auth.** The API sets `persora_session` as an HttpOnly cookie with `Path=/api`. The app stores it in an
   encrypted cookie jar and replays it on every request, exactly like the browser. Logging out on the web does not log out
   the phone (and vice versa) — sessions are independent, 7-day each.
2. **CORS/Origin.** OkHttp requests carry no `Origin` header, so the existing CORS logic treats them as same-origin. If you
   later add an origin allow-list, keep requests without `Origin` allowed, or add an `X-Persora-Client: android` header check.

## Feature parity checklist

| Website feature | Android |
|---|---|
| Boot animation | ✅ Compose port |
| Sign in / register / 7-digit login ID | ✅ |
| Dashboard (metrics, coming up, recent, favourites) | ✅ + getting-started checklist |
| 13 vault sections with dynamic forms | ✅ driven by `Sections.kt` |
| Folders (per section), favourites, pins | ✅ |
| File upload (25 MB) / view / share | ✅ via `/api/upload` + `/api/file` |
| Smart Scan | ✅ |
| Notes / tasks / reminders / alarms | ✅ + real Android alarms with ringtone + full-screen ringing |
| Wallet cards (masked) | ✅ |
| Personal finance totals | ✅ |
| Contacts (photos, duplicates, merge, vCard QR) | ✅ + call / SMS / save to phone |
| Medical records (files, follow-ups, links) | ✅ |
| Life timeline | ✅ |
| Sharing (documents, contacts, cards), permissions, comments | ✅ |
| Notifications bell | ✅ 30-second polling |
| Business cards (4 styles, public link, QR, report) | ✅ |
| Storage & billing, manual payments | ✅ |
| Profile, password, export, delete account | ✅ |
| Global search | ✅ |
| App lock (biometric / device credential) | ✅ Android-only |
| Admin console | ❌ web only (by design) |

## Onboarding research

See `docs/ONBOARDING_RESEARCH.md` for the Mobbin-pattern review that shaped the intro carousel → personalise → protect →
sign-up → guided-first-actions flow.


## Changelog — v1.1 (post-test fixes)

| # | Reported | Fix |
|---|----------|-----|
| 1 | Profile photo not synced / no avatar editing | New `UserAvatar` composable renders the same scheme as the web (`emoji:`, base64 data URL photo, DiceBear Adventurer SVG via Coil-SVG, or initials) in the top bar, sidebar and Settings. Settings → **Profile photo** lets you upload a photo (center-cropped, downscaled to fit the API's 80 000-char limit), pick an emoji, a DiceBear avatar, or revert to initials; saved through `PATCH /profile`. |
| 2, 5 | Bottom tabs returned to the previous screen (Notifications / Academics) | `navigateTop` now pops to the tab root without `saveState/restoreState`; stale snackbars are dismissed on navigation. |
| 3 | "The coroutine scope left the composition" error | Cancellation is no longer treated as an error (`runCatchingSafe`, repository `guard`, snackbar filter). |
| 4 | Greeting ignored time of day | Greeting is computed in the member's profile time zone (morning 5–11, afternoon 12–16, evening 17–20, night otherwise) with a rotating second line; refreshes every minute. |
| 6 | Alarms never rang | Manifest lacked `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (service start threw and nothing rang). Added them + `WAKE_LOCK`, a ringtone fallback if the service can't start, next-occurrence resync after firing, and a Settings row for Android 14 full-screen-intent permission. Ringtone **preview** button in the editor (`RingtonePreview`). |
| 7 | FAB labels too long | All add buttons now read **Add**. |
| 8 | Notes editor didn't match the web | New `NotesEditorScreen` (kind picker Note/Task/Reminder/Alarm, due-date quick picks, reminder quick picks, weekday chips + Every day/Weekdays/Weekends, one-time date disabled when repeating, ringtone + preview, enabled switch, live "Next ring", details ≤ 5000 chars). Metadata matches `TodoEditorDialog` exactly. |

New dependencies: `io.coil-kt:coil-svg`, `androidx.exifinterface:exifinterface` (sync Gradle after pulling).

## Changelog — v1.2

- **Home**: removed the Preferences button and the "Your personal space · date" line; greeting + single Add button.
- **Calls** (new): `calls/` package — `Calls` (Telecom helpers, SIM options, default-dialer role), `CallManager`, `PersoraInCallService` (InCallService), `InCallActivity` (Persora's own in-call screen: mute / keypad / speaker / hold / Bluetooth, answer & decline, duration), `CallLogStore` (own history + system call log). `ui/calls/CallLogScreen.kt` is the "Recents" page with dialpad FAB, All/Missed filter, call-back, and "Use Persora's in-call screen" set-up card (requests `ROLE_DIALER`). `rememberCaller()` powers every Call button: asks for phone permission once, shows a **Choose SIM** sheet on dual-SIM phones (skipped when a default SIM is set in Android settings), then places the call directly via `TelecomManager.placeCall` — no external dialer. Manifest: `CALL_PHONE`, `READ_PHONE_STATE`, `READ_CALL_LOG`, DIAL/tel intent filters, `BIND_INCALL_SERVICE` service. Calls lives under **More → Calls** and the "Recent calls" chip on Contacts.
- **Settings**: profile editor (photo · name · time zone) is collapsed behind an **Edit profile** button; it closes after saving.
- **Toasts**: Material snackbar replaced by `ui/components/Toast.kt` — floating dark card with success/error/info icon, auto-dismiss, tap or swipe-down to dismiss, newest message replaces the previous one. Legacy `notify(message, isError)` calls are classified automatically.
- Hardening: `Call.Details.callDirection` guarded for API < 29; `ScheduleReceiver` never crashes on ring; cancellation never surfaces as an error.

> If Persora is **not** the default phone app, calls still go out directly (no dialer screen) but Android shows the system in-call UI; tap **Set up** on the Calls page to switch to Persora's screen.

## Changelog — v1.3

- **Bottom drawers**: contact view and every task/note/reminder/alarm view now slide up as a full-height drawer (`DetailSheet`) over the list instead of pushing a page. Deep links / call-log "Open" still use the routed page.
- **Settings**: single pencil icon toggles the profile editor (photo · name · time zone); extra button and label removed.
- **Offline alarms**: alarms are scheduled straight from the on-device cache on launch (`loadFromCache` → `AlarmScheduler.sync`), on boot/time-change (`BootReceiver`), and use `setAlarmClock` (exact, Doze-proof). `USE_EXACT_ALARM` is declared; Android 12 users get an "Exact alarms" row in Settings if the toggle is off. No network is needed for an alarm to ring.
- **Offline-first sync**: on sign-in `refreshAll()` downloads items, contacts, cards, medical records, timeline, notifications, shares, folders, ringtones and storage into app-private JSON. Network failures while polling no longer show toasts — the cloud-off icon appears and cached data stays on screen. Background sync: light sync (items, notifications, contacts) every 30 s, full sync every 2 min and on resume; **pull down on any list** to force a full sync ("Syncing…" chip). Settings → "Offline copy" shows last sync time. (Edits still require a connection; they're sent to the website's API immediately.)
- **Tasks & Notes**: the intro paragraph was removed.
- **New look**: lavender page gradient (`PersoraGradients.page`), indigo-violet accent, translucent glass top bar, **floating pill tab bar** with soft shadow, glass cards (translucent white + hairline border + tinted shadow, 20 dp radius), app-store style Spaces grid with large squircle icons and minimal text.

## Changelog — v1.4

- **Build fix**: `ApiClient.kt` KDoc contained `/api/*` — Kotlin nests block comments, so `/*` inside the comment swallowed the file until the `*/*` Accept header. Comment rewritten; a repo-wide scan confirms no other nested comments.
- **Frosted cards**: every `PersoraCard` is now 40 % white (60 % transparent) with a top-light sheen and hairline border.
- **True frosted glass** for the top bar and tab bar via `dev.chrisbanes.haze` (blurs scrolling content on Android 12+, translucent fallback below).
- **Drawer everywhere**: `Details.openItem()/openContact()` (global) opens the record view as a bottom drawer from any screen (sections, dashboard, search, shared, timeline, call log, notifications, deep links). No status-bar gap inside the drawer; a short swipe settles at half height instead of closing.
- **iPhone-style tab bar**: filled icon when active, outlined otherwise, 10 sp labels, grey/violet tints, subtle scale.
- **Spaces = app drawer**: 4-per-row squircle app icons with gradient, white glyph, red count badge and label — no cards.

## Changelog — v1.5

- **Notes editor rebuilt as a phone-notes app** (`ui/vault/NotesEditorScreen.kt`): plain notes open full-bleed with a borderless large *Title* field, a meta line (`6 October 12:16 AM | 0 characters`), a free-form *Start typing* body, tag chips in a bottom strip (type a tag and press done / comma), and a slim top bar with back · favorite · share · save (check). Leaving with back auto-saves when the note has content (title falls back to the first line); empty notes are discarded silently.
- Task / reminder / alarm editors keep their structured form, but the **kind picker row and the "Write a note / Add a task / Set a reminder / Set an alarm" intro header are gone** — every editor opens directly for its own kind.
- **Spaces drawer badge** is now a soft frosted pill tinted in the space's own colour instead of a red iOS-style count.
- **Home top-4 metric cards are colourless liquid glass** (`LiquidGlass` in `ui/components/Common.kt`): transparent pane that shows the page gradient behind it, specular highlight, light-catching gradient edge and a soft shadow.
- **Sync is now fully background**: the dashboard spinner row and the shell's global "Syncing" state are gone, the poll loop and resume hook run on the repository scope, and `VaultRepository.publish()` only emits/stores lists that actually changed — so no visible "reload" a few seconds after launch. Pull-to-refresh is the only place a spinner appears, and the NavHost is never re-created (removed the `movableContentOf` wrapper swap).


## Changelog — v2.0 · Bento design system (fresh UI)

The lavender / liquid-glass theme is gone. Every screen now uses a 1:1 port of the shadcn **Agent Bento Grid** look
(`ui/theme/Bento.kt`, `ui/components/Common.kt`), following system light / dark mode.

- **Tokens** — `Bento.*` (bg, fg, card, panel, muted, mutedFg, subtleFg, border, borderStrong, ring, primary, primaryFg, danger…)
  for light (`neutral-50` page · white cards · `rgba(0,0,0,.08)` ring) and dark (`neutral-950` page · `neutral-900` cards · `white/5` ring).
  Primary is **monochrome** (shadcn neutral); colour lives only in `Accents` (cyan/sky/violet/fuchsia/emerald/amber/rose/… 400-500-600)
  used by 3-D icon tiles, bars, pills and dots. `Tones.*` are now derived from accents and switch with the palette.
- **Primitives** — `BentoCard` (rounded-20, 1 px ring + 2 px shadow), `FeatCard` (title + description + recessed `Panel`),
  `IconTile` (gradient 400→600, darker border, inset highlight), `ToneIconBox`, `Pill` (`bg-x/15` mono uppercase),
  `MonoLabel`, `CountBadge`, `ProgressTrack` (gradient bar with sheen), `LiveDot`, `HairLine`, `SegmentedTabs` (shadcn Tabs),
  `Modifier.dotGrid()` / `Modifier.hatch()` backgrounds, shadcn-style `PrimaryButton` / `QuietButton` / `SoftButton`.
- **Typography** — Roboto for copy; `FontFamily.Monospace` for all data labels, counters, tags and nav labels
  (`MonoCaption`, `MonoBody`, `MonoStat`; `labelMedium` / `labelSmall` are mono).
- **Chrome** — flat top bar with hairline, flat bento bottom bar (icon + mono uppercase label, 5 tabs), tablet rail / sidebar on the card surface.
  Haze / blur dependency removed.
- **Home = real bento grid** fed by vault data: *Activity* (hatched weekly bar chart + stat tiles), *Coming up*
  (activity-feed rows with status tiles), *Spaces* (tool-inspector tiles with counts + share bars), *Storage*
  (namespace bars + live-sync dot), *Sync pipeline* (animated node graph: phone → router → API → DB / R2),
  *Recently updated*, *Favorites*, plus the getting-started checklist. 2-column on tablets.
- **Spaces** page = grid of tool tiles; **More** / **Notifications** restyled; boot & onboarding orbit 3-D tiles;
  in-call screen uses the graphite surface; launcher / splash recoloured; `values-night` resources added.

## Changelog — v2.1

- **Brand primary**: `Bento.primary` is now Persora web blue (#1A73E8 light · #8AB4F8 dark) with `Bento.primarySoft` and `Accents.brand` (4285F4 / 1A73E8 / 174EA6) — buttons, active nav tab/rail/sidebar, segmented-tab labels, chart bars, progress and FABs use it.
- **Drawer → editor**: tapping Edit inside the item / contact drawer closes the drawer before opening the editor.
- **Spaces tiles**: icon tile 28 → 34 dp (+20 %).
- **Home**: four animated KPI cards after the greeting (Token-Monitor style: mono label, counting value, Δ vs previous 7 days, self-drawing sparkline with pulsing end dot) for Records / Files / People / Due; *Latest changes* is now the Agent-Bento **Activity Feed** carousel (centred active row, shrinking/fading neighbours, auto-advance every 2.2 s, position dots, spinning "running" tile for open tasks).
- **Storage card** moved from Home to **Storage & billing** (top of the page).
- **Search boxes**: new compact `SearchField` (40 dp, rounded-12) placed first on Section / Contacts / Medical / Search pages; page intro paragraphs removed; every page now uses the Home padding (14 dp top / sides).
- **Notifications**: reminders / alarms that fire on the device are recorded as `schedule:<itemId>:<time>` notifications (same as the web) and merged with server sharing notifications, cached offline; mark-as-read covers both.
- **App icon / splash**: bento 3-D tile in brand blue gradient (4285F4 → 1A73E8 → 174EA6) with inset highlight; splash core recoloured for light and dark.

## Changelog — v2.2

- **In-call screen, Agent Bento edition** (`calls/InCallActivity.kt`): always-dark bento stack with a dot-grid backdrop, breathing brand glow, header strip (pulsing live dot · `PERSORA CALL` · `IN/OUT · LIVE` status pill), an avatar stage with expanding sonar rings while ringing/dialing and a rotating dashed orbit + marker dot once connected, a status panel with a large mono timer and an 18-bar waveform that "listens" while live, ticks softly while connecting and goes flat on hold, square bento control tiles (staggered entrance, brand-blue when active) and a pulsing emerald Answer button. Keypad tiles are now bento squares in both the in-call and Calls screens.
- **Billing — plans are purchasable** (`ui/billing/BillingScreen.kt`): tapping a plan card (or its new `Choose plan` button) always opens the checkout sheet; your current plan and the free plan explain themselves with a toast. Inside checkout, blockers are shown as amber notices instead of silently disabling the tap (pending review → `Review pending`; billing paused → "Payments are paused…"), and the submit button is only disabled when there is genuinely nothing to submit.
- **Share dialog — recent recipients** (`ui/vault/ItemDetailScreen.kt`): members you've shared with before (from outgoing vault shares and contact/business-card shares, most recent first, de-duplicated) appear as tappable chips with avatar initials, name and `ID 1234567`; tapping fills the recipient field.
- **Home**: Spaces card removed from the home grid; **Latest changes** (the rotating activity feed) now sits in its slot right after Coming up.
- **Friendly error toasts** (`ui/components/Toast.kt` → `humanizeError`): every toast passes through one mapper. Transport failures (offline, DNS, timeouts, TLS, connection reset…) become an amber *offline* toast — "You're offline. Showing your saved copy — changes will sync when you're back online." — and are de-duplicated so polling never stacks them; 5xx / gateway / Cloudflare / JSON parse failures become "Persora's server is having a moment. Please try again in a few seconds."; expired sessions and generic 4xx get plain-language copy; server validation messages ("Title is required.") still pass through untouched. The same mapper is used for the inline error on the sign-in form, Billing and the public business-card page, and `ApiClient` no longer embeds raw OkHttp messages in the exception (they go to Logcat).

## Changelog — v2.3

- **Inline PDF / text preview in the item drawer** (`ui/components/FilePreview.kt`): PDF and text attachments (txt, md, csv, json, log, vcf, xml…) now render directly inside the item view like Google Drive — no external viewer, no third-party API. PDFs are rendered on-device with Android's `PdfRenderer` (lazy per-page rendering, page counter, taller/shorter toggle); text files are shown in a selectable mono panel (first 256 KB). Downloads are cached per file in `cache/previews`. Also used by Medical Records; "Open" / "Share file" still exist for everything else.
- **Contacts: ⋯ menu + import drawer** (`ui/contacts/ContactImportSheet.kt`, `core/util/ContactImport.kt`): the search box shares its row with a three-dot menu holding *Recent calls*, *Import contacts* and *Merge duplicates*. The import drawer offers (a) **Upload a file** — vCard `.vcf` or CSV (Google Contacts / iCloud / Outlook / generic headers) with the website's preview: invalid numbers skipped, numbers already saved on another contact filtered out, whole-contact duplicates flagged and unselected, embedded photos uploaded, per-contact progress, and a completion summary; (b) **From phone contacts** — asks for `READ_CONTACTS`, reads the address book, and automatically uploads only the people whose numbers aren't in Persora yet (bad numbers skipped, failures listed). Phone normalisation, duplicate keys and union-find duplicate groups are ports of `ContactsView.tsx`; the duplicates banner now uses the same logic as the site.
- **Tasks / reminders / alarms — Google Tasks style** (`ui/vault/NotesEditorScreen.kt`): full-width editor like the notes editor (no boxed cards): big borderless title, "Add details" row, chips for date / time / repeat with Material pickers, weekday circles, ringtone + Active row, and the favourite star in the top bar exactly like notes (the Favourite switch card is gone).
- **Billing**: paid plans always show **Select plan**; the pending-review state is explained inside checkout instead of relabelling the buttons.
- **Note colours** (`ui/theme/NoteColors.kt`): Keep's palette (coral, peach, sand, mint, sage, fog, storm, dusk, blossom, clay, chalk) with light/dark pairs. Palette button in the note editor tints the whole editor; the Notes list uses new Keep-style `NoteCard`s with the colour, body preview and tag chips; the item drawer tints the note body. Stored as metadata `color`.
- **Wallet cards fixed** (`ui/vault/ItemEditorScreen.kt`, `core/util/Cards.kt`, `SectionScreen.kt`): the duplicate *Card number* and *Expiry* inputs are gone (the section's own `cardNumber` / `expiry` fields were being rendered a second time); the card number is digits-only, capped at 19, grouped 4-4-4-4 (4-6-5 for Amex), Luhn-checked, and the network auto-detects from the first digits (Visa, Mastercard incl. 2221–2720, Amex, Discover, UnionPay); expiry auto-formats to `MM/YY` while typing (month clamped). Saved cards use the website's brand gradients, an EMV chip, and real network marks (Mastercard discs, boxed AMEX, VISA wordmark, Discover dot, UnionPay bars).
- **Bugs fixed along the way**: contacts UI used a category list (`Friend/Service/Emergency`) that doesn't exist on the website — now the shared `CONTACT_CATEGORIES` (Family, Friends, Work, Clients, Suppliers, Students, Other); reminders/alarms in the Notes list were at risk of being drawn as plain notes (branch order); `humanizeError` no longer treats any "401" inside a message as a session error; `ApiClient` logs raw transport errors instead of surfacing them.

## Changelog — v2.4

- **Adopted from the GitHub build (`sa8650/Persora.apk`)**: `IconTile` uses `this.size` inside `drawBehind` (the `size: Dp` parameter shadowed the DrawScope size) and `MoreScreen` re-collects `vault.storage` — both build fixes are now in this tree.
- **Phone-contact import no longer freezes the app**: `ContactImport.prepare` indexes the duplicate keys of existing contacts once (was an O(n²) regex loop per draft on the main thread); regexes are compiled once; parsing/preparing runs on `Dispatchers.Default`; the import loop runs on `Dispatchers.IO` and calls `api.saveContact` per row with a single `vault.refreshContacts()` at the end (was a full cache re-serialise per contact).
- **Boot animation is smooth**: orbit icons, breathing core and the expanding pulse ring read their animated values inside `Modifier.graphicsLayer {}` (translation / scale / alpha) instead of recomposing with `offset(dp)` / animated `size` / `shadow` every frame; `SessionManager.restore()` and `vault.loadFromCache()` run on `Dispatchers.IO` so cache / DataStore reads don't stall the first frames.
- **Spaces**: tool-tile icons 34 → 41 dp (+20 %).
- **More**: list icons 30 → 39 dp (+30 %).
- **Section pages (Notes / Tasks / Reminders / Alarms and every other section)**: the Favorites / Recent / A–Z chip row is gone; the search box is narrower with a 40 dp **filter button** to its right that opens a bento dropdown with *Favorites only*, *Sort by* (Recent · A–Z · By date) and *Reset*; the button turns brand-blue with a dot while a filter is active. "By date" sorts tasks by due date, reminders by `reminderAt`, alarms by `alarmTime`.
