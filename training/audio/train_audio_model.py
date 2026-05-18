# training/audio/train_audio_model.py

import tensorflow as tf
import tensorflow_hub as hub
import numpy as np
import os
import sys
sys.path.append('..')
from config import *

SAMPLE_RATE = 16000
CLIP_DURATION_S = 3
SAMPLES_PER_CLIP = SAMPLE_RATE * CLIP_DURATION_S  # 48000

def load_audio_dataset(data_dir: str):
    """
    Load .wav files organized as:
    data_dir/
      FIRE_CRACKLING/  ← .wav files
      HUMAN_DISTRESS/
      STRUCTURAL_CREAK/
      MACHINERY/
      SILENCE_ENCLOSED/
      AMBIENT_OUTDOOR/
    """
    import librosa

    X, y = [], []
    for class_idx, class_name in enumerate(AUDIO_CLASSES):
        class_dir = os.path.join(data_dir, class_name)
        if not os.path.exists(class_dir):
            print(f"Warning: {class_dir} not found")
            continue

        files = [f for f in os.listdir(class_dir) if f.endswith('.wav')]
        print(f"  {class_name}: {len(files)} files")

        for fname in files:
            path = os.path.join(class_dir, fname)
            try:
                audio, sr = librosa.load(path, sr=SAMPLE_RATE, mono=True,
                                         duration=CLIP_DURATION_S)
                # Pad or trim to exactly SAMPLES_PER_CLIP
                if len(audio) < SAMPLES_PER_CLIP:
                    audio = np.pad(audio, (0, SAMPLES_PER_CLIP - len(audio)))
                else:
                    audio = audio[:SAMPLES_PER_CLIP]

                X.append(audio)
                y.append(class_idx)
            except Exception as e:
                print(f"    Skip {fname}: {e}")

    return np.array(X, dtype=np.float32), np.array(y, dtype=np.int32)

def build_fine_tuned_yamnet():
    """YAMNet frozen as feature extractor + disaster-specific head."""
    waveform_input = tf.keras.Input(shape=(SAMPLES_PER_CLIP,), name='waveform')

    # 💡 trainable=False freezes all YAMNet weights.
    # We only train the new classification head.
    # This means we need far less data and training time.
    yamnet = hub.KerasLayer(
        'https://tfhub.dev/google/yamnet/1',
        trainable=False,
        name='yamnet_frozen'
    )

    _, embeddings, _ = yamnet(waveform_input)
    pooled = tf.keras.layers.GlobalAveragePooling1D()(
        tf.expand_dims(embeddings, 0)
    )

    x = tf.keras.layers.Dense(256, activation='relu')(pooled)
    x = tf.keras.layers.Dropout(0.3)(x)
    x = tf.keras.layers.Dense(128, activation='relu')(x)
    x = tf.keras.layers.Dropout(0.2)(x)
    output = tf.keras.layers.Dense(
        len(AUDIO_CLASSES), activation='softmax', name='disaster_class'
    )(x)

    return tf.keras.Model(inputs=waveform_input, outputs=output)

def train_audio_model():
    print("Loading audio dataset...")
    X, y = load_audio_dataset("./audio_data/")

    # Train/val split
    split = int(len(X) * 0.8)
    X_train, X_val = X[:split], X[split:]
    y_train, y_val = y[:split], y[split:]

    print(f"Train: {len(X_train)}, Val: {len(X_val)}")

    model = build_fine_tuned_yamnet()
    model.compile(
        optimizer=tf.keras.optimizers.Adam(1e-4),
        loss='sparse_categorical_crossentropy',
        metrics=['accuracy', tf.keras.metrics.AUC(name='auc')]
    )
    model.summary()

    callbacks = [
        tf.keras.callbacks.EarlyStopping(patience=5, restore_best_weights=True),
        tf.keras.callbacks.ReduceLROnPlateau(factor=0.5, patience=3),
        tf.keras.callbacks.ModelCheckpoint(
            'best_audio_model.keras', save_best_only=True
        )
    ]

    model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=50,
        batch_size=32,
        callbacks=callbacks
    )

    print("\nExporting to SavedModel...")
    export_audio_tflite(model, X_train[:100])

def export_audio_tflite(model, calibration_data):
    model.export('audio_saved_model')
    print("Exporting to TFLite...")
    converter = tf.lite.TFLiteConverter.from_saved_model('audio_saved_model')
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    converter.inference_input_type = tf.int8
    converter.inference_output_type = tf.int8

    def representative_dataset():
        for sample in calibration_data:
            yield [sample.reshape(1, -1).astype(np.float32)]

    converter.representative_dataset = representative_dataset
    tflite_model = converter.convert()

    output_path = f"{MODELS_DIR}omnimesh_audio_classifier_v1.tflite"
    with open(output_path, 'wb') as f:
        f.write(tflite_model)

    size_mb = len(tflite_model) / (1024 * 1024)
    print(f"✅ Audio model: {output_path} ({size_mb:.1f} MB)")

if __name__ == "__main__":
    train_audio_model()
