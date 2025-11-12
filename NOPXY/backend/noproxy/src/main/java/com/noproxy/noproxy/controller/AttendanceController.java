package com.noproxy.noproxy.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.noproxy.noproxy.DTO.AttendanceRequest;
import com.noproxy.noproxy.model.AttendanceRecord;
import com.noproxy.noproxy.model.User;
import com.noproxy.noproxy.repository.AttendanceRecordRepository;
import com.noproxy.noproxy.repository.UserRepository;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private UserRepository userRepository;

    // ✅ Mark attendance
    @PostMapping("/mark")
    public ResponseEntity<?> markAttendance(@RequestBody AttendanceRequest request) {
        User student = userRepository.findById(request.getStudentId())
                .orElseThrow(() -> new RuntimeException("Student not found"));
        User faculty = userRepository.findById(request.getFacultyId())
                .orElseThrow(() -> new RuntimeException("Faculty not found"));

        AttendanceRecord record = new AttendanceRecord();
        record.setDate(request.getDate());
        record.setLectureName(request.getLectureName()); // ✅ save lecture name
        record.setStatus(request.getStatus());
        record.setStudent(student);
        record.setFaculty(faculty);

        attendanceRecordRepository.save(record);
        return ResponseEntity.ok("Attendance marked successfully!");
    }

    // ✅ Get all attendance records
    @GetMapping
    public ResponseEntity<List<AttendanceRecord>> getAllRecords() {
        return ResponseEntity.ok(attendanceRecordRepository.findAll());
    }

    // ✅ Get attendance by date
    @GetMapping("/date/{date}")
    public ResponseEntity<List<AttendanceRecord>> getByDate(@PathVariable String date) {
        LocalDate localDate = LocalDate.parse(date);
        return ResponseEntity.ok(attendanceRecordRepository.findByDate(localDate));
    }

    // ✅ Get attendance by student ID
    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<AttendanceRecord>> getByStudent(@PathVariable Long studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        return ResponseEntity.ok(attendanceRecordRepository.findByStudent(student));
    }

    // ✅ Get attendance by faculty ID
    @GetMapping("/faculty/{facultyId}")
    public ResponseEntity<List<AttendanceRecord>> getByFaculty(@PathVariable Long facultyId) {
        User faculty = userRepository.findById(facultyId)
                .orElseThrow(() -> new RuntimeException("Faculty not found"));
        return ResponseEntity.ok(attendanceRecordRepository.findByFaculty(faculty));
    }
}
