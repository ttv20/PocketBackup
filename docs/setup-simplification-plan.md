# Setup Simplification Plan

## Goal

Make the normal setup path smaller and more direct for profiles, targets,
Tailscale, and selected settings. Advanced controls remain available but should
not be the default path.

## Onboarding

- Explain each setup step with short helper text.
- Keep the flow to Welcome, Permissions, Tailscale, Target, Profile, and Review.
- Remove the standalone onboarding SSH Access page; the app-generated SSH key is
  infrastructure, not a user setup step.
- Reuse the normal add/edit Target and Profile form content inside the
  onboarding frame so the guide keeps its own header and navigation while the
  fields cannot drift from the main screens.

## Permissions

- Split permissions into required and optional groups.
- Add one-line explanations under each permission.
- Required permissions are all files access, battery optimization exemption,
  exact alarm access, and notifications.
- Wi-Fi/SSID access is optional and only needed when backups should run only on
  Wi-Fi or only on a specific network.

## Target Setup

- Remove built-in demo target/profile data from first launch.
- Generate a global SSH key on app launch if no usable private key exists.
- Allow global SSH key override in Settings.
- Allow target-specific SSH key override in Target advanced settings.
- Show only username, server address, Tailscale device, and Connect in the
  primary target flow.
- Default SSH port to 22 and move port editing to Advanced.
- Keep route priority out of Target. Profiles own route priority.
- On Connect:
  - Show a loading state while connecting.
  - Try key-based SSH first.
  - If key auth succeeds, save the target.
  - If SSH handshake works but key auth is not authorized, show a minimal
    password prompt with the server fingerprint in small text.
  - Use the password once to install the selected public key, discard the
    password, then save.
  - If handshake fails, show the connection error.

## Profile Setup

- Remove target default remote path from the user flow.
- Require the user to choose or enter a profile remote folder.
- Start remote browsing at the server home folder (`~`).
- Keep visible fields focused on profile name, source folder, target, remote
  folder, and schedule.
- Move constraints, delete behavior, excludes, route priority, and rsync args to
  Advanced.
- If both server address and Tailscale device exist, default route priority to
  the address entered first, with an Advanced selector to change it.

## Tailscale

- Primary view shows connection status, node name, Sign in with browser, and
  Sign out.
- Keep auth key login, reset, route test, and related diagnostics available but
  lower priority.
- Use "Tailscale device" for user-facing target terminology.

## Settings

- Reorder settings so common app settings and key management are easy to reach.
- Export should present Copy and Save actions, not a full JSON text block by
  default.

## Default Excludes

- Keep cache, caches, thumbnails, trash variants, temporary files, `.rsync-partial`,
  `/Android/data/***`, and `/Android/obb/***`.
- Remove default exclusions for APKs, torrents, podcasts, and app-specific
  WhatsApp media/database patterns.
