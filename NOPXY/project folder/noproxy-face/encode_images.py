import warnings
warnings.filterwarnings("ignore", category=UserWarning)

import face_recognition
import cv2
import numpy as np
import os
import pickle
import re

STUDENTS_DIR = "students"
ENCODINGS_FILE = "data/encodings.pkl"


def normalize_name(name: str) -> str:
    """Normalize name for consistent matching and deduplication."""
    name = os.path.splitext(name)[0]
    name = re.sub(r"[_\-\d]+", " ", name)      # remove underscores, dashes, and numbers
    name = re.sub(r"\s+", " ", name).strip()
    return name.title()                        # Title case: 'me final' -> 'Me Final'


def encode_all_faces():
    print(f"\n🧠 Rebuilding face encodings from folder: {STUDENTS_DIR}\n")

    known_encodings = []
    known_names = []
    seen_encodings = []

    if not os.path.exists(STUDENTS_DIR):
        print("⚠️ Students folder not found. Creating it...")
        os.makedirs(STUDENTS_DIR)

    # Get all image files
    files = [f for f in os.listdir(STUDENTS_DIR) if f.lower().endswith((".jpg", ".jpeg", ".png"))]
    if not files:
        print(f"⚠️ No student photos found in '{STUDENTS_DIR}'")
        return

    for filename in files:
        filepath = os.path.join(STUDENTS_DIR, filename)
        name = normalize_name(filename)

        try:
            img = cv2.imread(filepath)
            if img is None:
                print(f"⚠️ Cannot read '{filename}', skipping.")
                continue

            rgb_img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
            encodings = face_recognition.face_encodings(rgb_img)

            if len(encodings) == 0:
                print(f"⚠️ No face found in '{filename}', skipping.")
                continue

            encoding = encodings[0]

            # Check if same face (similar encoding) already exists
            is_duplicate = False
            for existing in seen_encodings:
                if np.linalg.norm(existing - encoding) < 0.45:  # similarity threshold
                    print(f"🔁 Duplicate face detected ({filename}) — same as previous student, skipping.")
                    is_duplicate = True
                    break

            if is_duplicate:
                continue

            seen_encodings.append(encoding)
            known_encodings.append(encoding)
            known_names.append(name)
            print(f"✅ Encoded: {name}")

        except Exception as e:
            print(f"❌ Error encoding '{filename}': {e}")

    # Save to pickle file
    os.makedirs("data", exist_ok=True)
    with open(ENCODINGS_FILE, "wb") as f:
        pickle.dump({"encodings": known_encodings, "names": known_names}, f)

    print(f"\n🎯 Done! Encoded {len(known_names)} unique student(s).")
    print(f"💾 Encodings saved to: {ENCODINGS_FILE}\n")


if __name__ == "__main__":
    encode_all_faces()
