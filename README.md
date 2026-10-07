# PureEnhance AI

Free, ad-free, offline Android photo enhancement. Kotlin · Jetpack Compose · ONNX Runtime Mobile · ML Kit (bundled face detector).
No ads, subscriptions, credits, login, watermark or uploads. **The manifest declares no permissions at all** (no INTERNET).

## Before it runs: add the model files (required)

AI weights are not in this repo (hundreds of MB, and licences must be checked by you). The app shows a clear
"model not included" message until they exist.

```
pip install torch onnx onnxruntime numpy realesrgan gfpgan basicsr
python scripts/convert_models.py --realesrgan realesr-general-x4v3.pth --gfpgan GFPGANv1.4.pth
cp *.onnx app/src/main/assets/models/
```

| File | Model | Role | Licence (verify yourself) |
|---|---|---|---|
| `realesr_general_x4v3.onnx` | Real-ESRGAN general-x4v3 | tiled super-resolution + built-in denoise/deblock | BSD-3-Clause |
| `gfpgan_v1_4.onnx` | GFPGAN v1.4 | face restoration | Code Apache-2.0. **Check the weights' terms and the FFHQ training-data licence before commercial distribution.** If unsure, ship without it: the app falls back to the SR model for faces. |
| ML Kit face detection | Google ML Kit (bundled) | face boxes | SDK terms apply |

**CodeFormer is deliberately NOT used: its S-Lab licence is non-commercial.**
Without a face model everything else still works. Dev shortcut: `adb push x.onnx /data/data/com.pureenhance.ai/files/models/` (needs a debuggable build).

GFPGAN fp32 is ~340 MB, over Play's 200 MB base-module limit: put models in an install-time
**Play Asset Delivery** pack (assets there are read through the normal `AssetManager`, so no code change; they stay offline).

## Build

Open in Android Studio (Ladybug+), let it sync (it creates the Gradle wrapper), run `app`.
Release: set `RELEASE_STORE_*` in `~/.gradle/gradle.properties`, then `./gradlew bundleRelease`.
minSdk 29 (MediaStore scoped saving without storage permission), targetSdk 35, R8 + resource shrinking on.

## How it works

`ImageLoader` (bounds check, sample-size decode, EXIF) → `MemoryPlanner` (RAM tier + free memory → scale / input size) →
`ImageAnalyzer` (resolution, noise, blur, exposure, contrast, JPEG blocking, old-photo, faces) → `Recipe` (per mode) →
damage reduction + bilateral denoise → `FaceRestorer` (crop, GFPGAN, colour-match, size-gated strength) →
`TiledUpscaler` (128 px tiles, 8–24 px context, only tile interiors kept → no seams; 2× = x4 net + box downsample) →
non-destructive edit layer (`EditRenderer`: feathered face blend, auto-levels, tone, sharpen, denoise) → MediaStore save.

* Backends: NNAPI (Android 11+, mid/high tier) → XNNPACK → CPU. ONNX Runtime has no standalone Android GPU EP; GPU/NPU is reached via NNAPI. Each backend must pass a self-test (finite, non-constant output) or it is skipped and remembered, which prevents black images.
* Memory: output built tile-by-tile; budget by device tier (2 GB → 8 GB+) × quality; OOM retries with half budget and 2×; Maximum quality only on ≥ ~6 GB devices.
* Cancel: checked between tiles; temp bitmaps recycled; nothing is written to disk.
* Thermal: pauses between tiles when the device is warm, aborts with a message when critical.

## Honest limitations

* **I could not compile or run this in my environment (no Android SDK, no network, no model weights).** Expect to fix a few compile errors on first sync; unit/instrumented tests were written but not executed.
* Colourisation (optional in Old Photo mode) is **not implemented**: I found no small, clearly redistributable model I could verify. Old Photo never colours automatically.
* Scratch removal handles dust and ~1 px lines (3×3 median), not large tears.
* Face alignment is a padded box crop (no landmark warp), so very rotated faces restore less well.
* Processing runs in the app process; if Android kills it in the background, the job is lost (settings and selection are restored).
* Tests: `./gradlew test` (tiling coverage, memory planner, metrics, levels) and `./gradlew connectedAndroidTest` (seam-free tiling with a fake model, cancellation, loading, EXIF, MediaStore, analyzer, state restoration). Real-photo quality testing (blurry portrait, old photo, etc.) needs the model files and your own sample photos.
