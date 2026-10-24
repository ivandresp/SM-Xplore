package com.smxplore.users.identity.infrastructure.adapter.in.rest;

import com.smxplore.users.identity.application.dto.PublicProfileView;
import com.smxplore.users.identity.application.port.in.GetPublicProfileUseCase;
import com.smxplore.users.identity.domain.model.UserId;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class PublicUserController {

    private final GetPublicProfileUseCase getPublicProfile;

    public PublicUserController(GetPublicProfileUseCase getPublicProfile) {
        this.getPublicProfile = getPublicProfile;
    }

    @GetMapping("/users/{id}")
    public PublicProfileView get(@PathVariable UUID id) {
        return getPublicProfile.get(new UserId(id));
    }
}
