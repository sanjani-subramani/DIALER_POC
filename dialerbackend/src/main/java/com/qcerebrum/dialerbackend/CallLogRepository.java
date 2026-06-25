package com.qcerebrum.dialerbackend;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CallLogRepository extends JpaRepository<CallLog, Long> {
    List<CallLog> findAllByOrderByIdDesc();   // newest call first
}