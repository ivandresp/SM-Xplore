package com.smxplore.users.application.port.out;

import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.dto.UserSearchCriteria;
import com.smxplore.users.application.dto.UserSummaryView;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.Role;
import com.smxplore.users.domain.model.Usuario;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface UserRepository {

    Mono<Usuario> findById(UUID id);

    Mono<Usuario> findByEmail(Email email);

    Mono<Boolean> existsByEmail(Email email);

    Mono<PageResult<UserSummaryView>> search(UserSearchCriteria criteria);

    Mono<Long> countActiveByRole(Role role);

    Mono<Usuario> save(Usuario user);
}
