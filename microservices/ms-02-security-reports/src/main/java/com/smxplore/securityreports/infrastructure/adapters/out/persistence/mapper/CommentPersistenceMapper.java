package com.smxplore.securityreports.infrastructure.adapters.out.persistence.mapper;

import com.smxplore.securityreports.domain.model.Comment;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.document.CommentDocument;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = UserRefPersistenceMapper.class)
public interface CommentPersistenceMapper {

    CommentDocument toDocument(Comment comment);

    Comment toDomain(CommentDocument document);
}
