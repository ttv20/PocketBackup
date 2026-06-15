# Google Play Data Safety Draft

This is a working draft for Pocket Backup's Google Play Data Safety form. It is
based on the app behavior and privacy answers recorded on June 15, 2026.

Privacy policy URL:

https://codeberg.org/ttv20/PocketBackup/src/branch/main/PRIVACY.md

## Data Collection And Security

- Does the app collect or share required user data types? Yes.
- Is all collected user data encrypted in transit? Yes.
- Does the app provide a way for users to request data deletion? Yes.
  Users can email ttv200+pocketbackupdelete@gmail.com with their Pocket Backup
  diagnostics install ID.

## Data Types To Declare

Declare these for optional diagnostics and error reporting:

- App activity: App interactions
- App activity: Other actions
- App info and performance: Crash logs
- App info and performance: Diagnostics
- Device or other IDs: Device or other IDs

Recommended handling for each declared type:

- Collection/sharing: Collected.
- Required or optional: Optional, because users can turn diagnostics off and
  still use the app.
- Purpose: Analytics. This covers app health, crashes, diagnostics, and future
  performance improvements in Google's taxonomy.
- Processed ephemerally: No.

Sharing note: if Measure.sh and the OpenObserve hosting are acting only as
service providers processing data for @ttv20, Google says service-provider
transfers do not need to be disclosed as "sharing." If any diagnostics provider
uses the data for its own purposes, mark the relevant types as Shared too.

Relevant provider privacy pages:

- Measure.sh: https://measure.sh/privacy-policy
- Tailscale: https://tailscale.com/privacy-policy

## Data Types Not Recommended For Developer Collection

Do not declare these as collected by @ttv20 based on current behavior:

- Files and docs: backup file contents and file names are sent only to the
  user-configured SSH destination, not to @ttv20.
- Location: Wi-Fi access is used locally for Wi-Fi network constraints and
  network selection; Wi-Fi names are not sent in diagnostics.
- Personal info: server usernames, hostnames, remote paths, and SSH credentials
  are stored locally for app functionality and are not sent to @ttv20.

## Store Policy Notes

Google Play requires a privacy policy link in Play Console and a privacy policy
link or text inside the app, even for apps that do not collect user data.

The privacy policy and Data Safety form should remain consistent. Update both if
diagnostics, Tailscale behavior, retention, or third-party service use changes.
