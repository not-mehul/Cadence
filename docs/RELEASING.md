# Making a release

Pushing a tag such as `v1.0.0` runs `.github/workflows/release.yml`. That workflow:
- runs lint and the unit tests;
- builds a minified APK signed with the release key;
- checks the APK's permissions;
- publishes it to GitHub Releases with its SHA-256 checksum and the signing certificate's fingerprint.

The version comes from the tag: `v1.2.3` gives `versionName` 1.2.3 and `versionCode` 10203. Each part must stay below 100.

## One-time setup: the release key

Android only installs an update over an existing app if both are signed with **the same key**. If the key is lost, everyone has to uninstall Cadence (losing their data unless they made a backup) to install a version signed with a new key. Guard it accordingly.

1. **Create the keystore on your own computer.** Never create it in CI or a shared machine.

   ```bash
   keytool -genkeypair -v -keystore cadence-release.jks -alias cadence \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

   Use a long, unique password, and the same one for the key when asked.

2. **Back it up in two separate offline places**, for example an encrypted USB drive and a password manager attachment. Store the password with it.

3. **Add four repository secrets** in GitHub, under *Settings → Secrets and variables → Actions → New repository secret*:

   | Secret | Value |
   |---|---|
   | `CADENCE_KEYSTORE_BASE64` | the output of `base64 -w0 cadence-release.jks` (on macOS: `base64 -i cadence-release.jks`) |
   | `CADENCE_KEYSTORE_PASSWORD` | the keystore password |
   | `CADENCE_KEY_ALIAS` | `cadence` |
   | `CADENCE_KEY_PASSWORD` | the key password |

   If you'd like a release to wait for approval, put the secrets on a GitHub *environment* with required reviewers, and add `environment:` to the workflow's job.

4. **Note the certificate fingerprint** so you can recognise it later:

   ```bash
   keytool -list -v -keystore cadence-release.jks -alias cadence | grep SHA256
   ```

## Each release

```bash
git tag v1.0.0
git push origin v1.0.0
```

When the workflow finishes, the release page holds `cadence-1.0.0.apk`, its `.sha256` file, and notes with both fingerprints.

## Building a release locally

```bash
export CADENCE_KEYSTORE_FILE=/path/to/cadence-release.jks
export CADENCE_KEYSTORE_PASSWORD=... CADENCE_KEY_ALIAS=cadence CADENCE_KEY_PASSWORD=...
./gradlew :app:assembleRelease -Pcadence.version=1.0.0
```

Without the keystore variables the release APK is unsigned. To try a minified build on your own device, add `-Pcadence.signWithDebugKey=true`. CI does the same to smoke-test the release build on its emulators.
