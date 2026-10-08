# PureEnhance AI: dobara upload karne ka tareeqa

Is zip mein sab kuch hai: app ka code, `.github/workflows/build.yml` (APK banane wala workflow),
ANR fix, model converter notebook (`tools/`) aur `upload.sh`.
**AI models (.onnx) is zip mein nahi hain** (340 MB, GitHub par nahi chadhti). Wo GitHub Release `models-v1` mein pehle se hain.

## Termux mein (naya zip aane par)
```
cd ~
unzip -o ~/storage/downloads/PureEnhanceAI_FINAL.zip
cd PureEnhanceAI
sh upload.sh "update"
```
`unzip -o` purani files ko overwrite karta hai. Aap ka `.git` aur `app/src/main/assets/models/` waali models safe rehti hain.
Username `talhaluxury`, password mein GitHub token.

## APK banana
GitHub → repo → Actions → Build APK → Run workflow → 5 minute baad run kholein → Artifacts → `PureEnhanceAI-debug-apk` download → extract → install.

## Agar naye phone/repo par shuru karna ho
1. github.com par khali repo `PureEnhanceAI` banayein.
2. `tools/PureEnhance_model_converter_v4.ipynb` Colab mein chalayein, `.onnx` files Drive se download karein.
3. Repo ke Releases mein tag `models-v1` banakar dono files attach karein
   (naam bilkul `realesr_general_x4v3.onnx` aur `gfpgan_v1_4.onnx` hon).
4. Upar wala Termux tareeqa.
