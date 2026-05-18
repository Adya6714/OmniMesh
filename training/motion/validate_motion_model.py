"""Validate TFLite motion classifier — synthetic probes.

Run (from repo root):

  .venv-validate/bin/python training/motion/validate_motion_model.py

If you see ``mutex lock failed`` / ``zsh: abort`` when using plain ``python``,
your *global* TensorFlow wheel is crashing during native init (common with some
``tensorflow`` builds on Apple Silicon). Use the repo venv above (TensorFlow
2.16.x macOS wheel) or ``tensorflow-macos`` in a clean virtualenv.

One-time venv setup from repo root::

  python3 -m venv .venv-validate
  .venv-validate/bin/pip install --upgrade pip
  .venv-validate/bin/pip install numpy==1.26.4 tensorflow-macos==2.16.1
"""

import os

# TensorFlow on macOS can abort with libc++ "mutex lock failed" during thread-pool
# init if the process inherits aggressive OpenMP/BLAS threading. Pin before TF loads.
os.environ.setdefault("OMP_NUM_THREADS", "1")
os.environ.setdefault("MKL_NUM_THREADS", "1")
os.environ.setdefault("TF_NUM_INTRAOP_THREADS", "1")
os.environ.setdefault("TF_NUM_INTEROP_THREADS", "1")
os.environ.setdefault("TF_ENABLE_ONEDNN_OPTS", "0")

import numpy as np


def load_tflite_model(model_path):
    try:
        import tensorflow as tf

        interpreter = tf.lite.Interpreter(model_path=model_path)
        interpreter.allocate_tensors()
        return interpreter
    except ImportError:
        print("pip install tensorflow")
        return None

def run_inference(interpreter, sample):
    """Run inference on a single [250, 6] sample"""
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    
    # Reshape to [1, 250, 6] — batch size 1
    input_data = sample.reshape(1, 250, 6).astype(np.float32)
    interpreter.set_tensor(input_details[0]['index'], input_data)
    interpreter.invoke()
    output = interpreter.get_tensor(output_details[0]['index'])
    return output[0]

CLASS_NAMES = [
    'COLLAPSE_UNCONSCIOUS',
    'COLLAPSE_MOVING', 
    'FALL_PHONE',
    'CAR_CRASH',
    'RUNNING',
    'NORMAL'
]

def generate_test_collapse():
    """Synthetic collapse: spike then stiless"""
    data = np.zeros((250, 6))
    # Normal baseline for first 2 seconds
    data[:100] = np.random.normal(0, 0.1, (100, 6))
    # Massive spike at sample 100 — all axes simultaneously
    data[100:110] = np.random.uniform(8, 25, (10, 6))
    # Vibration decay
    for i in range(110, 150):
        decay = 1.0 - (i - 110) / 40.0
        data[i] = np.random.normal(0, decay * 2, 6)
    # Sustained stillness
    data[150:] = np.random.normal(0, 0.05, (100, 6))
    return data

def generate_test_drop():
    """Phone drop: spike then resumed movement"""
    data = np.zeros((250, 6))
    data[:100] = np.random.normal(0, 0.2, (100, 6))
    # Spike on only 1-2 axes (not all simultaneously like collapse)
    data[100:105, 2] = np.random.uniform(5, 12, 5)  # Z axis only
    data[105:] = np.random.normal(0, 0.3, (145, 6))  # resumes movement
    return data

def generate_test_normal():
    """Normal walking"""
    data = np.zeros((250, 6))
    t = np.linspace(0, 5, 250)
    data[:, 2] = np.sin(2 * np.pi * 2 * t) * 1.2  # walking cadence ~2Hz
    data += np.random.normal(0, 0.1, (250, 6))
    return data

_assets = os.path.normpath(
    os.path.join(os.path.dirname(__file__), "../../app/src/main/assets")
)
_model_names = (
    "Omnimesh Motion Classifier v1.tflite",
    "omnimesh_motion_classifier_v1.tflite",
)
model_path = next(
    (os.path.join(_assets, n) for n in _model_names if os.path.exists(os.path.join(_assets, n))),
    None,
)
if not model_path:
    print(f"Model not found in {_assets} (tried: {', '.join(_model_names)})")
    exit(1)

interpreter = load_tflite_model(model_path)
if interpreter is None:
    exit(1)

print("Running validation tests on motion model...\n")

tests = [
    ("Collapse (should → COLLAPSE_UNCONSCIOUS)", generate_test_collapse(), 
     'COLLAPSE_UNCONSCIOUS'),
    ("Phone drop (should → FALL_PHONE)", generate_test_drop(), 
     'FALL_PHONE'),
    ("Normal walk (should → NORMAL)", generate_test_normal(), 
     'NORMAL'),
]

passed = 0
for test_name, sample, expected in tests:
    probs = run_inference(interpreter, sample)
    predicted_idx = np.argmax(probs)
    predicted = CLASS_NAMES[predicted_idx]
    confidence = probs[predicted_idx]
    correct = predicted == expected
    if correct:
        passed += 1
    status = "✓ PASS" if correct else "✗ FAIL"
    print(f"{status} | {test_name}")
    print(f"       Predicted: {predicted} ({confidence:.2f} confidence)")
    print(f"       All probs: "
          f"{dict(zip(CLASS_NAMES, [f'{p:.2f}' for p in probs]))}")
    print()

print(f"Result: {passed}/{len(tests)} tests passed")
if passed < 2:
    print("Model needs retraining — run train_motion_model.py")
else:
    print("Model looks functional for demo use")

