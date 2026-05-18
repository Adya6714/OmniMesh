import os
import tensorflow as tf
import sys

sys.path.append('.')
from config import *

print("Loading best_motion.keras...")
model = tf.keras.models.load_model('best_motion.keras')

print("\nExporting to SavedModel...")
model.export('motion_saved_model')

print("\nConverting from SavedModel to TFLite...")
converter = tf.lite.TFLiteConverter.from_saved_model('motion_saved_model')
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS, tf.lite.OpsSet.SELECT_TF_OPS]
converter._experimental_lower_tensor_list_ops = False
tflite_model = converter.convert()

output_path = f"{MODELS_DIR}omnimesh_motion_classifier_v1.tflite"
os.makedirs(MODELS_DIR, exist_ok=True)
with open(output_path, 'wb') as f:
    f.write(tflite_model)

size_mb = len(tflite_model) / (1024 * 1024)
print(f"✅ Motion model: {output_path} ({size_mb:.1f} MB)")
