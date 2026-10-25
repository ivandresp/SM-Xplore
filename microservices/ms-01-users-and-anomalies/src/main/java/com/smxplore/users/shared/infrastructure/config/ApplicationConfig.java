package com.smxplore.users.shared.infrastructure.config;

import com.smxplore.users.identity.application.model.PasswordResetPolicy;
import com.smxplore.users.identity.application.port.in.BootstrapAdminUseCase;
import com.smxplore.users.identity.application.port.in.ChangeOwnPasswordUseCase;
import com.smxplore.users.identity.application.port.in.ForgotPasswordUseCase;
import com.smxplore.users.identity.application.port.in.GetCurrentUserUseCase;
import com.smxplore.users.identity.application.port.in.GetPublicProfileUseCase;
import com.smxplore.users.identity.application.port.in.ListAuditEntriesUseCase;
import com.smxplore.users.identity.application.port.in.LoginUseCase;
import com.smxplore.users.identity.application.port.in.RegisterProviderUseCase;
import com.smxplore.users.identity.application.port.in.RegisterTouristUseCase;
import com.smxplore.users.identity.application.port.in.ResetPasswordUseCase;
import com.smxplore.users.identity.application.port.in.UpdateProfileUseCase;
import com.smxplore.users.identity.application.port.out.AuditLogRepository;
import com.smxplore.users.identity.application.port.out.PasswordHasher;
import com.smxplore.users.identity.application.port.out.PasswordResetNotifier;
import com.smxplore.users.identity.application.port.out.PasswordResetTokenRepository;
import com.smxplore.users.identity.application.port.out.TokenGenerator;
import com.smxplore.users.identity.application.port.out.TokenHasher;
import com.smxplore.users.identity.application.port.out.TokenIssuer;
import com.smxplore.users.identity.application.port.out.TransactionPort;
import com.smxplore.users.identity.application.port.out.UserRepository;
import com.smxplore.users.identity.application.service.BootstrapAdminService;
import com.smxplore.users.identity.application.service.ChangeOwnPasswordService;
import com.smxplore.users.identity.application.service.ForgotPasswordService;
import com.smxplore.users.identity.application.service.GetCurrentUserService;
import com.smxplore.users.identity.application.service.GetPublicProfileService;
import com.smxplore.users.identity.application.service.ListAuditEntriesService;
import com.smxplore.users.identity.application.service.LoginService;
import com.smxplore.users.identity.application.service.RegisterProviderService;
import com.smxplore.users.identity.application.service.RegisterTouristService;
import com.smxplore.users.identity.application.service.ResetPasswordService;
import com.smxplore.users.identity.application.service.UpdateProfileService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {

    @Bean
    public PasswordResetPolicy passwordResetPolicy(PasswordResetProperties properties) {
        return new PasswordResetPolicy(properties.ttl());
    }

    @Bean
    public RegisterTouristUseCase registerTouristUseCase(
            UserRepository users, PasswordHasher passwordHasher, TransactionPort transactions, Clock clock) {
        return new RegisterTouristService(users, passwordHasher, transactions, clock);
    }

    @Bean
    public RegisterProviderUseCase registerProviderUseCase(
            UserRepository users, PasswordHasher passwordHasher, TransactionPort transactions, Clock clock) {
        return new RegisterProviderService(users, passwordHasher, transactions, clock);
    }

    @Bean
    public LoginUseCase loginUseCase(
            UserRepository users, PasswordHasher passwordHasher, TokenIssuer tokenIssuer, Clock clock) {
        return new LoginService(users, passwordHasher, tokenIssuer, clock);
    }

    @Bean
    public GetCurrentUserUseCase getCurrentUserUseCase(UserRepository users) {
        return new GetCurrentUserService(users);
    }

    @Bean
    public UpdateProfileUseCase updateProfileUseCase(UserRepository users, TransactionPort transactions, Clock clock) {
        return new UpdateProfileService(users, transactions, clock);
    }

    @Bean
    public ForgotPasswordUseCase forgotPasswordUseCase(
            UserRepository users,
            PasswordResetTokenRepository resetTokens,
            PasswordResetNotifier notifier,
            TokenGenerator tokenGenerator,
            TokenHasher tokenHasher,
            PasswordResetPolicy policy,
            TransactionPort transactions,
            Clock clock) {
        return new ForgotPasswordService(
                users, resetTokens, notifier, tokenGenerator, tokenHasher, policy, transactions, clock);
    }

    @Bean
    public ResetPasswordUseCase resetPasswordUseCase(
            UserRepository users,
            PasswordResetTokenRepository resetTokens,
            PasswordHasher passwordHasher,
            TokenHasher tokenHasher,
            TransactionPort transactions,
            Clock clock) {
        return new ResetPasswordService(users, resetTokens, passwordHasher, tokenHasher, transactions, clock);
    }

    @Bean
    public GetPublicProfileUseCase getPublicProfileUseCase(UserRepository users) {
        return new GetPublicProfileService(users);
    }

    @Bean
    public ChangeOwnPasswordUseCase changeOwnPasswordUseCase(
            UserRepository users,
            PasswordResetTokenRepository resetTokens,
            PasswordHasher passwordHasher,
            TransactionPort transactions,
            Clock clock) {
        return new ChangeOwnPasswordService(users, resetTokens, passwordHasher, transactions, clock);
    }

    @Bean
    public ListAuditEntriesUseCase listAuditEntriesUseCase(AuditLogRepository auditLogRepository) {
        return new ListAuditEntriesService(auditLogRepository);
    }

    @Bean
    public BootstrapAdminUseCase bootstrapAdminUseCase(
            UserRepository users, PasswordHasher passwordHasher, TransactionPort transactions, Clock clock) {
        return new BootstrapAdminService(users, passwordHasher, transactions, clock);
    }
}
