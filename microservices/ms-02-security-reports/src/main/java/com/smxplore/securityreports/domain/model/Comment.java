package com.smxplore.securityreports.domain.model;

import com.smxplore.securityreports.domain.exceptions.InvalidCommentException;
import com.smxplore.securityreports.domain.exceptions.InvalidUserReporterException;
import com.smxplore.securityreports.domain.exceptions.ReportWithoutContentException;
import com.smxplore.securityreports.domain.model.types.UserRef;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class Comment {
    @Builder.Default
    private final UUID id = UUID.randomUUID();
    private UUID reportId;
    private UserRef user;
    private String content;

    @Builder.Default
    private final Instant createdAt = Instant.now();

    public void validate() {
        if (reportId == null) throw new InvalidCommentException("Report id is mandatory.");
        if (user == null) throw new InvalidUserReporterException("No user found.");

        user.validate();
        validateContent();
    }

    private void validateContent() {
        if (content == null || content.isBlank())
            throw new ReportWithoutContentException("Comment content is empty");
    }
}
