// ==========================
// BASE URLS
// ==========================
const JAVA_BASE_URL = "http://localhost:5501/api/auth";
const PYTHON_BASE_URL = "http://localhost:5500";
const $ = (id) => document.getElementById(id);

// ==========================
// GLOBAL STATE
// ==========================
let token = null;
let currentUser = null;
let currentRole = "student";
let studentChart = null;

// ==========================
// TOASTS
// ==========================
function showToast(msg, type = "info", dur = 3000) {
  const c = $("toastContainer");
  const t = document.createElement("div");
  t.className = "toast";
  t.style.background =
    type === "error" ? "#ef4444" : type === "success" ? "#22c55e" : "#3b82f6";
  t.textContent = msg;
  c.appendChild(t);
  setTimeout(() => (t.style.opacity = "0"), dur - 300);
  setTimeout(() => t.remove(), dur);
}

// ==========================
// AUTH UI
// ==========================
function setupAuthUI() {
  $("signInTab").onclick = () => {
    $("signInForm").classList.remove("hidden");
    $("signUpForm").classList.add("hidden");
    $("signInTab").classList.add("bg-red-600", "text-white");
    $("signUpTab").classList.remove("bg-red-600", "text-white");
  };

  $("signUpTab").onclick = () => {
    $("signUpForm").classList.remove("hidden");
    $("signInForm").classList.add("hidden");
    $("signUpTab").classList.add("bg-red-600", "text-white");
    $("signInTab").classList.remove("bg-red-600", "text-white");
  };

  $("facultyTab").onclick = () => {
    currentRole = "faculty";
    $("photoField").classList.add("hidden");
    $("facultyTab").classList.add("bg-red-600", "text-white");
    $("studentTab").classList.remove("bg-red-600", "text-white");
  };

  $("studentTab").onclick = () => {
    currentRole = "student";
    $("photoField").classList.remove("hidden");
    $("studentTab").classList.add("bg-red-600", "text-white");
    $("facultyTab").classList.remove("bg-red-600", "text-white");
  };

  $("toggleLoginPassword").onclick = () => {
    const i = $("loginPassword");
    i.type = i.type === "password" ? "text" : "password";
  };
  $("toggleRegisterPassword").onclick = () => {
    const i = $("registerPassword");
    i.type = i.type === "password" ? "text" : "password";
  };

  $("registerBtn").onclick = registerUser;
  $("loginBtn").onclick = loginUser;
  $("backToSignIn").onclick = () => $("signInTab").click();
  window.addEventListener("DOMContentLoaded", () => $("studentTab").click());
}

// ==========================
// REGISTER USER
// ==========================
async function registerUser() {
  const id = $("registerId").value.trim();
  const name = $("registerName").value.trim();
  const email = $("registerEmail").value.trim();
  const password = $("registerPassword").value;
  const photo = $("studentPhoto").files[0];

  if (!email || !password) return showToast("Email & Password required", "error");

  try {
    const res = await fetch(`${JAVA_BASE_URL}/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ id, name, email, password, role: currentRole }),
    });

    if (!res.ok) throw await res.json();
    showToast("✅ Registered successfully", "success");

    if (currentRole === "student" && photo) {
      const fd = new FormData();
      fd.append("file", photo);
      fd.append("email", email);
      await fetch(`${JAVA_BASE_URL}/upload-photo`, { method: "POST", body: fd });
      showToast("📸 Face registered successfully", "success");
    }

    $("signInTab").click();
  } catch {
    showToast("❌ Registration failed", "error");
  }
}

// ==========================
// LOGIN USER
// ==========================
async function loginUser() {
  const email = $("loginEmail").value.trim();
  const password = $("loginPassword").value;

  if (!email || !password) return showToast("Enter credentials", "error");

  try {
    const res = await fetch(`${JAVA_BASE_URL}/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password, role: currentRole }),
    });

    if (!res.ok) throw await res.json();
    const data = await res.json();

    token = data.token;
    currentUser = {
      id: data.id || email,
      name: data.name || "User",
      role: data.role?.toLowerCase() || currentRole,
    };

    showToast("✅ Login successful", "success");
    showDashboard(currentUser.role);
  } catch {
    showToast("❌ Login failed", "error");
  }
}

// ==========================
// DASHBOARD SWITCH
// ==========================
function showDashboard(role) {
  $("authScreen").classList.add("hidden");
  $("dashboards").classList.remove("hidden");

  $("sidebar").innerHTML = `
    <div class="text-center">
      <div class="w-20 h-20 bg-white/20 rounded-full mx-auto flex items-center justify-center text-2xl font-bold">
        ${(currentUser.name || "U")[0].toUpperCase()}
      </div>
      <h2 class="mt-3 font-semibold text-lg">${currentUser.name}</h2>
      <p class="text-sm text-red-100">${role}</p>
    </div>
    <nav class="flex-1 space-y-2 mt-4">
      ${
        role === "faculty"
          ? `
            <button id="processAttendanceBtn" class="w-full text-left px-4 py-2 rounded-lg bg-white/20 hover:bg-white/30">📸 Process Attendance</button>
            <button id="viewStudentsBtn" class="w-full text-left px-4 py-2 rounded-lg bg-white/20 hover:bg-white/30">👥 View Students Data</button>
          `
          : ``
      }
    </nav>
    <button id="logoutBtn" class="mt-auto bg-white text-red-600 font-semibold py-2 rounded-lg hover:bg-gray-100">Logout</button>
    <p class="text-xs text-center mt-4 text-red-200">NoProxy © 2025</p>`;

  $("logoutBtn").onclick = logout;

  if (role === "faculty") {
    renderFacultyDashboard(); // Default page
    $("processAttendanceBtn").onclick = renderFacultyDashboard;
    $("viewStudentsBtn").onclick = viewAllStudents;
  } else {
    renderStudentDashboard();
  }
}

// ==========================
// FACULTY DASHBOARD (Upload Attendance)
// ==========================
function renderFacultyDashboard() {
  $("mainArea").innerHTML = `
    <h1 class="text-3xl font-bold text-gray-800 mb-2">KJ Somaiya Institute of Technology</h1>
    <p class="text-gray-600 mb-6">Welcome, <strong>${currentUser.name}</strong></p>

    <section class="bg-white p-6 rounded-2xl shadow-md mb-8">
      <h2 class="text-xl font-semibold text-gray-800 mb-4">Process Attendance</h2>
      <p class="text-gray-500 text-sm mb-4">Upload a classroom photo to automatically mark attendance</p>
      <div class="grid md:grid-cols-2 gap-4">
        <div>
          <label class="text-sm font-medium text-gray-700 mb-1 block">Session Name</label>
          <input id="sessionInput" placeholder="e.g., CS101 - Lecture 15" class="border p-2 rounded w-full">
        </div>
        <div>
          <label class="text-sm font-medium text-gray-700 mb-1 block">Faculty ID</label>
          <input id="facultyId" type="text" value="${currentUser.id}" class="border p-2 rounded w-full bg-gray-100" readonly>
        </div>
      </div>

      <div class="mt-3">
        <label class="text-sm font-medium text-gray-700 mb-1 block">Classroom Photo</label>
        <input id="imageInput" type="file" accept="image/*" class="border p-2 rounded w-full bg-gray-50">
        <p class="text-xs text-gray-500 mt-1">Upload a classroom photo (JPG, PNG, max 10MB)</p>
      </div>

      <button id="uploadBtn" class="mt-4 bg-red-600 text-white px-5 py-2 rounded-lg hover:bg-red-700 font-semibold transition-all">
        Process Attendance
      </button>
      <div id="facultyResult" class="mt-4 text-sm font-medium text-green-700"></div>
    </section>`;

  $("uploadBtn").onclick = async () => {
    const file = $("imageInput").files[0];
    if (!file) return showToast("Please select an image first", "error");

    const fd = new FormData();
    fd.append("file", file);
    fd.append("session", $("sessionInput").value || "Default Lecture");
    fd.append("marked_by", currentUser.id);

    try {
      const res = await fetch(`${PYTHON_BASE_URL}/recognize`, { method: "POST", body: fd });
      const data = await res.json();

      if (data.error) throw data.error;

      $("facultyResult").innerHTML = `
        ✅ <strong>Attendance Processed!</strong><br>
        Present: ${data.count_present || 0}<br>
        Absent: ${data.count_absent || 0}
      `;
      showToast("✅ Attendance processed successfully", "success");
    } catch (e) {
      console.error(e);
      showToast("Error processing attendance", "error");
    }
  };
}

// ==========================
// FACULTY: View All Students
// ==========================
async function viewAllStudents() {
  $("mainArea").innerHTML = `
    <h1 class="text-2xl font-semibold mb-4">Registered Students</h1>
    <div class="bg-white p-6 rounded-2xl shadow-md overflow-x-auto">
      <table class="w-full text-sm border-collapse">
        <thead class="bg-gray-100 text-gray-700">
          <tr>
            <th class="p-2 text-left">#</th>
            <th class="p-2 text-left">Name</th>
            <th class="p-2 text-left">Email</th>
            <th class="p-2 text-left">Student ID</th>
          </tr>
        </thead>
        <tbody id="studentsTable" class="text-gray-700"></tbody>
      </table>
    </div>`;

  try {
    const res = await fetch(`${JAVA_BASE_URL}/all-students`);
    const students = await res.json();

    const table = $("studentsTable");
    if (!students.length) {
      table.innerHTML = `<tr><td colspan="4" class="p-3 text-center text-gray-500">No registered students yet</td></tr>`;
      return;
    }

    table.innerHTML = students
      .map(
        (s, i) =>
          `<tr class="border-b hover:bg-gray-50 transition-all">
            <td class="p-2">${i + 1}</td>
            <td class="p-2">${s.name}</td>
            <td class="p-2">${s.email}</td>
            <td class="p-2">${s.id || "-"}</td>
          </tr>`
      )
      .join("");
  } catch {
    showToast("Error fetching students", "error");
  }
}

// ==========================
// STUDENT DASHBOARD
// ==========================
async function renderStudentDashboard() {
  $("mainArea").innerHTML = `
  <h1 class="text-3xl font-bold text-gray-800 mb-2">KJ Somaiya Institute of Technology</h1>
  <p class="text-gray-600 mb-6">Your Attendance Overview</p>
  <div class="grid md:grid-cols-2 gap-6 mb-8">
    <div class="bg-white p-6 rounded-2xl shadow-md text-center">
      <div id="attendancePercent" class="text-5xl font-bold text-red-600">--%</div>
      <p class="text-gray-500 mt-2">Overall Attendance</p>
    </div>
    <div class="bg-white p-6 rounded-2xl shadow-md">
      <canvas id="attendanceChart" height="180"></canvas>
    </div>
  </div>
  <div class="bg-white p-6 rounded-2xl shadow-md overflow-x-auto">
    <table class="w-full text-sm border-collapse">
      <thead class="bg-gray-100 text-gray-700">
        <tr>
          <th class="p-2 text-left">Date</th>
          <th class="p-2 text-left">Subject</th>
          <th class="p-2 text-left">Status</th>
        </tr>
      </thead>
      <tbody id="attendanceTable" class="text-gray-700"></tbody>
    </table>
  </div>`;

  try {
    const res = await fetch(`${PYTHON_BASE_URL}/attendance/${currentUser.id}`);
    const data = await res.json();
    const list = data?.attendance || [];

    const counts = { PRESENT: 0, ABSENT: 0 };
    list.forEach((x) => (counts[x.status] = (counts[x.status] || 0) + 1));

    const total = counts.PRESENT + counts.ABSENT || 1;
    const percent = Math.round((counts.PRESENT / total) * 100);
    $("attendancePercent").textContent = percent + "%";

    const ctx = document.getElementById("attendanceChart").getContext("2d");
    if (studentChart) studentChart.destroy();
    studentChart = new Chart(ctx, {
      type: "doughnut",
      data: {
        labels: ["Present", "Absent"],
        datasets: [
          { data: [counts.PRESENT, counts.ABSENT], backgroundColor: ["#16a34a", "#dc2626"], borderWidth: 0 },
        ],
      },
      options: { plugins: { legend: { display: true, position: "bottom" } }, maintainAspectRatio: false },
    });

    $("attendanceTable").innerHTML = list
      .map(
        (x) =>
          `<tr class="border-b hover:bg-gray-50 transition-all">
            <td class="p-2">${x.date}</td>
            <td class="p-2">${x.lecture_name}</td>
            <td class="p-2 font-semibold ${
              x.status === "PRESENT" ? "text-green-600" : "text-red-600"
            }">${x.status}</td>
          </tr>`
      )
      .join("");
  } catch {
    $("attendanceTable").innerHTML = `<tr><td colspan="3" class="p-3 text-center text-gray-500">No attendance found</td></tr>`;
  }
}

// ==========================
// LOGOUT
// ==========================
function logout() {
  token = null;
  currentUser = null;
  $("dashboards").classList.add("hidden");
  $("authScreen").classList.remove("hidden");
  $("signInTab").click();
  showToast("Logged out successfully", "info");
}

// ==========================
// INIT
// ==========================
setupAuthUI();
