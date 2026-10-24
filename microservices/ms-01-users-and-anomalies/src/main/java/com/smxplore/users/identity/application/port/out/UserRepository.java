package com.smxplore.users.identity.application.port.out;

import com.smxplore.users.identity.domain.model.Email;
import com.smxplore.users.identity.domain.model.UserId;
import com.smxplore.users.identity.domain.model.Usuario;
import java.util.Optional;

public interface UserRepository {

    Optional<Usuario> findById(UserId id);

    Optional<Usuario> findByEmail(Email email);

    boolean existsByEmail(Email email);

    Usuario save(Usuario user);
}
