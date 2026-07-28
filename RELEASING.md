# Android release channels

M8droid publishes signed APKs through `.github/workflows/android-release.yml`. A release is created only from a valid version tag; ordinary pushes to `main` continue to run debug CI without publishing.

## Channels

| Channel | Tag format | GitHub release state | Permanent download |
|---|---|---|---|
| Stable/latest | `vX.Y.Z` | Normal release, marked latest | `https://github.com/dnordberg/m8/releases/latest/download/M8droid-latest.apk` |
| Beta | `vX.Y.Z-beta.N` where `N` is 1–98 | Versioned prerelease plus rolling `beta` prerelease | `https://github.com/dnordberg/m8/releases/download/beta/M8droid-beta.apk` |

Every versioned release includes:

- `M8droid-<tag>.apk`
- `M8droid-<tag>.apk.sha256`
- the channel alias APK and checksum

The rolling `beta` release is replaced by each newer beta. Versioned beta releases remain available for provenance and rollback.

## Android version mapping

The workflow derives Android metadata from the tag:

```text
versionName = tag without leading v
versionCode = major*1,000,000 + minor*10,000 + patch*100 + channel
```

- Beta `N` uses channel value `N` (`1`–`98`).
- Stable uses channel value `99`.

Example:

- `v0.1.0-beta.1` → version name `0.1.0-beta.1`, code `10001`
- `v0.1.0` → version name `0.1.0`, code `10099`

This guarantees that stable `vX.Y.Z` upgrades every beta in the same version line.

## Required GitHub Actions secrets

The repository must define:

- `M8_ANDROID_KEYSTORE_BASE64`
- `M8_ANDROID_KEYSTORE_PASSWORD`
- `M8_ANDROID_KEY_ALIAS`
- `M8_ANDROID_KEY_PASSWORD`

The keystore and passwords must remain outside Git. The workflow restores the keystore only inside the ephemeral GitHub runner, signs the release build, verifies it with `apksigner`, and then publishes the APK.

## Publishing a beta

1. Confirm `main` contains the intended code and CI is green.
2. Choose the next beta tag, for example `v0.2.0-beta.1`.
3. Create and push the annotated tag:

```bash
git tag -a v0.2.0-beta.1 -m "M8droid 0.2.0 beta 1"
git push origin refs/tags/v0.2.0-beta.1
```

4. Verify the **Android Release** workflow, versioned prerelease, rolling beta release, checksums, and public download.

## Publishing stable/latest

Do not publish stable until the real-device smoke checklist in `TODO.md` and `M8_DIFFERENCES.md` is complete.

After the gate passes:

```bash
git tag -a v0.2.0 -m "M8droid 0.2.0"
git push origin refs/tags/v0.2.0
```

The workflow marks that release as GitHub's latest release and updates the permanent `M8droid-latest.apk` URL.

## Manual rerun

The workflow supports `workflow_dispatch` with an existing valid tag. This is for recovering a failed publication; it does not invent or push a tag.
