package com.smxplore.securityreports.infrastructure.adapters.in.web.pagination;

import com.smxplore.securityreports.domain.model.types.Page;
import com.smxplore.securityreports.domain.model.types.PageRequest;
import com.smxplore.securityreports.infrastructure.adapters.in.web.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class PageResponseMapper {

    private final CursorCodec cursorCodec;

    public PageRequest toPageRequest(String cursor, int size) {
        return new PageRequest(size, cursorCodec.decode(cursor));
    }

    public <T, R> PageResponse<R> toResponse(Page<T> page, Function<T, R> responseMapper) {
        var content = page.content().stream().map(responseMapper).toList();
        var nextCursor = page.nextCursor() == null ? null : cursorCodec.encode(page.nextCursor());
        return PageResponse.of(content, nextCursor);
    }
}
