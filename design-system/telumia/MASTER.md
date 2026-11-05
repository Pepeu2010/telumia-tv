# Telumia visual system

This file is the visual source of truth for the Telumia TV and desktop clients. Page-specific files under `pages/` may refine layout, but must not replace these foundations.

## Product direction

- Cinematic, content-first and dark by default.
- Artwork leads; chrome recedes until the user interacts.
- One primary navigation surface per viewport. Never show a top navigation bar and a lateral navigation rail at the same time.
- PC supports pointer, keyboard and remote-style focus. TV must remain fully operable with directional keys, Select and Back.
- Motion explains hierarchy and continuity. It must never delay navigation or carry required state.

## Three-layer tokens

### Primitives

| Role | Value |
| --- | --- |
| Background | `#090D12` |
| Elevated background | `#131B24` |
| Card | `#1B2632` |
| Cream | `#FFF0D6` |
| Amber | `#F3A652` |
| Coral | `#EC7460` |
| Focus | `#FFD295` |
| Black | `#000000` |
| White | `#FFFFFF` |

Spacing follows a 4/8dp rhythm. Preferred radii are 12dp for compact controls, 16dp for cards, 24dp for panels and fully rounded navigation beacons or chips.

### Semantic roles

- `background`: background primitive, or pure black in OLED mode.
- `surface`: elevated background.
- `surfaceCard`: card primitive.
- `textPrimary`: near-white with at least 4.5:1 contrast.
- `textSecondary`: desaturated neutral with at least 4.5:1 contrast at body sizes.
- `accent`: active theme accent; Telumia defaults to amber/coral.
- `focusRing`: focus primitive, 2–3dp on TV.
- `focusBackground`: accent-toned dark surface.
- `overlayScrim`: black at 56–82%, selected from artwork readability.
- `danger`, `warning`, `success`, `info`: always paired with text or an icon, never communicated by color alone.

### Component rules

- Cards reserve their aspect ratio before artwork loads and use skeletons for waits longer than one second.
- TV targets are at least 48dp. Desktop pointer targets are at least 40dp and keyboard focus is always visible.
- Focus uses border, contrast and a restrained scale up to 1.02. It must not move neighboring layout.
- Buttons have centered text and one clear primary action per content group.
- Player controls use an opaque-enough scrim to preserve subtitle and artwork readability.
- Settings use a stable category rail and a content workspace; destructive/account actions are separated.

## Typography

Manrope is the Telumia family. Use Bold/Semibold for display and section hierarchy, Medium for controls and Regular for body copy. Body text should normally be at least 16sp on TV and keep a readable line length. Timers and timeline values use tabular figures where supported.

## Motion

| Purpose | Full | Reduced | Off |
| --- | ---: | ---: | ---: |
| Immediate feedback | 125–150ms | <= 100ms | 0ms |
| Focus/state | 180–220ms | <= 120ms | 0ms |
| Route/content replacement | 220–300ms | crossfade <= 120ms | 0ms |
| Panel enter | 280–345ms | <= 120ms | 0ms |
| Panel exit | 145–250ms | <= 100ms | 0ms |
| Hero/opening | 450–700ms | opacity only | final state |

Animate transform and opacity. Route motion is interruptible. Exits are shorter than entrances. Infinite decorative motion pauses offscreen and is disabled by reduced/off policies.

## Navigation

- TV: a compact top-left beacon identifies the current root destination; Left or Back opens one lateral panel. The beacon and panel never appear as two competing navigation bars.
- Desktop: use one adaptive top or side navigation layout selected by the user; never duplicate destinations in a second simultaneous root bar.
- Restoring a destination restores its scroll position, selected filter and last meaningful focus.
- Back closes transient surfaces first, then returns in hierarchy, then exits only from a root destination.

## Performance tiers

- Quality: full hero transitions, trailer preview when enabled, blur and richer artwork.
- Balanced: restrained blur and motion, delayed trailer preview and bounded prefetch.
- Light: static hero, crossfades only, no continuous blur/trailer, reduced image resolution and conservative cache.
- Auto selects a tier from memory, decoder capability and storage. It never changes semantic content or hides required controls.

## Accessibility and QA gates

- Normal text contrast >= 4.5:1; focus and meaningful non-text boundaries >= 3:1.
- Icon-only controls expose a localized accessible name and selected/expanded state when applicable.
- Reduced motion and motion off remain fully functional.
- Verify TV at 720p and 1080p; desktop at compact, 1080p and 4K scaling.
- Verify keyboard, pointer and DPAD paths; rapid reverse navigation; slow-device mode; theme switching; empty, loading, error and offline states.
- No raw theme color in a component when a semantic token exists.
