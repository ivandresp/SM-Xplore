package com.smxplore.users.infrastructure.adapter.in.rest;

import com.smxplore.users.application.dto.GuideSearchCriteria;
import com.smxplore.users.application.dto.GuideSummaryView;
import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.dto.ReviewRegistrationCommand;
import com.smxplore.users.application.port.in.ReviewGuideUseCase;
import com.smxplore.users.application.port.in.SearchGuidesUseCase;
import com.smxplore.users.domain.model.RegistrationStatus;
import com.smxplore.users.infrastructure.adapter.in.dto.RegistrationReviewRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Revisión de guías por parte de una gestora de destinos. El administrador también puede revisar para no depender de
 * que exista una gestora activa (mismo criterio que en el resto de tareas administrativas).
 */
@RestController
@RequestMapping("/api/v1/manager")
@PreAuthorize("hasAnyRole('DESTINATION_MANAGER','ADMIN')")
public class ManagerGuideController {

    private final SearchGuidesUseCase searchGuides;
    private final ReviewGuideUseCase reviewGuide;

    public ManagerGuideController(SearchGuidesUseCase searchGuides, ReviewGuideUseCase reviewGuide) {
        this.searchGuides = searchGuides;
        this.reviewGuide = reviewGuide;
    }

    @GetMapping("/guides")
    public Mono<PageResult<GuideSummaryView>> guides(
            @RequestParam(required = false) RegistrationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return searchGuides.search(new GuideSearchCriteria(status, page, size));
    }

    @PostMapping("/guides/{id}/review")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> review(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody RegistrationReviewRequest request) {
        return reviewGuide.review(
                UUID.fromString(jwt.getSubject()),
                id,
                new ReviewRegistrationCommand(request.decision(), request.reason()));
    }
}
