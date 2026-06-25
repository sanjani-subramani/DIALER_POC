package com.qcerebrum.dialerbackend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface CallLogRepository extends JpaRepository<CallLog, Long> {
    List<CallLog> findAllByOrderByIdDesc();   // newest call first

    @Query("SELECT c FROM CallLog c WHERE c.providerCallSid IS NOT NULL AND c.status NOT IN ('COMPLETED', 'NO_ANSWER', 'BUSY', 'FAILED')")
    List<CallLog> findActiveCalls();
}