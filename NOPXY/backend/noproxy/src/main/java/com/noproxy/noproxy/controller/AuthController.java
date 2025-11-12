package com.noproxy.noproxy.controller;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.noproxy.noproxy.DTO.AuthRequest;
import com.noproxy.noproxy.DTO.AuthResponse;
import com.noproxy.noproxy.DTO.RegisterRequest;
import com.noproxy.noproxy.DTO.RegisterResponse;
import com.noproxy.noproxy.model.Role;
import com.noproxy.noproxy.model.User;
import com.noproxy.noproxy.repository.UserRepository;
import com.noproxy.noproxy.service.UserService;
import com.noproxy.noproxy.util.JwtUtil;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    // Java backend uploads folder
    private static final String JAVA_UPLOAD_DIR = "uploads";

    // Python students folder (relative to backend)
    private static final Path PYTHON_STUDENTS_DIR = Paths.get("..", "noproxy-face", "students");

    // Flask endpoint to re-encode faces
    private static final String FLASK_ENCODE_URL = "http://127.0.0.1:5500/encode";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public AuthController(AuthenticationManager authenticationManager,
                          UserService userService,
                          UserRepository userRepository,
                          JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    // ✅ Backend health check
    @GetMapping("/ping")
    public ResponseEntity<?> ping() {
        return ResponseEntity.ok(Map.of("status", "Java backend running!", "port", "5501"));
    }

    // ✅ Register new user (Student or Faculty)
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest body) {
        try {
            Role role = Role.valueOf(body.getRole().toUpperCase());
            User user = userService.registerUser(
                    body.getId(),
                    body.getName(),
                    body.getEmail(),
                    body.getPassword(),
                    role,
                    body.getImagePath()
            );

            RegisterResponse response = new RegisterResponse(
                    "User registered successfully!",
                    user.getId(),
                    user.getName(),
                    user.getEmail(),
                    user.getRole().name(),
                    user.getRole() == Role.STUDENT ? user.getImagePath() : null
            );

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid role specified."));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "User already exists or invalid data."));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Unexpected error: " + e.getMessage()));
        }
    }

    // ✅ Upload student photo → saves relative path and syncs with Flask
    @PostMapping("/upload-photo")
    public ResponseEntity<?> uploadPhoto(@RequestParam("file") MultipartFile file,
                                         @RequestParam("email") String email) {
        try {
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "User not found."));
            }

            // Ensure directories exist
            Files.createDirectories(Paths.get(JAVA_UPLOAD_DIR));
            Files.createDirectories(PYTHON_STUDENTS_DIR);

            // Clean filename
            String cleanName = Objects.requireNonNull(file.getOriginalFilename())
                    .replaceAll("[^a-zA-Z0-9._-]", "_");

            // Save in Java uploads/
            Path javaPath = Paths.get(JAVA_UPLOAD_DIR).resolve(cleanName).toAbsolutePath();
            file.transferTo(javaPath);

            // ✅ Copy to Python /students folder
            Path pythonPath = PYTHON_STUDENTS_DIR.resolve(cleanName).normalize().toAbsolutePath();
            Files.copy(javaPath, pythonPath, StandardCopyOption.REPLACE_EXISTING);

            // ✅ Save relative path to DB (for Python use)
            String relativePath = "students/" + cleanName;

            User user = userOpt.get();
            user.setImagePath(relativePath);
            userRepository.save(user);

            System.out.println("✅ Copied to Python folder: " + pythonPath);
            System.out.println("✅ Saved relative path in DB: " + relativePath);

            // ✅ Trigger Flask encoding automatically
            triggerFlaskEncode();

            return ResponseEntity.ok(Map.of(
                    "message", "Photo uploaded, copied to Python /students and DB updated.",
                    "path", relativePath
            ));

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "File upload failed: " + e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Unexpected error: " + e.getMessage()));
        }
    }

    // 🔄 Helper to trigger Flask /encode
    private void triggerFlaskEncode() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(FLASK_ENCODE_URL))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            httpClient.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                    .thenAccept(resp ->
                            System.out.println("🧠 Flask encode triggered (status: " + resp.statusCode() + ")"))
                    .exceptionally(ex -> {
                        System.err.println("⚠️ Could not trigger Flask encode: " + ex.getMessage());
                        return null;
                    });

        } catch (Exception e) {
            System.err.println("⚠️ Trigger encode error: " + e.getMessage());
        }
    }

    // ✅ Login
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest loginRequest) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );

            UserDetails userDetails = userService.loadUserByUsername(loginRequest.getEmail());
            User user = userService.getUserByEmail(loginRequest.getEmail()).orElseThrow();

            String token = jwtUtil.generateToken(
                    userDetails,
                    user.getRole().name(),
                    user.getId().toString(),
                    user.getName()
            );

            AuthResponse response = new AuthResponse(
                    token,
                    user.getRole().name(),
                    user.getId(),
                    user.getName(),
                    user.getEmail(),
                    user.getRole() == Role.STUDENT ? user.getImagePath() : null
            );

            return ResponseEntity.ok(response);

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid email or password."));
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Invalid login request: " + e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Unexpected error during login: " + e.getMessage()));
        }
    }

    // ✅ Get all registered students
    @GetMapping("/all-students")
    public ResponseEntity<?> getAllStudents() {
        try {
            List<User> students = userRepository.findByRole(Role.STUDENT);
            if (students.isEmpty()) {
                return ResponseEntity.ok(Map.of("message", "No students found"));
            }

            List<Map<String, Object>> response = students.stream()
                    .sorted(Comparator.comparing(User::getName, String.CASE_INSENSITIVE_ORDER))
                    .map(s -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", s.getId());
                        m.put("name", s.getName());
                        m.put("email", s.getEmail());
                        m.put("role", s.getRole().name());
                        m.put("imagePath", s.getImagePath());
                        return m;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error fetching students: " + e.getMessage()));
        }
    }
}
