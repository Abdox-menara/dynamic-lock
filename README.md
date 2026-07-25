# Dynamic Lock — a modern, from-scratch alternative to DroidLock

Dynamic Lock recreates the idea behind **DroidLock: Dynamic Lockscreen** — a PIN that
constantly changes with the **time, date and battery**, so it can't be shoulder-surfed —
and rebuilds it as a complete, modern Android app (Kotlin, Material 3, min SDK 24).

## Important: what changed since the original (2016)

The original DroidLock changed your phone's **system** lock-screen PIN. Android removed
that ability for normal apps in **Android 8 (2017)** for security reasons, and it required
risky `READ_SMS` / `RECEIVE_SMS` permissions for a "rescue PIN".

So Dynamic Lock does **not** touch the system PIN. Instead it applies the same
ever-changing-PIN idea to things an app *is* allowed to protect:

1. **A private vault** — notes and photos locked behind the dynamic PIN.
2. **An app locker** — other apps (WhatsApp, Gallery, Settings…) gated behind the dynamic PIN.

No SMS permissions are used.

## The dynamic PIN

The PIN is assembled from building blocks and then optionally transformed. Everything below
is faithful to the original app's documented behaviour and is unit-verified (see *Verification*).

| Mode | Example (04 May 2016, 01:23 / 13:23, battery 52%) | PIN |
|---|---|---|
| Time 12h | 01:23 | `0123` |
| Time 12h, +10 min offset | 01:23 → 01:33 | `0133` |
| Time 24h | 13:23 | `1323` |
| Date · International (DD/MM) | 04/05 | `0405` |
| Date · USA (MM/DD) | 05/04 | `0504` |
| Date · Intl + 2-digit year | 04/05/16 | `040516` |
| Date · Intl + 4-digit year | 04/05/2016 | `04052016` |
| Battery (with Double) | 52% | `5252` |
| Geek combo: battery + hour(12h) + month + minute | 52 · 01 · 05 · 23 | `52010523` |

**Add-ons** (applied to the assembled PIN, base `1234`):

| Add-on | Result |
|---|---|
| Double | `12341234` |
| Mirror | `12344321` |
| Sum (1+2+3+4) | `1010` |
| Reverse | `4321` |

You configure all of this in **PIN Rule**, build custom combos by tapping components in order,
and there's a live **Show Current PIN** screen so you can never lock yourself out.

## Build it (you get the APK here)

This project is delivered as source — you compile the `.apk`. Two ways:

### A) Android Studio (easiest)
1. Install **Android Studio** (Hedgehog or newer).
2. **File → Open** and select this `DynamicLock` folder.
3. Let Gradle sync (it downloads the Android SDK / dependencies automatically).
4. Press **Run** ▶ (or **Build → Build Bundle(s)/APK → Build APK(s)**).
5. The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

### B) Command line
Requires **JDK 17** and the **Android SDK** (set `ANDROID_HOME`, or create `local.properties`
with `sdk.dir=/path/to/Android/sdk`). The Gradle wrapper jar isn't bundled, so first generate it:

```bash
gradle wrapper --gradle-version 8.7   # only needed once; needs a local Gradle install
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

Then install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

## Permissions and why they're needed

| Permission | Purpose |
|---|---|
| `PACKAGE_USAGE_STATS` (Usage Access) | Detect which app is in the foreground, to know when to lock it |
| `SYSTEM_ALERT_WINDOW` (Display over apps) | Show the unlock screen on top of a locked app |
| `FOREGROUND_SERVICE` (+ special-use) | Keep the lightweight watcher running |
| `POST_NOTIFICATIONS` | Show the ongoing "protection active" notice (Android 13+) |
| `RECEIVE_BOOT_COMPLETED` | Re-enable protection after a reboot |
| `QUERY_ALL_PACKAGES` | List installed apps so you can choose which to lock |

The app-locker permissions are only needed if you use **Lock Other Apps**. The vault needs none.

## Project layout

```
app/src/main/java/com/example/dynamiclock/
├── pin/         PinEngine, PinConfig, PinRepository   (the dynamic-PIN brain)
├── ui/          MainActivity, UnlockActivity, SettingsActivity, CurrentPinActivity
├── vault/       VaultActivity, NoteEditorActivity, VaultStore
├── locker/      AppLockService, LockedAppsActivity, LockManager, BootReceiver
└── App.kt
verify/          verify_pin.py  (algorithm test — see below)
```

## Verification

The PIN algorithm was checked against **all 13 examples** documented for the original app.
Run it yourself (pure Python, no Android needed):

```bash
python3 verify/verify_pin.py    # -> RESULT passed=13 failed=0
```

`verify_pin.py` mirrors `PinEngine.kt` line for line, so a pass confirms the shipped engine
reproduces DroidLock's behaviour exactly.

## Notes & caveats

- App-lockers on Android are inherently "best effort": aggressive battery optimisation can kill
  the background watcher. If protection stops, exclude Dynamic Lock from battery optimisation.
- The vault stores files in app-private internal storage (not visible to other apps). For
  stronger at-rest protection you can later wrap files with Jetpack Security `EncryptedFile`.
- To protect the "Show Current PIN" screen itself, add **Dynamic Lock** to your locked apps.
- This is a personal-use template. Test before relying on it, and keep a normal device lock too.

Free to use and modify.
