package com.noproxy.noproxy.DTO;

import java.time.LocalDate;

import com.noproxy.noproxy.model.Status;

public class AttendanceRequest {
    private LocalDate date;
    private String lectureName; // ✅ added field
    private Status status;
    private Long studentId;
    private Long facultyId;

    // Getters & Setters
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getLectureName() { return lectureName; }
    public void setLectureName(String lectureName) { this.lectureName = lectureName; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public Long getFacultyId() { return facultyId; }
    public void setFacultyId(Long facultyId) { this.facultyId = facultyId; }
}
