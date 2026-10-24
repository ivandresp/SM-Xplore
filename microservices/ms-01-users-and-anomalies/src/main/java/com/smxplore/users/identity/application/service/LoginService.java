package com.smxplore.users.identity.application.service;

import com.smxplore.users.identity.application.dto.LoginCommand;
import com.smxplore.users.identity.application.dto.LoginResult;
import com.smxplore.users.identity.application.port.in.LoginUseCase;
import com.smxplore.users.identity.application.port.out.PasswordHasher;
import com.smxplore.users.identity.application.port.out.TokenIssuer;
import com.smxplore.users.identity.application.port.out.UserRepository;
import com.smxplore.users.identity.domain.exception.AccountNotActiveException;
import com.smxplore.users.identity.domain.exception.InvalidCredentialsException;
import com.smxplore.users.identity.domain.model.Email;
import com.smxplore.users.identity.domain.model.User;
import java.time.Clock;
import java.time.Duration;

public final class LoginService implements LoginUseCase {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;
    private final Clock clock;

    public LoginService(UserRepository users, PasswordHasher passwordHasher, TokenIssuer tokenIssuer, Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.clock = clock;
    }

    @Override
    public LoginResult login(LoginCommand command) {
        User user = users.findByEmail(new Email(command.email())).orElseThrow(InvalidCredentialsException::new);

        if (!passwordHasher.matches(command.rawPassword(), user.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        if (!user.isActive()) {
            throw new AccountNotActiveException();
        }

        TokenIssuer.IssuedToken issuedToken = tokenIssuer.issue(user);
        long expiresIn = Math.max(
                0, Duration.between(clock.instant(), issuedToken.expiresAt()).toSeconds());

        return new LoginResult(issuedToken.value(), "Bearer", expiresIn, issuedToken.expiresAt());
    }
}
