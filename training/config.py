# training/config.py

PROJECT_ID = "omnimesh-command"
LOCATION = "us-central1"
BUCKET_URI = "gs://omnimesh-training-data"
MODEL_BUCKET = "gs://omnimesh-models"

# Vertex AI training
VISION_DATASET_NAME = "omnimesh_injury_triage_v1"
AUDIO_MODEL_NAME = "omnimesh_audio_classifier_v1"
MOTION_MODEL_NAME = "omnimesh_motion_classifier_v1"
META_MODEL_NAME = "omnimesh_meta_classifier_v1"

# Model output paths (local)
MODELS_DIR = "../app/src/main/assets/"

# Training data paths in GCS
VISION_DATA_CSV = f"{BUCKET_URI}/vision/data.csv"
AUDIO_DATA_DIR = f"{BUCKET_URI}/audio/"
MOTION_DATA_DIR = f"{BUCKET_URI}/motion/"
META_DATA_CSV = f"{BUCKET_URI}/meta/labels.csv"

# Triage classes
URGENCY_CLASSES = ["RED", "YELLOW", "GREEN", "BLACK", "STRUCTURAL"]
AUDIO_CLASSES = ["FIRE_CRACKLING", "HUMAN_DISTRESS", "STRUCTURAL_CREAK",
                 "MACHINERY", "SILENCE_ENCLOSED", "AMBIENT_OUTDOOR"]
MOTION_CLASSES = ["COLLAPSE_UNCONSCIOUS", "COLLAPSE_MOVING", "FALL_PHONE",
                  "CAR_CRASH", "RUNNING", "NORMAL"]
