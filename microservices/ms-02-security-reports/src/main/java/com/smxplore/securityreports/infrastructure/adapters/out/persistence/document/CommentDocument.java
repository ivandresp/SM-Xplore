package com.smxplore.securityreports.infrastructure.adapters.out.persistence.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "security_report_comments")
@CompoundIndex(name = "idx_security_report_comments_report_created_at",
        def = "{'reportId': 1, 'createdAt': 1}")
public class CommentDocument {
    @Id
    private String id;

    private String reportId;
    private UserRefDocument user;
    private String content;
    private Instant createdAt;
}
