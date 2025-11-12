package com.noproxy.noproxy.DTO;

public class RegisterResponse {
    private String message;
    private Long id;
    private String name;
    private String email;
    private String role;
    private String imagePath; // only for STUDENT

    // Constructors
    public RegisterResponse() {}

    public RegisterResponse(String message, Long id, String name, String email, String role, String imagePath) {
        this.message = message;
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.imagePath = imagePath;
    }

    // Getters & Setters
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
}
