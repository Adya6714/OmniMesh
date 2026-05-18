# training/motion/train_motion_model.py

import tensorflow as tf
import numpy as np
import sys
sys.path.append('..')
from config import *

SEQUENCE_LENGTH = 250   # 5 seconds at 50Hz
FEATURES = 6            # ax, ay, az, gx, gy, gz
NUM_CLASSES = len(MOTION_CLASSES)

# ─────────────────────────────────────────────
# SYNTHETIC DATA GENERATORS
# 💡 Real collapse accelerometer data is extremely rare.
# We generate physically accurate synthetic data based on
# building collapse dynamics from structural engineering literature.
# ─────────────────────────────────────────────

def generate_collapse_unconscious(n=500):
    samples = []
    for _ in range(n):
        s = np.random.normal(0, 0.1, (SEQUENCE_LENGTH, FEATURES))
        spike = np.random.randint(60, 100)

        # Multi-axis spike (all 3 accel axes simultaneously — collapse signature)
        for axis in range(3):
            amplitude = np.random.uniform(8, 25)
            width = np.random.randint(3, 8)
            s[spike:spike+width, axis] += amplitude * np.random.randn(width)

        # Vibration decay
        for i in range(50):
            decay = np.exp(-i / 15)
            s[spike+8+i, :3] *= (1 + decay * np.random.randn(3) * 1.5)

        # Post-collapse stillness (key differentiator)
        s[spike+60:, :] = np.random.normal(0, 0.05, (SEQUENCE_LENGTH - spike - 60, FEATURES))
        samples.append(s)
    return np.array(samples, dtype=np.float32)

def generate_phone_drop(n=500):
    samples = []
    for _ in range(n):
        s = np.random.normal(0, 0.15, (SEQUENCE_LENGTH, FEATURES))
        spike = np.random.randint(50, 150)

        # 💡 Phone drop: spike on 1-2 axes only, then normal movement resumes
        axis = np.random.randint(0, 3)
        s[spike:spike+3, axis] += np.random.uniform(6, 15) * np.random.randn(3)

        # Movement resumes after drop (person picks it up)
        s[spike+20:spike+60, :3] += np.random.normal(0, 0.5, (40, 3))
        samples.append(s)
    return np.array(samples, dtype=np.float32)

def generate_normal(n=500):
    """Normal walking/sitting movement."""
    samples = []
    for _ in range(n):
        t = np.linspace(0, 5, SEQUENCE_LENGTH)
        s = np.zeros((SEQUENCE_LENGTH, FEATURES))
        # Simulate walking gait frequency (~2Hz)
        s[:, 0] = 0.3 * np.sin(2 * np.pi * 2 * t) + np.random.normal(0, 0.1, SEQUENCE_LENGTH)
        s[:, 1] = 0.2 * np.sin(2 * np.pi * 2 * t + 0.5) + np.random.normal(0, 0.1, SEQUENCE_LENGTH)
        s[:, 2] = 1.0 + 0.1 * np.sin(2 * np.pi * 1 * t) + np.random.normal(0, 0.05, SEQUENCE_LENGTH)
        s[:, 3:] = np.random.normal(0, 0.05, (SEQUENCE_LENGTH, 3))
        samples.append(s)
    return np.array(samples, dtype=np.float32)

def generate_car_crash(n=300):
    samples = []
    for _ in range(n):
        s = np.random.normal(0, 0.2, (SEQUENCE_LENGTH, FEATURES))
        spike = np.random.randint(50, 150)
        # Car crash: large spike then irregular movement (airbag, door opening)
        for axis in range(3):
            s[spike:spike+5, axis] += np.random.uniform(5, 20) * np.random.randn(5)
        # Movement continues after crash (person conscious, moving)
        s[spike+10:, :3] += np.random.normal(0, 0.3, (SEQUENCE_LENGTH - spike - 10, 3))
        samples.append(s)
    return np.array(samples, dtype=np.float32)

def generate_running(n=400):
    samples = []
    for _ in range(n):
        t = np.linspace(0, 5, SEQUENCE_LENGTH)
        s = np.zeros((SEQUENCE_LENGTH, FEATURES))
        freq = np.random.uniform(2.5, 3.5)  # running cadence
        s[:, 0] = 0.8 * np.sin(2 * np.pi * freq * t) + np.random.normal(0, 0.2, SEQUENCE_LENGTH)
        s[:, 1] = 0.6 * np.sin(2 * np.pi * freq * t + 1) + np.random.normal(0, 0.15, SEQUENCE_LENGTH)
        s[:, 2] = 1.0 + 0.4 * np.abs(np.sin(2 * np.pi * freq * t)) + np.random.normal(0, 0.1, SEQUENCE_LENGTH)
        s[:, 3:] = np.random.normal(0, 0.15, (SEQUENCE_LENGTH, 3))
        samples.append(s)
    return np.array(samples, dtype=np.float32)

def generate_collapse_moving(n=300):
    """Collapse but person is conscious and moving."""
    samples = []
    for _ in range(n):
        s = generate_collapse_unconscious(1)[0].copy()
        # Override stillness period with some movement
        stillness_start = 130
        s[stillness_start:, :3] += np.random.normal(0, 0.2, (SEQUENCE_LENGTH - stillness_start, 3))
        samples.append(s)
    return np.array(samples, dtype=np.float32)

def build_motion_lstm():
    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=(SEQUENCE_LENGTH, FEATURES)),
        # 💡 Bidirectional LSTM processes sequence forward AND backward.
        # The "aftermath" of a collapse (stillness) is as diagnostic
        # as the spike itself — bidirectional captures both.
        tf.keras.layers.Bidirectional(
            tf.keras.layers.LSTM(128, return_sequences=True,
                                  dropout=0.2, recurrent_dropout=0.1)
        ),
        tf.keras.layers.Bidirectional(
            tf.keras.layers.LSTM(64, return_sequences=False, dropout=0.2)
        ),
        tf.keras.layers.Dense(64, activation='relu'),
        tf.keras.layers.Dropout(0.3),
        tf.keras.layers.Dense(NUM_CLASSES, activation='softmax')
    ])

    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=0.001),
        loss='sparse_categorical_crossentropy',
        metrics=['accuracy']
    )
    return model

def train_motion_model():
    print("Generating synthetic training data...")

    X_parts = [
        generate_collapse_unconscious(500),
        generate_collapse_moving(300),
        generate_phone_drop(500),
        generate_car_crash(300),
        generate_running(400),
        generate_normal(500)
    ]
    y_parts = [np.full(len(x), i) for i, x in enumerate(X_parts)]

    X = np.concatenate(X_parts)
    y = np.concatenate(y_parts)

    # Shuffle
    idx = np.random.permutation(len(X))
    X, y = X[idx], y[idx]

    split = int(len(X) * 0.8)
    X_train, X_val = X[:split], X[split:]
    y_train, y_val = y[:split], y[split:]

    print(f"Total samples: {len(X)} | Train: {len(X_train)} | Val: {len(X_val)}")

    model = build_motion_lstm()
    model.summary()

    model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=100,
        batch_size=64,
        callbacks=[
            tf.keras.callbacks.EarlyStopping(
                monitor='val_accuracy',
                patience=10,
                restore_best_weights=True
            ),
            tf.keras.callbacks.ReduceLROnPlateau(
                monitor='val_loss',
                factor=0.5,
                patience=5
            )
        ]
    )

    print("\nExporting to TFLite...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()

    output_path = f"{MODELS_DIR}omnimesh_motion_classifier_v1.tflite"
    with open(output_path, 'wb') as f:
        f.write(tflite_model)

    size_mb = len(tflite_model) / (1024 * 1024)
    print(f"✅ Motion model: {output_path} ({size_mb:.1f} MB)")

if __name__ == "__main__":
    train_motion_model()
