#!/usr/bin/env python3
"""
Converts the two PyTorch checkpoints to the ONNX files PureEnhance AI loads.

  pip install torch onnx onnxruntime numpy realesrgan gfpgan basicsr
  python scripts/convert_models.py --realesrgan realesr-general-x4v3.pth --gfpgan GFPGANv1.4.pth

Outputs (copy into app/src/main/assets/models/):
  realesr_general_x4v3.onnx   static input 1x3x128x128  -> 1x3x512x512   (TILE = 128 in ModelSpecs.kt)
  gfpgan_v1_4.onnx            static input 1x3x512x512 (range -1..1) -> same size

Both exports are verified against PyTorch with onnxruntime before the script exits successfully.
"""
import argparse
import numpy as np
import torch
import onnxruntime as ort


def verify(onnx_path, model, shape, atol):
    x = torch.rand(*shape) * (2 if "gfpgan" in onnx_path else 1) - (1 if "gfpgan" in onnx_path else 0)
    with torch.no_grad():
        ref = model(x).numpy()
    sess = ort.InferenceSession(onnx_path, providers=["CPUExecutionProvider"])
    out = sess.run(None, {sess.get_inputs()[0].name: x.numpy()})[0]
    err = float(np.abs(ref - out).max())
    print(f"{onnx_path}: max abs diff vs PyTorch = {err:.5f}")
    if not np.isfinite(out).all() or err > atol:
        raise SystemExit(f"verification failed for {onnx_path}")


def export_sr(path, out):
    from realesrgan.archs.srvgg_arch import SRVGGNetCompact
    m = SRVGGNetCompact(num_in_ch=3, num_out_ch=3, num_feat=64, num_conv=32, upscale=4, act_type="prelu")
    sd = torch.load(path, map_location="cpu")
    sd = sd.get("params_ema", sd.get("params", sd))
    m.load_state_dict(sd, strict=True)
    m.eval()
    torch.onnx.export(m, torch.zeros(1, 3, 128, 128), out, input_names=["input"], output_names=["output"],
                      opset_version=17, do_constant_folding=True)
    verify(out, m, (1, 3, 128, 128), 5e-3)


def export_face(path, out):
    from gfpgan.archs.gfpganv1_clean_arch import GFPGANv1Clean
    net = GFPGANv1Clean(out_size=512, num_style_feat=512, channel_multiplier=2, decoder_load_path=None,
                        fix_decoder=False, num_mlp=8, input_is_latent=True, different_w=True, narrow=1, sft_half=True)
    sd = torch.load(path, map_location="cpu")
    net.load_state_dict(sd.get("params_ema", sd.get("params", sd)), strict=True)
    net.eval()

    class Wrap(torch.nn.Module):
        def __init__(self, n):
            super().__init__()
            self.n = n

        def forward(self, x):
            return self.n(x, return_rgb=False, randomize_noise=False)[0]

    w = Wrap(net).eval()
    torch.onnx.export(w, torch.zeros(1, 3, 512, 512), out, input_names=["input"], output_names=["output"],
                      opset_version=17, do_constant_folding=True)
    verify(out, w, (1, 3, 512, 512), 5e-2)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--realesrgan", required=True, help="realesr-general-x4v3.pth")
    ap.add_argument("--gfpgan", help="GFPGANv1.4.pth (optional: without it faces use the standard model)")
    a = ap.parse_args()
    export_sr(a.realesrgan, "realesr_general_x4v3.onnx")
    if a.gfpgan:
        export_face(a.gfpgan, "gfpgan_v1_4.onnx")
