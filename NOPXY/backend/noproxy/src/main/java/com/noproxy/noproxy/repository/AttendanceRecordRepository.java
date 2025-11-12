package com.noproxy.noproxy.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.noproxy.noproxy.model.AttendanceRecord;
import com.noproxy.noproxy.model.User;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    List<AttendanceRecord> findByStudent(User student);
    List<AttendanceRecord> findByFaculty(User faculty);
    List<AttendanceRecord> findByDate(LocalDate date);
}
