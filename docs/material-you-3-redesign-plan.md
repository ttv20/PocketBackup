# Material You 3 Visual Redesign Plan

## Scope

This plan covers option 2: make the existing Compose app read visually as a Material Design 3 / Material You app across the current `minSdk = 29` device range.

This is not the older-device wallpaper-dynamic-color implementation. Official dynamic color remains Android 12+ only, but Material 3 visual structure, shapes, typography, component defaults, tonal elevation, and static M3 color roles can be applied on Android 10 and Android 11.

## Source Validation

Official sources checked:

- Android Developers, Material Design 3 in Compose: Compose Material 3 implements Material You/M3, and theming is built from color scheme, typography, and shapes. It also says dynamic color is Android 12+ and should fall back to a custom light/dark color scheme below that.  
  https://developer.android.com/develop/ui/compose/designsystems/material3
- Android Developers, Material Design 3 in Compose: M3 emphasis should come from color roles such as `surface`, `surfaceVariant`, `background`, `onSurface`, `onSurfaceVariant`, font weight, and tonal elevation, not arbitrary local colors.  
  https://developer.android.com/develop/ui/compose/designsystems/material3#emphasis
- Android Developers, Scaffold: `Scaffold` is the standard structure for top bars, bottom bars, FABs, and content padding.  
  https://developer.android.com/develop/ui/compose/components/scaffold
- Android Developers, App bars: top app bars expose title, navigation, actions, colors, and scroll behavior; app bars should sit in `Scaffold`.  
  https://developer.android.com/develop/ui/compose/components/app-bars
- Android Developers, Navigation bar: navigation bars are appropriate for three to five equal destinations on compact window sizes.  
  https://developer.android.com/develop/ui/compose/components/navigation-bar
- Android Developers, Cards: cards should present one coherent content subject; `Scaffold`, `Column`, and `Row` should carry broader layout structure.  
  https://developer.android.com/develop/ui/compose/components/card
- Android Developers, Buttons and FABs: button variants encode action emphasis; FABs are for a single primary action in an app or screen.  
  https://developer.android.com/develop/ui/compose/components/button  
  https://developer.android.com/develop/ui/compose/components/fab

## Current App Audit

The app already uses Compose Material 3:

- `app/build.gradle.kts` includes `androidx.compose.material3:material3`.
- `RsyncBackupApp.kt` imports M3 components such as `Scaffold`, `TopAppBar`, `NavigationBar`, `NavigationRail`, `Button`, `OutlinedCard`, `OutlinedTextField`, `FilterChip`, and `Switch`.

The app does not currently look like a Material You 3 app because the visual system is mostly custom:

- `Theme.kt` defines a narrow custom teal/green palette and does not provide M3 shape or typography customization.
- `RsyncBackupApp.kt` centralizes most screen containment in `SectionCard`, which uses `OutlinedCard`, `RoundedCornerShape(6.dp)`, and `MaterialTheme.colorScheme.surface`.
- Several surfaces use handpicked colors or comparisons against exact background colors, such as `LogSurfaceLight`, `LogSurfaceDark`, `SuccessColor`, and the dark-background check in `toneColor`.
- Many rows are hand-built from `Row`, `Column`, `Surface`, and small custom chips instead of leaning on M3 component defaults and color roles.
- Primary create actions are currently inline rows or icon buttons instead of screen-level FAB/extended FAB actions.

## Visual Thesis

Pocket Backup should feel like a quiet Android system utility: soft M3 surfaces, wallpaper-compatible color roles, generous rounded shape, clear action hierarchy, and compact but breathable operational lists.

## Content And Interaction Thesis

- Primary workspace: dashboard status, profiles, servers, logs.
- Navigation: keep bottom navigation for compact screens and rail for wide screens, because this already matches M3 guidance and existing tests.
- Secondary tools: settings remains the hub for SSH keys, Tailscale, permissions, import/export.
- Interactions: use screen-level FAB/extended FAB for create actions, top app bar scroll behavior for long forms/lists, and selected list rows using container roles instead of heavy borders.

## Implementation Plan

### 1. Theme Foundation

- Replace the narrow custom palette with a full static M3 light/dark `ColorScheme` generated from the existing seed color `#2F6F73` or a slightly calmer Pocket Backup seed.
- Keep Android 12+ dynamic color out of this option unless explicitly requested later. The goal here is visual M3 consistency on Android 10+.
- Add an app `Shapes` definition and pass it to `MaterialTheme`; use M3-like sizes such as `small = 8.dp`, `medium = 12.dp`, `large = 16.dp`, `extraLarge = 28.dp`.
- Remove exact color comparisons like `MaterialTheme.colorScheme.background == Color(0xFF0B0F0E)`.
- Replace custom log/metric surface colors with M3 roles, preferably `surfaceContainer*` roles if available in the current Material3 dependency, otherwise `surfaceVariant` plus tonal elevation.

### 2. App Shell

- Keep the existing `Scaffold`, bottom navigation, and rail structure.
- Replace the dashboard top bar subtitle with content inside the dashboard body; use the app bar for title, back navigation, and actions.
- Add `TopAppBarDefaults` scroll behavior on long screens, using `nestedScroll` on the content root where appropriate.
- Add screen-level FABs:
  - Profiles: add profile.
  - Servers: add server.
  - Settings: no FAB.
  - Dashboard: no FAB; run remains a per-profile action.
- Keep test tags and visible text stable where smoke tests rely on them.

### 3. Replace Generic Section Cards

- Reduce `SectionCard` as a default page-section wrapper. Use plain `Column` sections for forms and settings groups where the card is only decorative.
- Keep cards for coherent entities: profile row, server row, backup log row, key block, import/export block.
- Change cards from 6 dp outlined boxes to M3 containers:
  - default entity rows: `Card` or `ElevatedCard` with `MaterialTheme.shapes.medium`;
  - selected entity rows: `CardDefaults.cardColors(containerColor = colorScheme.primaryContainer, contentColor = colorScheme.onPrimaryContainer)`;
  - secondary/tool blocks: tonal `Surface` or filled card using `surfaceContainer`/`surfaceVariant`.
- Use `HorizontalDivider` sparingly for grouping inside a card, not around every section.

### 4. Rows, Chips, And State

- Convert repeated entity rows toward an M3 `ListItem`-style layout: leading icon, headline, supporting text, trailing action.
- Use `AssistChip` or role-aware `Surface` badges for status. Avoid custom alpha badges when M3 chip defaults communicate the state.
- Map operational states to semantic M3 roles:
  - Success: `primary`/`primaryContainer` unless a distinct success color is essential.
  - Warning: `tertiary`/`tertiaryContainer`.
  - Error/destructive: `error`/`errorContainer`.
  - Neutral: `surfaceVariant`/`onSurfaceVariant`.
- Keep destructive actions visually secondary until confirmation dialogs, then use error roles in the dialog/action.

### 5. Buttons And Primary Actions

- Use `Button` for the highest emphasis action in a local context, such as Save or Import.
- Use `FilledTonalButton` for important but less urgent actions, such as Test, Generate, Copy, or setup flows.
- Use `OutlinedButton` only for alternate or medium-emphasis actions.
- Use `TextButton` for low-emphasis dialog dismissals or navigation-like actions.
- Replace visual arrow text (`">"`) with an icon from Material icons.

### 6. Screen-Specific Pass

- Dashboard:
  - Replace the metric strip with softer tonal containers and larger rounded corners.
  - Make profile cards cleaner: one title line, one route/source summary, compact status row, trailing run/stop button.
  - Move dashboard description out of the app bar.
- Profiles and Servers:
  - Use list/detail or selected-card treatment with `primaryContainer`.
  - Move add actions to FAB/extended FAB.
  - Keep editor forms readable with plain section headings and tonal groups instead of stacked outlined cards.
- Logs:
  - Use log cards only for individual backup log records.
  - Use M3 container roles for command/output blocks.
- Settings:
  - Use `ListItem`-style rows for SSH keys and Tailscale tools.
  - Keep permission rows as action rows with clear grant/open buttons.
  - Keep import/export as contained blocks because they are coherent text-heavy tools.
- SSH keys and Tailscale:
  - Use tonal cards for current state and setup flows.
  - Use error/tertiary roles for warnings instead of custom warning surfaces.

### 7. Validation

- Build with the Docker-backed script from `AGENTS.md`:
  ```bash
  ./scripts/docker-build-debug.sh
  ```
- Run existing unit/instrumentation smoke checks that cover visible labels and navigation. Preserve all `testTag` values unless tests are updated intentionally.
- Visually inspect at least:
  - compact phone width;
  - wide layout where `NavigationRail` appears;
  - light and dark theme preferences;
  - Android 10/11 behavior if available, because this option is meant to improve older devices too.
- Check for regressions in:
  - text clipping in bottom navigation and chips;
  - selected row contrast;
  - disabled button contrast;
  - forms inside vertical scroll containers;
  - system bar icon contrast.

## Explicit Non-Goals

- No wallpaper-derived dynamic color on Android 10/11 in this option.
- No data model changes.
- No navigation rewrite to Navigation Compose unless separately requested.
- No broad copy rewrite beyond labels needed for Material component clarity.

## Expected Result

After this pass, the app should still behave like the current Pocket Backup utility, but visually it should read as a Material 3 Android app: softer shapes, fewer hard outlines, clearer action hierarchy, proper M3 color roles, tonal elevation, FABs for create actions, and less custom chrome.
