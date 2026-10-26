package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.LoginCommand;
import com.smxplore.users.application.dto.LoginResult;
import com.smxplore.users.application.port.in.LoginUseCase;
import com.smxplore.users.application.port.out.PasswordHasher;
import com.smxplore.users.application.port.out.TokenIssuer;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.AccountNotActiveException;
import com.smxplore.users.domain.exception.InvalidCredentialsException;
import com.smxplore.users.domain.model.Email;
import java.time.Clock;
import java.time.Duration;
import reactor.core.publisher.Mono;

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
    public Mono<LoginResult> login(LoginCommand command) {
        return users.findByEmail(new Email(command.email()))
                .switchIfEmpty(Mono.error(InvalidCredentialsException::new))
                .flatMap(user -> passwordHasher
                        .matches(command.rawPassword(), user.passwordHash())
                        .flatMap(matches -> {
                            if (!matches) {
                                return Mono.error(new InvalidCredentialsException());
                            }
                            if (!user.isActive()) {
                                return Mono.error(new AccountNotActiveException());
                            }
                            return tokenIssuer.issue(user);
                        }))
                .map(issuedToken -> {
                    long expiresIn = Math.max(
                            0,
                            Duration.between(clock.instant(), issuedToken.expiresAt())
                                    .toSeconds());
                    return new LoginResult(issuedToken.value(), "Bearer", expiresIn, issuedToken.expiresAt());
                });
    }
}
