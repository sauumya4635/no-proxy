import os
import csv
import re
import shutil
import mysql.connector
from datetime import datetime
from flask import Flask, request, jsonify
from flask_cors import CORS
from encode_images import encode_all_faces
from recognize import recognize_faces

# ===============================
# Flask Setup
# ===============================
app = Flask(__name__)
CORS(app, resources={r"/*": {"origins": "*"}})

UPLOAD_FOLDER = "uploads"
STUDENT_FOLDER = "students"
UNKNOWN_FOLDER = "unknowns"
ATTENDANCE_FILE = "data/attendance.csv"

for folder in [UPLOAD_FOLDER, STUDENT_FOLDER, UNKNOWN_FOLDER, "data"]:
    os.makedirs(folder, exist_ok=True)

# ===============================
# MySQL Connection
# ===============================
def get_db_connection():
    return mysql.connector.connect(
        host="localhost",
        user="root",
        password="cpgisHD@21",  # ⚠️ change if needed
        database="noproxy"
    )

# ===============================
# Normalize helper
# ===============================
def normalize_name(s):
    if not s:
        return ""
    s = os.path.splitext(s)[0]
    s = s.replace("_", " ")
    s = re.sub(r"\s+", " ", s).strip().lower()
    return s

# ===============================
# Initialize backup CSV
# ===============================
if not os.path.exists(ATTENDANCE_FILE):
    with open(ATTENDANCE_FILE, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["Name", "Timestamp"])

# ===============================
# Routes
# ===============================
@app.route("/")
def home():
    return jsonify({"message": "Flask backend running!", "port": 5500})


# --------------------------------
# 👤 Register new student
# --------------------------------
@app.route("/register", methods=["POST"])
def register_student():
    if "file" not in request.files:
        return jsonify({"error": "No file provided"}), 400

    file = request.files["file"]
    if not file.filename:
        return jsonify({"error": "No file selected"}), 400

    filename = os.path.basename(file.filename)
    upload_temp_path = os.path.join(UPLOAD_FOLDER, filename)
    student_final_path = os.path.join(STUDENT_FOLDER, filename)

    # Save in uploads/
    file.save(upload_temp_path)

    # Copy also to students/
    try:
        shutil.copy2(upload_temp_path, student_final_path)
        print(f"📁 Copied file to both uploads/ and students/: {filename}")
    except Exception as e:
        print(f"⚠️ File copy failed for {filename}: {e}")

    student_name = os.path.splitext(filename)[0]
    db_path = f"students/{filename}"

    # Update image path in DB
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        cursor.execute(
            "UPDATE users SET image_path=%s WHERE LOWER(name)=LOWER(%s)",
            (db_path, student_name)
        )
        conn.commit()
        cursor.close()
        conn.close()
        print(f"🧾 Updated DB image_path for {student_name}")
    except Exception as e:
        print("❌ MySQL update error:", e)

    # Auto-encode again
    try:
        print("\n🔄 Rebuilding encodings after new registration...")
        encode_all_faces()
        print("✅ Encodings updated successfully.\n")
    except Exception as e:
        print("⚠️ Auto-encoding failed:", e)

    return jsonify({
        "message": f"✅ Student '{student_name}' registered successfully and saved.",
        "file_saved": db_path
    })


# --------------------------------
# 🧠 Recognize faces → mark attendance (includes unknowns)
# --------------------------------
@app.route("/recognize", methods=["POST"])
def recognize_class():
    if "file" not in request.files:
        return jsonify({"error": "No file provided"}), 400

    file = request.files["file"]
    if not file.filename:
        return jsonify({"error": "No file selected"}), 400

    lecture_name = request.form.get("session", "Default Lecture")
    marked_by = request.form.get("marked_by", None)
    save_path = os.path.join(UPLOAD_FOLDER, file.filename)
    file.save(save_path)

    rec = recognize_faces(save_path)
    recognized_raw = rec.get("raw", [])
    recognized_norm = [normalize_name(n) for n in recognized_raw]

    print("DEBUG recognized_raw:", recognized_raw)

    try:
        conn = get_db_connection()
        cursor = conn.cursor()

        cursor.execute("SELECT id, name FROM users WHERE role='STUDENT'")
        all_students = cursor.fetchall()

        present, absent, unknowns = [], [], []

        for sid, sname in all_students:
            norm_db = normalize_name(sname)
            is_present = norm_db in recognized_norm
            status = "PRESENT" if is_present else "ABSENT"

            cursor.execute("""
                SELECT COUNT(*) FROM attendance_records
                WHERE user_id=%s AND date=CURDATE() AND lecture_name=%s
            """, (sid, lecture_name))
            exists = cursor.fetchone()[0] > 0

            if not exists:
                cursor.execute("""
                    INSERT INTO attendance_records (date, lecture_name, status, user_id, marked_by)
                    VALUES (CURDATE(), %s, %s, %s, %s)
                """, (lecture_name, status, sid, marked_by))
                conn.commit()

            if is_present:
                present.append(sname)
            else:
                absent.append(sname)

        # ✅ Add unknown faces as present (logged for review)
        for name in recognized_raw:
            if name.lower() == "unknown":
                unknowns.append(name)
                unknown_copy = os.path.join(
                    UNKNOWN_FOLDER, f"{datetime.now().strftime('%H%M%S')}_unknown.jpg"
                )
                shutil.copy2(save_path, unknown_copy)

                cursor.execute("""
                    INSERT INTO attendance_records (date, lecture_name, status, user_id, marked_by)
                    VALUES (CURDATE(), %s, 'PRESENT', NULL, %s)
                """, (lecture_name, marked_by))
                conn.commit()

        cursor.close()
        conn.close()

    except Exception as e:
        print("❌ MySQL insert error:", e)
        return jsonify({"error": str(e)}), 500

    # ✅ CSV backup (includes unknowns)
    with open(ATTENDANCE_FILE, "a", newline="") as f:
        writer = csv.writer(f)
        ts = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        for name in recognized_raw:
            writer.writerow([name, ts])

    return jsonify({
        "message": "✅ Attendance processed successfully (including unknown faces)",
        "present": present + unknowns,
        "absent": absent,
        "unknown": unknowns,
        "count_present": len(present) + len(unknowns),
        "count_absent": len(absent),
        "total_faces_detected": len(recognized_raw)
    })


# --------------------------------
# 🧩 Encode Trigger (from Java)
# --------------------------------
@app.route("/encode", methods=["GET"])
def encode_trigger():
    """Triggered by Java after photo upload."""
    try:
        print("🧠 Received encode trigger from Java backend.")
        encode_all_faces()
        print("✅ Encodings rebuilt successfully via trigger.")
        return jsonify({"status": "ok", "message": "Encodings rebuilt successfully"}), 200
    except Exception as e:
        print("❌ Encode trigger error:", e)
        return jsonify({"status": "error", "message": str(e)}), 500


# --------------------------------
# 📊 Get attendance for a student
# --------------------------------
@app.route("/attendance/<int:user_id>", methods=["GET"])
def get_attendance(user_id):
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        cursor.execute("""
            SELECT date, lecture_name, status
            FROM attendance_records
            WHERE user_id=%s
            ORDER BY date DESC
        """, (user_id,))
        rows = cursor.fetchall()
        cursor.close()
        conn.close()

        return jsonify({
            "user_id": user_id,
            "attendance": [
                {"date": str(r[0]), "lecture_name": r[1], "status": r[2]} for r in rows
            ]
        })
    except Exception as e:
        return jsonify({"error": str(e)}), 500


# --------------------------------
# 📅 Daily summary for faculty
# --------------------------------
@app.route("/summary", methods=["GET"])
def summary():
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        cursor.execute("""
            SELECT SUM(status='PRESENT'), SUM(status='ABSENT')
            FROM attendance_records WHERE date=CURDATE()
        """)
        result = cursor.fetchone() or (0, 0)
        cursor.close()
        conn.close()

        present, absent = map(int, result)
        return jsonify({
            "date": datetime.now().strftime("%Y-%m-%d"),
            "present": present,
            "absent": absent,
            "total": present + absent
        })
    except Exception as e:
        return jsonify({"error": str(e)}), 500


# --------------------------------
# 🩵 Ping
# --------------------------------
@app.route("/ping")
def ping():
    return jsonify({"status": "Python backend running!", "port": 5500})


# --------------------------------
# 🚀 Run server
# --------------------------------
if __name__ == "__main__":
    app.run(host="127.0.0.1", port=5500, debug=True)
