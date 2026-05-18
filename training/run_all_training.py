# training/run_all_training.py
# Run this to train all models in correct order

import subprocess
import sys
import os

def run(script: str):
    print(f"\n{'='*50}")
    print(f"RUNNING: {script}")
    print('='*50)
    result = subprocess.run([sys.executable, script], capture_output=False)
    if result.returncode != 0:
        print(f"❌ FAILED: {script}")
        sys.exit(1)
    print(f"✅ DONE: {script}")

if __name__ == "__main__":
    os.chdir(os.path.dirname(__file__))

    # Order matters — meta-classifier trains last
    run("vision/auto_label_images.py")    # 1. Label raw images
    run("vision/train_vision_model.py")   # 2. Train vision classifier
    run("audio/train_audio_model.py")     # 3. Train audio classifier
    run("motion/train_motion_model.py")   # 4. Train motion LSTM
    run("meta/train_meta_model.py")       # 5. Train meta-classifier last

    print("\n🎉 ALL MODELS TRAINED AND SAVED TO assets/")
    print("Models ready:")
    import os
    assets = "../app/src/main/assets/"
    for f in os.listdir(assets):
        if f.endswith('.tflite'):
            size = os.path.getsize(os.path.join(assets, f)) / (1024*1024)
            print(f"  {f} ({size:.1f} MB)")
