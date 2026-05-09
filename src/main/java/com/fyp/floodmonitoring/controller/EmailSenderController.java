package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.entity.EmailSender;
import com.fyp.floodmonitoring.exception.AppException;
import com.fyp.floodmonitoring.repository.EmailSenderRepository;
import com.fyp.floodmonitoring.service.EmailSenderResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * Admin-only CRUD over the email-sender registry. Lets ops swap the
 * from-address for a purpose at runtime without a redeploy.
 *
 * <pre>
 *   GET   /admin/email-senders            list all
 *   PATCH /admin/email-senders/{purpose}  update from-address / display name / active
 * </pre>
 */
@RestController
@RequestMapping("/admin/email-senders")
@RequiredArgsConstructor
public class EmailSenderController {

    private final EmailSenderRepository repo;
    private final EmailSenderResolver   resolver;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('OPERATIONS_MANAGER')")
    public ResponseEntity<List<EmailSenderDto>> list() {
        return ResponseEntity.ok(
                repo.findAllByOrderByPurposeAsc()
                    .stream()
                    .map(EmailSenderController::toDto)
                    .toList()
        );
    }

    @PatchMapping("/{purpose}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OPERATIONS_MANAGER')")
    public ResponseEntity<EmailSenderDto> update(
            @PathVariable String purpose,
            @Valid @RequestBody UpdateEmailSenderRequest req) {

        EmailSender row = repo.findByPurposeIgnoreCaseAndActiveTrue(purpose)
                .orElseThrow(() -> AppException.notFound("Sender not found: " + purpose));

        if (req.fromAddress() != null && !req.fromAddress().isBlank()) {
            row.setFromAddress(req.fromAddress().trim());
        }
        if (req.displayName() != null) {
            row.setDisplayName(req.displayName().isBlank() ? null : req.displayName().trim());
        }
        if (req.active() != null) {
            row.setActive(req.active());
        }
        row.setUpdatedAt(Instant.now());
        EmailSender saved = repo.save(row);

        // Resolver caches per JVM lifetime — drop cache so the next email
        // picks up the new value without a restart.
        resolver.invalidate();

        return ResponseEntity.ok(toDto(saved));
    }

    public record EmailSenderDto(
            String id,
            String purpose,
            String fromAddress,
            String displayName,
            boolean active,
            Instant updatedAt
    ) {}

    public record UpdateEmailSenderRequest(
            @Email @Size(max = 255) String fromAddress,
            @Size(max = 100)        String displayName,
            Boolean                 active
    ) {}

    private static EmailSenderDto toDto(EmailSender s) {
        return new EmailSenderDto(
                s.getId().toString(),
                s.getPurpose(),
                s.getFromAddress(),
                s.getDisplayName(),
                Boolean.TRUE.equals(s.getActive()),
                s.getUpdatedAt());
    }
}
