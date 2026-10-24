# Zafiro Control 1.8.1 upgrade audit

Status: signed installation candidate, independently verified. CI tests, lint, signed release compilation and identity checks passed. Physical Nothing Phone (3a)/Android 17 beta smoke testing remains pending. No installed application was accessed or changed. `main` remains at `8b4e9cb6405f04a0f3eb1cbe6370460b43ec28e4`.

## Source selection and comparison

Upstream is [niki914/zafiro](https://github.com/niki914/zafiro). The latest non-prerelease at audit time (24 October 2026) is [v15-1.8.1](https://github.com/niki914/zafiro/releases/tag/v15-1.8.1), commit `ee33b4ad83eb14a82c639c709ea2ff7eaf76c99f`. Upstream main contains later UI/settings refactors; this upgrade uses the released tag rather than unreleased main.

| Source | Finding | Integration |
| --- | --- | --- |
| Fork main | Installed release basis: versionCode 14, 1.5.3-control; release package uses `.control` suffix | First parent of isolated upgrade merge; preserved as stable reference |
| feat/system-explorer | Earlier Explorer/migration development; Nothing Explorer also contains additional experimental discovery code | Preserve the working main implementations, not a blind merge of this older branch |
| fix/shizuku-user-service-leak | Explicit AIDL destroy transaction and process cleanup; service version 2 | Already in main, retained; upstream stable still lacks these fixes |
| fix/shizuku-session-isolation | Per-client service tags stop one terminal teardown affecting another | Already in main, retained; tags now also distinguish app process instances |
| build/release-apk-temp | Historical build/signing experiments | Not merged as a production base |
| 1.6.0 | New onboarding, conversation management, GitHub skill installation | Included via upstream history |
| 1.7.0 | AgentManager and independent agent sessions, persistent terminal pool, background improvements | Included; custom Explorers keep the existing public LibTerm entry point |
| 1.8.0 | Context compaction, grouped regenerated responses, repetition protection, UI improvements | Included |
| 1.8.1 | Session hardening, MCP interoperability and cold-start improvements, full tool output | Included |

Four merge conflicts were resolved manually: build identity/version, Home page imports/state, navigation declarations, and suffixed local-package recognition. Both Explorers remain linked from the overflow menu and routed to their original interfaces. Setup import/export remains available. An obsolete, unreferenced ThemeSettingsPage was removed to follow upstream navigation.

## Streaming and lifecycle findings

The reported `Network error: stream failed` maps to a generic transport exception during request setup. Without a device log containing its exception cause, the exact observed failure cannot be proven. The source nevertheless contains reproducible gaps relevant to the symptoms:

- Upstream's agent CPU wake demand was gated by the screen-on preference. A background session with that preference off received no CPU lock. Running sessions now demand CPU protection independently of screen lighting.
- A 15-minute timed CPU lock was acquired only when state changed. A continuously running session could outlive it. Active demand now renews the finite lock every five minutes and releases it on idle/cancellation.
- Foreground-service residency depended on the resident-notification preference. It now also follows running main/background agents. Android foreground-start restrictions still apply, and start failures remain logged.
- SSE body reads blocked on an IO thread, but the old completion handler did not reliably cancel the socket until the reading coroutine completed. A channelFlow reader now closes the socket through awaitClose during cancellation.
- [DeepSeek documents queue keep-alive comments](https://api-docs.deepseek.com/quick_start/rate_limit/). Upstream's model-idle timer ignores these comments. DeepSeek pre-inference heartbeats now reset the silence timer, bounded by a ten-minute absolute queue deadline. Once the first protocol event arrives, only protocol progress resets idle; endless post-inference comments cannot hide a stall. Other providers retain their idle rules. Network connect/read/write limits are unchanged.
- The existing segment retry implementation was not enabled by the app's turn options. Two bounded segment retries are now enabled. They rebuild only the failed model request from committed history; completed tool results are reused. Auth, quota, parse errors, user cancellation and idle expiration are not turned into indefinite retry loops.
- The upstream stream-event mapper held mutable text/thinking/retry state in a global singleton despite independent agent sessions. Each turn now owns a mapper instance; an interleaved-session regression protects its text baselines.
- Abandoned partial UI output is discarded on a stream retry while completed tools are retained. This prevents duplicate partial text after recovery.

There is no claim of surviving force-stop, reboot, Doze restrictions or vendor process killing with a live socket intact. Upstream persists committed conversation entries and tool results independently of the chat screen. After process death, history can be reopened; an in-flight external operation is not automatically replayed. Partial content not yet committed can still be lost.

## Explorers and permissions

System Explorer retains live device/display/power metrics and Shizuku RSS process sampling. RSS is now labelled explicitly. A page owns and reuses one terminal, tears it down on leaving, and recreates it after permission/shell failure. Permission requests always reset their busy state. Shell exit failures and stderr are no longer silently converted to empty metrics. Cancellation propagates and terminal teardown runs in a non-cancellable cleanup context.

Nothing Explorer retains its existing search, discovery categories and activity launch controls. It checks permission before scanning, surfaces partial-scan stderr (including permission/SELinux failures), handles cancellation correctly, checks launch results and validates activity components before shell interpolation. Discovery is not exhaustive: activity discovery queries MAIN actions, vendor access can be blocked, and a discovered activity is not necessarily exported or launchable.

No permission changes, root requirements or targetSdk increase were introduced. compileSdk remains 37; targetSdk remains upstream's 34. Nothing Phone (3a)/Android 17 beta validation still requires a real device.

## Data and migration

Application ID remains `com.niki914.zafiro.control`, candidate release version `1.8.1-control`, versionCode 15. Development/debug builds have the separate `.control.dev` package and are not update APKs for the installed application.

Conversation schema advances from 2 to 4 using upstream's explicit 2→3 and 3→4 migrations. These add conversation config/unread/branch metadata without discarding existing entries. Provider configuration, MCP settings, skills, shell/Python execution and accessibility modules remain in the same package/private storage.

Setup export includes `local_settings.json`, `settings/**/*.json` and `skills/**`. It contains credentials in plaintext and is **not** a conversation-history backup. The export result now says this. Import validates/stages files before writing and uses AtomicFile for individual replacements. Import is rejected while agents are running. Existing path/size restrictions remain. Multi-file import is not a transaction: a device/storage failure between file replacements can leave a partially imported setup; retain the original export and restart after import.

The update checker now follows this fork's releases instead of offering upstream APKs as Control updates. App branding is consistent across existing locales.

## Signing and build gates

Signing remains exclusively in GitHub Actions using existing `SIGNING_KEYSTORE_BASE64` and `SIGNING_PASSWORD`, alias `nexus_key`. No private signing material was retrieved, printed or committed. The workflow fails if the secrets are missing, and removes decoded signing material after the job.

Baseline: successful stable build [37458270847](https://github.com/mitchwalsh5-sys/zafiro/actions/runs/37458270847), artifact `Zafiro-Control-1.5.3-control`, ID 11410262448, not expired at audit time. The baseline was downloaded and verified independently with apksigner. Its public signer SHA-256 digest is pinned in `.github/control-signing-cert.sha256`. The workflow verifies the candidate against that pin and checks package name/versionCode; verification remains usable after the old Actions artifact expires. It also produces `apk-verification.txt` with public certificate/identity/file-hash evidence. Availability of secret names in the workflow does not prove that their current values work; only a successful signed build does.

The verified build is [Actions run 40081095826](https://github.com/mitchwalsh5-sys/zafiro/actions/runs/40081095826), source commit `630afedef9e40febf5a51eeba6a2e5396f9c1978`. Its signing preflight confirmed that the configured GitHub keystore certificate equals the original stable APK certificate. The final APK passed both CI and independent local `apksigner`/`aapt` checks.

### Exact verification-script failure

Run 40079636064 used an anchored sed expression expecting `Signer #1 certificate SHA-256 digest:`. The CI apksigner report instead uses `V2 Signer: certificate SHA-256 digest:`. Replaying the old expression against the CI report extracts an empty byte string, so the certificate equality guard fails even though the fingerprint is correct. This was a certificate-report parsing defect, not a changed signing identity or package ID.

The corrected parser reads the certificate digest independently of the signer-label prefix and still requires exactly one complete matching SHA-256 fingerprint. Cryptographic signature verification, exact package validation and the versionCode upgrade check remain mandatory. The report is printed and the candidate is retained for diagnostic evidence.

Verified artifact: `Zafiro-Control-1.8.1.apk`, 30,499,396 bytes.

- Package: `com.niki914.zafiro.control`
- Version: `1.8.1-control`, versionCode `15`
- Debuggable: false
- Certificate SHA-256: `39085cf7f70bed43adc7a55c14b5c75377fd438290564d96361422e692e441d2`
- APK SHA-256: `5968e7c3c6a7361a520041250bb9bb485a19cf4748414fae68dfe9f4defdb732`

The downloaded APK hash also matched the CI verification record. No new signing key was created or substituted.

## Installation and rollback constraints

Do not uninstall or clear data. Export existing setup before testing and keep it private. After every release gate passes, use the signed `.control` release APK as an in-place update only; never substitute the `.control.dev` debug APK. Check actual installed signer if the installed APK did not come from the baseline build.

Normal Android installation cannot downgrade versionCode 15 to 14. The older app's database schema also predates the upgraded schema, so reinstalling the old APK is not a reliable data-preserving rollback. Keep main and its original APK; any rollback release must use a higher versionCode and an explicit compatible database/restore strategy. No automatic rollback, uninstall or data-clearing action is implemented.

## Validation record

| Check | Result |
| --- | --- |
| OKIA transport/agent loop | 447 passed |
| Shizuku backend | 22 passed |
| Agent runtime | 470 passed |
| Business agent | 58 passed |
| App | 420 passed |
| Kotlin total | 1,417 passed, zero failures/errors/skips |
| Python runtime | 14 passed locally |
| App lint | Passed: zero errors, 89 warnings |
| Release compilation/R8/signing | Passed in CI |
| Schema 2→3→4 | Room tests passed; independent SQLite fixture retained conversation/tool-result rows |
| APK signer/package/versionCode | Passed in CI and independently after download |
| Physical phone/live DeepSeek sessions | Pending user device testing; no device or live API credentials accessed |

Persistence and notification test fixtures now initialise/reset their own settings context. Lifecycle-boundary tests await ordered delivery instead of relying on StateFlow to retain rapid synthetic intermediate states. No tests or signing checks were disabled.
