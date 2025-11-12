import face_recognition
import cv2
import numpy as np
import pickle
import os
import re

ENCODINGS_FILE = "data/encodings.pkl"


def _normalize_name(s: str) -> str:
    """Normalize name for consistent matching."""
    s = os.path.splitext(s)[0]              # remove .jpg/.png
    s = re.sub(r"[_\-\d]+", " ", s)        # remove underscores, dashes, and numbers
    s = re.sub(r"\s+", " ", s).strip().lower()  # collapse extra spaces, lowercase
    return s


def recognize_faces(image_path, tolerance=0.55):
    """
    Recognize known faces in an uploaded image.
    Returns {"raw": [...], "normalized": [...]}.
    """
    print(f"\n🔍 Recognizing faces in: {image_path}")

    # -----------------------------
    # Load saved encodings
    # -----------------------------
    if not os.path.exists(ENCODINGS_FILE):
        print("⚠️ No encodings found. Please run encode_all_faces() first.")
        return {"raw": [], "normalized": []}

    with open(ENCODINGS_FILE, "rb") as f:
        data = pickle.load(f)

    known_encodings = data.get("encodings", [])
    known_names = data.get("names", [])

    print(f"✅ Loaded {len(known_encodings)} known encodings from {ENCODINGS_FILE}")

    if not known_encodings:
        print("⚠️ No valid encodings found in file.")
        return {"raw": [], "normalized": []}

    # -----------------------------
    # Load and process the uploaded image
    # -----------------------------
    img = cv2.imread(image_path)
    if img is None:
        print(f"⚠️ Unable to read image: {image_path}")
        return {"raw": [], "normalized": []}

    rgb_img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
    face_locations = face_recognition.face_locations(rgb_img)
    face_encodings = face_recognition.face_encodings(rgb_img, face_locations)

    print(f"📸 Detected {len(face_encodings)} face(s) in the uploaded photo")

    recognized_raw = []
    recognized_normalized = []

    # -----------------------------
    # Compare each face to known encodings
    # -----------------------------
    for idx, face_encoding in enumerate(face_encodings):
        matches = face_recognition.compare_faces(known_encodings, face_encoding, tolerance=tolerance)
        face_distances = face_recognition.face_distance(known_encodings, face_encoding)

        if len(face_distances) == 0:
            print(f"⚠️ Face {idx+1}: No encodings to compare.")
            recognized_raw.append("Unknown")
            recognized_normalized.append("unknown")
            continue

        best_match_index = np.argmin(face_distances)
        confidence = 1 - face_distances[best_match_index]

        if matches[best_match_index]:
            raw_name = known_names[best_match_index]
            normalized = _normalize_name(raw_name)
            recognized_raw.append(raw_name)
            recognized_normalized.append(normalized)
            print(f"✅ Face {idx+1}: {raw_name} ({confidence:.2f} confidence)")
        else:
            recognized_raw.append("Unknown")
            recognized_normalized.append("unknown")
            print(f"❌ Face {idx+1}: Unknown (best match {confidence:.2f})")

    # -----------------------------
    # Summary output
    # -----------------------------
    print("\n🎯 Final recognized (normalized):", recognized_normalized)
    print("🧠 Final recognized (raw):", recognized_raw)
    print("-" * 60)

    return {"raw": recognized_raw, "normalized": recognized_normalized}
