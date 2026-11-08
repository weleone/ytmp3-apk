# YTMP3 Android 1.0.3 — 16KB page-size fix (NOT pushed to GitHub)

Source of truth: `weleone/ytmp3-apk` @ `ed76de4` (tag v1.0.2, versionCode 3).
This copy lives only in the workspace: `~/workspace/ytmp3-apk-16kb/`.
Nothing here was pushed, and no GitHub Release was created.

## Root cause

The v1.0.2 APK that shipped was a **debug** build (`assembleDebug`,
despite the workflow step being named "Build release APK"), and both
native dependencies predate 16KB support:

| Component | v1.0.2 | Problem |
|---|---|---|
| Chaquopy Gradle plugin | 15.0.1 | Predates all 16KB work. 16.1.0 only linked the `_chaquopy` OpenSSL/SQLite stubs in 16KB mode; 17.0.0 (2025-12-01) is the first release where Chaquopy itself supports 16KB devices, and it recommends Python 3.13+ because "many existing Android wheels will still fail to load on 16KB devices". |
| Python runtime | 3.11 | 3.12+ is 64-bit only; 3.13 is the Chaquopy-recommended version for 16KB devices. |
| FFmpegKit | `dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7` | The 8.1.7 AAR downloaded from Maven Central today *is* 16KB-aligned (every .so LOAD p_align=16384, verified 2026-11-08), so the unaligned FFmpeg libs in the phone screenshot came from the pre-16KB build that shipped in v1.0.2's debug APK. Pinned to the current LTS 8.1.9 anyway. |
| AGP | 8.5.2 | Already >= 8.5.1, so 16KB zip alignment of uncompressed libs works. Kept. |
| compileSdk / targetSdk | 34 | Play requires 16KB support for apps targeting API 35+. Raised to 35. |
| NDK | unpinned | Pinned to r28 (`28.2.13676358`), which aligns .so files to 16KB by default (r27 and lower need `-Wl,-z,max-page-size=16384` explicitly). |
| ABI filters | arm64-v8a + armeabi-v7a | Python 3.12+ has no 32-bit ABIs and FFmpegKit 8.1.x ships no armeabi-v7a. Now arm64-v8a only. The user's phone (Redmi K60 Pro) is arm64. 32-bit devices are dropped by this release. |

## Files changed (vs v1.0.2)

- `build.gradle` — Chaquopy Gradle plugin `15.0.1` -> `17.2.0`
  (Maven Central latest, verified 2026-11-08; supports AGP 7.3–9.5).
- `app/build.gradle` —
  `compileSdk 34 -> 35`, `targetSdk 34 -> 35`,
  `versionCode 3 -> 4`, `versionName '1.0.2' -> '1.0.3'`,
  `ndkVersion "28.2.13676358"`,
  Python `3.11 -> 3.13`,
  `abiFilters 'arm64-v8a'` only,
  FFmpegKit `8.1.7 -> 8.1.9` (Maven Central latest, verified),
  `release { debuggable false; zipAlignEnabled true }`,
  `packaging.jniLibs.useLegacyPackaging false` (uncompressed libs, required for 16KB zip alignment).
- `.github/workflows/build.yml` —
  installs `platforms;android-35`, `build-tools;35.0.0`, `ndk;28.2.13676358`,
  sets up Python 3.13 (Chaquopy buildPython must match the app Python),
  runs `gradle assembleRelease` (was `assembleDebug`),
  runs `zipalign -c -P 16 -v 4` on the release APK and fails the build if it is not aligned,
  uploads `app-release-unsigned.apk`.
- `scripts/verify-16kb.sh` (new) — runs the zipalign check plus an ELF
  LOAD `p_align >= 16384` check for every `.so` in an APK:
  `scripts/verify-16kb.sh <apk> [zipalign]`

No app features, Java/Kotlin code, Python code, or UI were changed.

## Verification done in this workspace (2026-11-08)

- FFmpegKit AARs downloaded from Maven Central and ELF-checked with a
  Python program-header parser (`/tmp/so-check/check.py`):
  - `ffmpeg-kit-full:8.1.7` — 20/20 .so files (arm64-v8a + x86_64),
    all LOAD `p_align=[16384, …]` — OK.
  - `ffmpeg-kit-full:8.1.9` — 20/20 .so files, all LOAD `p_align=16384` — OK.
- Maven Central metadata verified: `ffmpeg-kit-full` latest `8.1.9`
  (versions 6.0.3, 6.0.4, 7.1.6, 7.1.7, 8.1.7, 8.1.8, 8.1.9);
  `com.chaquo.python:gradle` latest `17.2.0`
  (15.0.1, 15.1.1, 16.0.0, 16.1.0, 17.0.0, 17.1.0, 17.2.0).
- Chaquopy 17.2 docs/changelog verified: Python 3.10–3.15, AGP 7.3–9.5,
  minSdk 24; 17.0.0 adds 16KB device support, recommends Python 3.13+.
- Local full build: see "Build status" below — update this section with
  the actual result before claiming a release APK exists.

## Build status

- **Local build in this VM: NOT possible — environment limitation, not a
  project error.** Toolchain was installed and verified working
  (Temurin JDK 17.0.21, Python 3.13.18, Android SDK platform 35 /
  build-tools 35.0.0 / NDK 28.2.13676358, Gradle 8.7, zipalign).
  But every Gradle invocation fails identically, even for a trivial
  `hello` task in an empty project:
  `FAILURE: Could not receive a message from the daemon.`
  The daemon starts and listens on 127.0.0.1, but the client reads a
  garbage serializer tag (`Unexpected type tag 109`) and the daemon
  log shows no connection was ever accepted. Loopback TCP itself works
  (verified with a Python socket test), unsetting the proxy env vars
  does not help, and `--no-daemon` fails the same way. Gradle daemon
  builds cannot run in this sandbox. Logs:
  `build-logs/local-assembleRelease*.log`, `build-logs/minimal-gradle*.log`.
  `apt-get update` also hangs in this VM (abandoned; JDK/SDK were
  downloaded directly via curl instead) and `sdkmanager` fails on the
  authenticated egress proxy (`HttpURLConnection.doTunneling`
  `NoSuchElementException`) — SDK packages were downloaded directly
  from dl.google.com and unpacked by hand.
- **GitHub Actions build: not run (nothing pushed).** This is the
  verification step that remains: push the changed files to
  `weleone/ytmp3-apk` main, let the workflow run `assembleRelease`,
  and confirm the `zipalign -c -P 16` step passes and
  `scripts/verify-16kb.sh` passes on the produced APK — in particular
  for the Chaquopy libs (`libpython3.13.so`, `libchaquopy_java.so`,
  `libssl_chaquopy.so`, `libcrypto_chaquopy.so`, `libsqlite3_chaquopy.so`),
  whose .so files are downloaded by the Chaquopy plugin at build time
  and therefore could not be ELF-checked locally (only the FFmpegKit
  side could, see below).
- **No release APK exists yet.** Do not claim v1.0.3 is built until the
  CI build above passes and the APK is signed (see Signing).

## Build the release APK (GitHub Actions, after user approves a push)

```bash
cd ytmp3-apk-16kb
# copy the changed files into a clone of weleone/ytmp3-apk, commit, push to main
git push   # workflow builds assembleRelease, checks zipalign -P 16, uploads the unsigned APK
```

## Signing — required before publishing v1.0.3

The workflow uploads an **unsigned** release APK. It cannot be installed
as-is. Add repository secrets and a signing step (see the commented block
in `.github/workflows/build.yml`):

- `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
  `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`

Or sign locally:

```bash
apksigner sign --ks <your-keystore.jks> --ks-key-alias <alias> \
  --out ytmp3_v1.0.3.apk app/build/outputs/apk/release/app-release-unsigned.apk
apksigner verify --verbose ytmp3_v1.0.3.apk
scripts/verify-16kb.sh ytmp3_v1.0.3.apk /path/to/zipalign
```

## Install note for the user

The old APK on the phone is a **debug** build signed with the debug key.
A release APK signed with a different key **cannot update-install over it**
— uninstall the old `com.weleone.ytmp3` first, then install v1.0.3.
Its local data will not carry over; server-mode settings must be re-added.
