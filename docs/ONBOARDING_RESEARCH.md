# Onboarding research → Persora Android flow

Mobbin's MCP endpoint requires an OAuth token, so this review was done from Mobbin's public flow pages plus
published teardowns of comparable "personal management" apps (Dropbox, Notion, Wise, Mimo, Flo) and password-manager
reviews (1Password vs. Bitwarden). The goal was to find the patterns that consistently appear in well-rated Android
onboarding for apps that store personal data, then map each one onto what Persora already does on the web.

## Patterns observed

| # | Pattern | Seen in | Why it matters for Persora |
|---|---|---|---|
| 1 | **Short value carousel** — 3–4 screens, one illustration + one headline + one sentence each, page dots, single primary CTA, "Skip" top-right, "I already have an account" link | Mimo, Wise, Dropbox, Notion | New members need to understand "one private place for documents, money, people and health" before being asked for an email. |
| 2 | **Privacy / trust step** early | Wise, Flo | Persora stores IDs, cards and medical files. State plainly what is stored, where (your own vault), and what is never stored (full card numbers, passwords). |
| 3 | **Light personalisation (1 question)** before sign-up | Mimo, Flo, Notion | Asking "What do you want to organise first?" lets the first session land on a relevant space instead of an empty dashboard. |
| 4 | **Account creation after value is clear**; sign-in path always one tap away | all | Reduces drop-off; returning users (web members) skip straight to sign-in. |
| 5 | **Permission priming with context**, never a bare system dialog | Dropbox, Flo | Notifications and exact-alarm access are only needed for reminders/alarms — explain that, show the benefit, allow "Not now". |
| 6 | **Security opt-in during onboarding** (biometrics / PIN) | 1Password | Password-manager reviews consistently reward apps that offer biometric lock up front. |
| 7 | **Guided first actions after sign-up** — checklist / empty states with a clear next step | Dropbox ("upload your first file"), 1Password | Bitwarden is criticised for dropping users on a blank screen; Persora's dashboard should never be blank. |
| 8 | **Progress is visible and dismissible** | Dropbox | The checklist card can be closed once the user is comfortable. |

## Resulting Persora Android flow

```
Boot (Persora rings animation)
  └─ Onboarding carousel (4 pages: Everything in one place · Never miss a date · Private by design · Works with the website)
       ├─ Skip → Sign in
       └─ Get started → Personalise ("What would you like to organise first?" 9 tiles)
             └─ Create account (name, email, password) / Sign in (email or 7-digit Persora ID)
                   └─ Protect (allow notifications · allow exact alarms · turn on app lock) — all optional
                         └─ Home with "Getting started" checklist (save a document · add a contact · set a reminder · turn on app lock)
                               and the chosen space opened on first run
```

### Implementation map

| Step | File |
|---|---|
| Carousel + personalise | `ui/onboarding/OnboardingScreen.kt` |
| Auth | `ui/auth/AuthScreen.kt` |
| Permission priming + app lock | `ui/onboarding/ProtectScreen.kt` |
| Checklist card | `ui/dashboard/DashboardScreen.kt` (`GettingStartedCard`) |
| Empty states per space | `ui/components/Common.kt` (`EmptyState`) used in every list screen |
| State machine (which step shows) | `data/repository/SessionManager.kt`, `ui/PersoraRoot.kt` |

### Copy principles kept from the website

- Calm, second-person, no exclamation marks ("A clear horizon.", "Everything important, gathered in one thoughtful place.").
- Trust statements are specific: "Only the brand and last four digits are saved — never the full number or CVV."
- Every destructive or permission action has a "Not now" / "Keep my account" alternative.

## Sources

- Mobbin public flow pages: Mimo (Android onboarding intro), Wise, Notion, Dropbox onboarding; Flo (Android).
- PageFlows: Dropbox Android onboarding (splash → account → notifications → questions → first-file task).
- Wirecutter, "The Best Password Managers" — onboarding comparison of 1Password and Bitwarden.
