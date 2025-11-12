package com.noproxy.noproxy.DTO;

public class AuthResponse {
    private String token;
    private String role;
    private Long id;
    private String name;
    private String email;
    private String imagePath; // only for STUDENT

    // Constructors
    public AuthResponse() {}

    public AuthResponse(String token, String role, Long id, String name, String email, String imagePath) {
        this.token = token;
        this.role = role;
        this.id = id;
        this.name = name;
        this.email = email;
        this.imagePath = imagePath;
    }

    // Getters & Setters
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
}
