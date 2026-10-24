package com.smxplore.users.identity.application.port.out;

import com.smxplore.users.identity.domain.model.Email;
import com.smxplore.users.identity.domain.model.User;
import com.smxplore.users.identity.domain.model.UserId;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(UserId id);

    Optional<User> findByEmail(Email email);

    boolean existsByEmail(Email email);

    User save(User user);
}
