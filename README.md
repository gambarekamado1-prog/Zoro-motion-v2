# Zoro Motion

Android motion-graphics editor for AMVs / velocity / phonk edits. Kotlin + Jetpack Compose, with a pure-Kotlin `core` module.

## Build status (read this first)
**This code has NOT been compiled or run.** It was written in an environment without the Android SDK, Kotlin compiler or network.
Expect a few compile errors to fix on first build. The `core` unit tests are written but have not been executed.

## Setup
1. Open this folder in Android Studio (Ladybug+ / JDK 17). Let it sync (it will create the Gradle wrapper).
2. Run core tests: `./gradlew :core:test`
3. Run the app: select the `app` configuration on a device/emulator (minSdk 26).

## Layout
- `core/` (JVM, testable): `animation` (easing, keyframes), `model` (versioned JSON project), `effects` (8 Zoro effects + transitions),
  `timeline` (pure edit ops + undo/redo), `io` (atomic save, backup recovery, sanitising, missing-media report).
- `app/`: `ui` (Home, Editor, Preview canvas, Timeline, Panels, Export settings), `data` (prefs).

## Status
**Implemented (untested on device):** home/project list (create, rename, duplicate, delete); multi-track timeline (move, trim, split,
duplicate, delete, hide, lock, rename, reorder tracks, zoom, pan, snap, context menu); layers: media stills, text, shapes, adjustment;
keyframes for position/scale/rotation/opacity/anchor + effect params, with linear/ease/bezier/hold and a draggable curve editor;
preview-canvas select/move/resize + rotation slider; 8 effects with parameter-driven output (test asserts every control changes output);
transition builder (5 types, all listed controls), presets, apply between clips; versioned JSON save/load with backup recovery.

**Partial:** preview of blur/glow/RGB split is a Canvas approximation (trail/offset copies). Video layers show a still frame, not playback.
Audio clips appear on the timeline but are not played. Transitions assume clips butt together on one track.

**Not implemented:** video/audio decoding for playback, GPU (OpenGL ES) compositing, MediaCodec export (export screen is settings only
and its button is disabled), velocity remap applied to real media (the time-remap math exists and is tested), Gold Cutout, external-editor interop.

## Next steps
M2 finish: Media3 playback + frame-accurate decode -> M8: GL compositor + MediaCodec encode sharing `EffectEngine`/`TransitionEngine`.
Interop note: Alight Motion's project format is undocumented; plan on exporting transparent overlays / rendered video instead.
