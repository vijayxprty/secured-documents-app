# DocuBuds — secured-documents-app

Personal Android document and notes vault. Stores encrypted data on each user's phone, with no account, app internet access, cloud backup, analytics or ads.

## Current release

**1.2.3** · Android 11+ · Native Java Android app.

The interface uses a restrained purple palette, grouped quick actions, sticky search and a detached white navigation pill. The lock screen is centred with a smaller folder illustration. Purple, blue, teal and muted amber appear in icon badges while the main interface stays restrained.

## Features

- Named and nested spaces for IDs, banking, properties, vehicles and companies.
- Encrypted documents, structured records, passwords, notes, OCR text and edit history.
- Local imports, in-memory camera capture, original-byte export and Android sharing.
- Bundled offline English/Hindi OCR, image editing and image-to-PDF tools.
- Native titled notes with rich text, checklists and encrypted autosave.
- Phone authentication or optional separate app PIN/pattern; one-minute inactivity lock.
- Sticky search, floating +, local reminders, archive/trash and local password tools.
- Screenshots allowed; optional donation is one line in Settings only.

## Security and loss of access

**Not independently security-audited.** Implementation details and verification limits are in [the security design](docs/SECURITY_DESIGN.txt) and [release checks](docs/VERIFICATION_1.2.2.txt).

Each installation creates its own keys. Sharing an APK does not share a user's vault. Phone unlock remains an authorised alternative even when an app PIN/pattern is enabled. Screenshots and exported/shared copies leave encrypted-storage protection.

There is **no backup, recovery key or developer reset**. Losing the phone/device keys, uninstalling or clearing app data can permanently lose the vault. Install updates over the existing app rather than uninstalling it.

The repository is public. No signing keys, passwords, local SDK configuration, vault records or test-device data belong in this repository. The release-signing certificate fingerprint is public; the private key must remain outside GitHub.

## Build

Open this directory in Android Studio, or install Java 17+, Android SDK platform 36 and Build Tools 35.0.0. Set `ANDROID_HOME` or create an ignored `local.properties` containing `sdk.dir`.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Gradle 8.13 and AGP 8.13.2 are pinned. Development dependency downloads need connectivity; the installed app has no internet permission.

For a compatible signed release, privately provide `signing/docubuds-release.p12` (alias `docubuds`) and the environment variable `DOCUBUDS_SIGNING_PASSWORD`, then run:

```sh
./gradlew assembleRelease
```

Without that private material, a release build is unsigned. Do not distribute an unsigned build or a differently signed build as an update. The original signing key is not stored in GitHub or GitHub release assets.

## Installation

Download the signed APK from this repository's Releases, then install it on the intended phone. Grant Camera or Notifications only when using those features. Keep Android's installation protections enabled.

## Creator

Made with love ♥ by Vijay Mamta Sahani (@momentswithvms).

## Landing page

Live website: https://vijayxprty.github.io/secured-documents-app/

The responsive landing page source is in `website/`. It includes real emulator screenshots, feature explanations, installation instructions and optional developer support. Serve it with any static web server.
