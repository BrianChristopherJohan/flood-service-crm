package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.EmailSender;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailSenderRepository extends JpaRepository<EmailSender, UUID> {

    Optional<EmailSender> findByPurposeIgnoreCaseAndActiveTrue(String purpose);

    List<EmailSender> findAllByOrderByPurposeAsc();
}
