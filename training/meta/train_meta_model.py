# training/meta/train_meta_model.py
# 💡 The meta-classifier is trained on OUTPUTS of the other 3 models.
# You run this AFTER the other 3 models are trained and validated.
# The training data is: run all 3 models on labeled samples,
# collect their probability outputs, add human-verified ground truth label.

import numpy as np
import pandas as pd
import tensorflow as tf
import sys
sys.path.append('..')
from config import *

FEATURES = [
    # Vision probabilities
    'vision_RED', 'vision_YELLOW', 'vision_GREEN', 'vision_BLACK', 'vision_STRUCTURAL',
    'vision_confidence',
    # Audio probabilities
    'audio_FIRE', 'audio_DISTRESS', 'audio_CREAK', 'audio_MACHINERY',
    'audio_SILENCE', 'audio_OUTDOOR', 'audio_confidence',
    # Motion probabilities
    'motion_COLLAPSE_UNCONSCIOUS', 'motion_COLLAPSE_MOVING', 'motion_FALL_PHONE',
    'motion_CAR_CRASH', 'motion_RUNNING', 'motion_NORMAL', 'motion_confidence',
    # Gemini signal (one-hot encoded urgency)
    'gemini_RED', 'gemini_YELLOW', 'gemini_GREEN', 'gemini_BLACK', 'gemini_confidence',
    # Context
    'hour_of_day', 'battery_level', 'gps_accuracy_m', 'is_auto_generated'
]

TARGET = 'final_urgency'
URGENCY_MAP = {'RED': 0, 'YELLOW': 1, 'GREEN': 2, 'BLACK': 3}

def generate_synthetic_meta_data(n_samples=5000):
    """
    Generate synthetic meta-classifier training data.
    💡 In production you'd collect real inference outputs + human labels.
    For the demo/hackathon, synthetic data calibrated to realistic
    signal agreement patterns works well enough.
    """
    np.random.seed(42)
    data = []

    for _ in range(n_samples):
        true_urgency = np.random.choice(['RED', 'YELLOW', 'GREEN', 'BLACK'],
                                         p=[0.3, 0.35, 0.25, 0.1])
        true_idx = URGENCY_MAP[true_urgency]

        def signal_probs(correct_class, n_classes=5, noise=0.15):
            """Generate realistic probability vector — correct class highest."""
            probs = np.random.dirichlet(np.ones(n_classes) * noise)
            probs[correct_class] += np.random.uniform(0.4, 0.8)
            return probs / probs.sum()

        vision_probs = signal_probs(true_idx)
        audio_probs = signal_probs(
            0 if true_urgency == 'RED' else 5,  # fire/distress → RED, outdoor → others
            n_classes=6, noise=0.2
        )
        motion_probs = signal_probs(
            0 if true_urgency == 'RED' else 5, n_classes=6, noise=0.3
        )
        gemini_probs = signal_probs(true_idx, n_classes=4, noise=0.1)

        row = {
            'vision_RED': vision_probs[0], 'vision_YELLOW': vision_probs[1],
            'vision_GREEN': vision_probs[2], 'vision_BLACK': vision_probs[3],
            'vision_STRUCTURAL': vision_probs[4], 'vision_confidence': vision_probs.max(),
            'audio_FIRE': audio_probs[0], 'audio_DISTRESS': audio_probs[1],
            'audio_CREAK': audio_probs[2], 'audio_MACHINERY': audio_probs[3],
            'audio_SILENCE': audio_probs[4], 'audio_OUTDOOR': audio_probs[5],
            'audio_confidence': audio_probs.max(),
            'motion_COLLAPSE_UNCONSCIOUS': motion_probs[0],
            'motion_COLLAPSE_MOVING': motion_probs[1],
            'motion_FALL_PHONE': motion_probs[2], 'motion_CAR_CRASH': motion_probs[3],
            'motion_RUNNING': motion_probs[4], 'motion_NORMAL': motion_probs[5],
            'motion_confidence': motion_probs.max(),
            'gemini_RED': gemini_probs[0], 'gemini_YELLOW': gemini_probs[1],
            'gemini_GREEN': gemini_probs[2], 'gemini_BLACK': gemini_probs[3],
            'gemini_confidence': gemini_probs.max(),
            'hour_of_day': np.random.randint(0, 24) / 24.0,
            'battery_level': np.random.uniform(0.1, 1.0),
            'gps_accuracy_m': np.random.uniform(3, 50) / 50.0,
            'is_auto_generated': float(np.random.random() < 0.2),
            TARGET: true_idx
        }
        data.append(row)

    return pd.DataFrame(data)

def build_meta_classifier():
    # 💡 Shallow MLP — the meta-classifier doesn't need to be deep.
    # Its job is just to weight the 4 input signals, not learn new features.
    inputs = tf.keras.Input(shape=(len(FEATURES),))
    x = tf.keras.layers.Dense(64, activation='relu')(inputs)
    x = tf.keras.layers.Dropout(0.2)(x)
    x = tf.keras.layers.Dense(32, activation='relu')(x)
    outputs = tf.keras.layers.Dense(4, activation='softmax')(x)
    model = tf.keras.Model(inputs, outputs)
    model.compile(
    optimizer='adam',
    loss='sparse_categorical_crossentropy',
    metrics=['accuracy']   # ← remove Precision/Recall here; compute them after training
)
    return model

def train_meta_model():
    print("Generating meta-classifier training data...")
    df = generate_synthetic_meta_data(5000)

    X = df[FEATURES].values.astype(np.float32)
    y = df[TARGET].values.astype(np.int32)

    split = int(len(X) * 0.8)
    X_train, X_val = X[:split], X[split:]
    y_train, y_val = y[:split], y[split:]

    model = build_meta_classifier()
    model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=50, batch_size=128,
        callbacks=[tf.keras.callbacks.EarlyStopping(patience=5, restore_best_weights=True)]
    )

    val_loss, val_acc = model.evaluate(X_val, y_val)
    print(f"\nValidation accuracy: {val_acc:.3f}")

    print("Exporting to SavedModel...")
    model.export('meta_saved_model')
    print("Exporting to TFLite...")
    converter = tf.lite.TFLiteConverter.from_saved_model('meta_saved_model')
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()

    output_path = f"{MODELS_DIR}omnimesh_meta_classifier_v1.tflite"
    with open(output_path, 'wb') as f:
        f.write(tflite_model)

    size_mb = len(tflite_model) / (1024 * 1024)
    print(f"✅ Meta-classifier: {output_path} ({size_mb:.1f} MB)")

if __name__ == "__main__":
    train_meta_model()
