"""
Validates all three TFLite models are present and producing
sensible outputs before you build the APK.
"""
import numpy as np
import os
import sys

_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))


def _resolve_assets_dir() -> str:
    """Find app/src/main/assets whether you run this from repo root or from training/."""
    candidates = [
        os.path.join(_SCRIPT_DIR, "app", "src", "main", "assets"),
        os.path.join(os.path.dirname(_SCRIPT_DIR), "app", "src", "main", "assets"),
    ]
    for c in candidates:
        if os.path.isdir(c):
            return c
    return candidates[-1]


ASSETS_PATH = _resolve_assets_dir()


def _parse_tf_version() -> tuple[int, int, int]:
    import tensorflow as tf

    parts = tf.__version__.split(".")
    nums: list[int] = []
    for p in parts[:3]:
        n = ""
        for ch in p:
            if ch.isdigit():
                n += ch
            else:
                break
        nums.append(int(n) if n else 0)
    while len(nums) < 3:
        nums.append(0)
    return nums[0], nums[1], nums[2]


def _require_tensorflow_for_models() -> None:
    """These assets use TFLite ops (e.g. FULLY_CONNECTED v12) that need TF ≥ 2.17."""
    try:
        import tensorflow as tf
    except ImportError:
        print("TensorFlow is not installed. Install with:")
        print(f"  {sys.executable} -m pip install -r {_SCRIPT_DIR}/requirements-validate.txt")
        sys.exit(2)
    ver = tf.__version__
    major, minor, _ = _parse_tf_version()
    if (major, minor) < (2, 17):
        print(f"TensorFlow: {ver} — too old for the bundled .tflite files.")
        print("Upgrade the same interpreter you are using now:")
        print(f"  {sys.executable} -m pip install -U 'tensorflow>=2.17'")
        print("Or:")
        print(f"  {sys.executable} -m pip install -r {_SCRIPT_DIR}/requirements-validate.txt")
        print("(Then re-run this script.)")
        sys.exit(2)


def load_interpreter(*filename_candidates: str):
    """Load first existing file among filename_candidates (e.g. display name vs export name)."""
    try:
        import tensorflow as tf
        path = None
        for name in filename_candidates:
            p = os.path.join(ASSETS_PATH, name)
            if os.path.exists(p):
                path = p
                break
        if path is None:
            tried = ", ".join(filename_candidates)
            return None, (
                f"FILE NOT FOUND under {ASSETS_PATH} (tried: {tried}). "
                f"Run from repo root or training/ — path is resolved from this script."
            )
        interp = tf.lite.Interpreter(model_path=path)
        interp.allocate_tensors()
        return interp, None
    except Exception as e:
        msg = str(e)
        if "FULLY_CONNECTED" in msg and "version" in msg:
            msg += (
                "\n  → Your TensorFlow is older than the TFLite runtime that built this model. "
                "Upgrade: pip install -U 'tensorflow>=2.17' "
                "(see training/requirements-validate.txt)."
            )
        return None, msg

def run(interp, input_data):
    inp = interp.get_input_details()
    out = interp.get_output_details()
    interp.set_tensor(inp[0]['index'], input_data)
    interp.invoke()
    result = interp.get_tensor(out[0]['index'])
    # If output has a batch dimension [1, N], squeeze it
    # If output is already [N], return as-is
    if result.ndim > 1:
        return result[0]
    return result

print("=" * 55)
print("OmniMesh Model Validation")
print("=" * 55)
print(f"Assets: {ASSETS_PATH}")
_require_tensorflow_for_models()
import tensorflow as tf

print(f"TensorFlow: {tf.__version__}")

all_passed = True

# ── Motion model ──────────────────────────────────────────────────────────────
print("\n[1] Motion LSTM")
interp, err = load_interpreter(
    "Omnimesh Motion Classifier v1.tflite",
    "omnimesh_motion_classifier_v1.tflite",
)
if err:
    print(f"  ✗ {err}")
    all_passed = False
else:
    inp_shape = interp.get_input_details()[0]['shape']
    print(f"  Input shape: {inp_shape}")
    
    # Synthetic collapse signature
    data = np.zeros((1, 250, 6), dtype=np.float32)
    data[0, 100:108, :3] = 15.0   # multi-axis spike
    data[0, 108:150, :]  = np.random.normal(0, 0.5, (42, 6))
    data[0, 150:, :]     = np.random.normal(0, 0.05, (100, 6))
    
    probs = run(interp, data)
    classes = ['COLLAPSE_UNCONSCIOUS','COLLAPSE_MOVING',
               'FALL_PHONE','CAR_CRASH','RUNNING','NORMAL']
    predicted = classes[np.argmax(probs)]
    conf = np.max(probs)
    
    passed = predicted == 'COLLAPSE_UNCONSCIOUS' and conf > 0.5
    status = "✓" if passed else "✗"
    print(f"  {status} Collapse test → {predicted} ({conf:.2f})")
    
    # Normal walking
    data2 = np.zeros((1, 250, 6), dtype=np.float32)
    t = np.linspace(0, 5, 250)
    data2[0, :, 2] = np.sin(2 * np.pi * 2 * t) * 1.2
    data2 = data2 + np.random.normal(0, 0.1, data2.shape)
    
    probs2 = run(interp, data2.astype(np.float32))
    pred2 = classes[np.argmax(probs2)]
    passed2 = pred2 == 'NORMAL'
    status2 = "✓" if passed2 else "✗"
    print(f"  {status2} Normal walk → {pred2} ({np.max(probs2):.2f})")
    
    if not (passed and passed2):
        all_passed = False

# ── Audio model ───────────────────────────────────────────────────────────────
print("\n[2] Audio Classifier")
interp, err = load_interpreter(
    "Omnimesh Audio Classifier v1.tflite",
    "omnimesh_audio_classifier_v1.tflite",
)
if err:
    print(f"  ✗ {err}")
    all_passed = False
else:
    inp_detail = interp.get_input_details()[0]
    out_detail = interp.get_output_details()[0]
    print(f"  Input shape:  {inp_detail['shape']}")
    print(f"  Output shape: {out_detail['shape']}")
    
    # Feed random audio — just verify it runs without error
    input_shape = inp_detail['shape']
    dummy_audio = np.random.uniform(-1, 1, input_shape).astype(np.float32)
    
    try:
        probs = run(interp, dummy_audio)
        print(f"  ✓ Inference runs — output shape {probs.shape}, "
              f"sum={probs.sum():.3f}")
        if abs(probs.sum() - 1.0) > 0.01:
            print("  ✗ WARNING: probabilities don't sum to 1.0")
            all_passed = False
    except Exception as e:
        print(f"  ✗ Inference failed: {e}")
        all_passed = False

# ── Meta-classifier ───────────────────────────────────────────────────────────
print("\n[3] Meta-Classifier")
interp, err = load_interpreter(
    "Omnimesh Meta Classifier v1.tflite",
    "omnimesh_meta_classifier_v1.tflite",
)
if err:
    print(f"  ✗ {err}")
    all_passed = False
else:
    inp_shape = interp.get_input_details()[0]['shape']
    print(f"  Input shape: {inp_shape}")
    
    # Simulate a clear RED auto-SOS input
    # Vision: mostly RED
    vis  = np.array([0.85, 0.08, 0.04, 0.02, 0.01], dtype=np.float32)
    # Audio: distress
    aud  = np.array([0.05, 0.80, 0.05, 0.03, 0.04, 0.03], dtype=np.float32)
    # Motion: collapse unconscious
    mot  = np.array([0.91, 0.04, 0.02, 0.01, 0.01, 0.01], dtype=np.float32)
    # Gemini high confidence + auto context
    ctx  = np.array([0.88, 0.5, 0.9, 0.1, 1.0], dtype=np.float32)
    
    features = np.concatenate([vis, aud, mot, ctx]).reshape(1, -1)
    
    # Check feature count matches model expectation
    expected_features = inp_shape[1] if len(inp_shape) > 1 else inp_shape[0]
    if features.shape[1] != expected_features:
        print(f"  ✗ Feature mismatch: got {features.shape[1]}, "
              f"model expects {expected_features}")
        all_passed = False
    else:
        probs = run(interp, features)
        labels = ['RED', 'YELLOW', 'GREEN', 'BLACK']
        predicted = labels[np.argmax(probs)]
        conf = np.max(probs)
        passed = predicted == 'RED' and conf > 0.6
        status = "✓" if passed else "✗"
        print(f"  {status} RED scenario → {predicted} ({conf:.2f})")
        if not passed:
            all_passed = False

# ── Summary ───────────────────────────────────────────────────────────────────
print("\n" + "=" * 55)
if all_passed:
    print("✓ ALL MODELS VALIDATED — ready to build APK")
else:
    print("✗ SOME MODELS FAILED — check output above")
    print("  Motion or meta failures → retrain on Colab")
    print("  Audio shape error → check export cell in notebook")
print("=" * 55)