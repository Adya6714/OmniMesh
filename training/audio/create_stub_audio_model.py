import tensorflow as tf
import os
import sys

sys.path.append('.')
from config import *

# Ensure the output directory exists
os.makedirs(MODELS_DIR, exist_ok=True)

class StubAudioModel(tf.Module):
    # The audio model takes a waveform input of SAMPLES_PER_CLIP
    @tf.function(input_signature=[tf.TensorSpec(shape=[None, 48000], dtype=tf.float32)])
    def __call__(self, x):
        # We need to return probabilities for the 6 audio classes:
        # ["FIRE_CRACKLING", "HUMAN_DISTRESS", "STRUCTURAL_CREAK", "MACHINERY", "SILENCE_ENCLOSED", "AMBIENT_OUTDOOR"]
        # Return AMBIENT_OUTDOOR (index 5) with high confidence.
        batch_size = tf.shape(x)[0]
        preds = tf.constant([[0.05, 0.05, 0.05, 0.05, 0.05, 0.75]], dtype=tf.float32)
        return tf.tile(preds, [batch_size, 1])

model = StubAudioModel()
converter = tf.lite.TFLiteConverter.from_concrete_functions([model.__call__.get_concrete_function()])
tflite_model = converter.convert()

output_path = os.path.join(MODELS_DIR, "omnimesh_audio_classifier_v1.tflite")
with open(output_path, 'wb') as f:
    f.write(tflite_model)

size_mb = len(tflite_model) / (1024 * 1024)
print(f"✅ Stub Audio model: {output_path} ({size_mb:.1f} MB)")
