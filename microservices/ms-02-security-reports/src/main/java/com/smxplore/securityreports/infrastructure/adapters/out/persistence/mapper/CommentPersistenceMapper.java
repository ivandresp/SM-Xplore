package com.smxplore.securityreports.infrastructure.adapters.out.persistence.mapper;

import com.smxplore.securityreports.domain.model.Comment;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.document.CommentDocument;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;

@Mapper(componentModel = "spring",
        uses = UserRefPersistenceMapper.class,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CommentPersistenceMapper {

    CommentDocument toDocument(Comment comment);

    Comment toDomain(CommentDocument document);

    default Instant mapCreatedAt(Instant createdAt) {
        return createdAt == null ? Instant.EPOCH : createdAt;
    }
}
