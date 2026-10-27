package com.smxplore.users.infrastructure.config;

import com.smxplore.users.application.port.in.AdminResetPasswordUseCase;
import com.smxplore.users.application.port.in.BootstrapAdminUseCase;
import com.smxplore.users.application.port.in.ChangeOwnPasswordUseCase;
import com.smxplore.users.application.port.in.ChangeUserRoleUseCase;
import com.smxplore.users.application.port.in.ChangeUserStatusUseCase;
import com.smxplore.users.application.port.in.ClassifyAnomalyUseCase;
import com.smxplore.users.application.port.in.CreateInternalUserUseCase;
import com.smxplore.users.application.port.in.CreateReviewUseCase;
import com.smxplore.users.application.port.in.DeleteUserUseCase;
import com.smxplore.users.application.port.in.DetectAnomaliesUseCase;
import com.smxplore.users.application.port.in.ForgotPasswordUseCase;
import com.smxplore.users.application.port.in.GetAnomalyUseCase;
import com.smxplore.users.application.port.in.GetCurrentUserUseCase;
import com.smxplore.users.application.port.in.GetPublicProfileUseCase;
import com.smxplore.users.application.port.in.GetReputationSummaryUseCase;
import com.smxplore.users.application.port.in.GetUserDetailUseCase;
import com.smxplore.users.application.port.in.ListAnomaliesUseCase;
import com.smxplore.users.application.port.in.ListAnomalyThresholdsUseCase;
import com.smxplore.users.application.port.in.ListAuditEntriesUseCase;
import com.smxplore.users.application.port.in.ListReportedReviewsUseCase;
import com.smxplore.users.application.port.in.ListReviewsUseCase;
import com.smxplore.users.application.port.in.LoginUseCase;
import com.smxplore.users.application.port.in.ModerateReviewUseCase;
import com.smxplore.users.application.port.in.RegisterProviderUseCase;
import com.smxplore.users.application.port.in.RegisterTouristUseCase;
import com.smxplore.users.application.port.in.ReportReviewUseCase;
import com.smxplore.users.application.port.in.ResetPasswordUseCase;
import com.smxplore.users.application.port.in.ResolveAnomalyUseCase;
import com.smxplore.users.application.port.in.RespondReviewUseCase;
import com.smxplore.users.application.port.in.ReviewProviderUseCase;
import com.smxplore.users.application.port.in.SearchUsersUseCase;
import com.smxplore.users.application.port.in.UpdateAnomalyThresholdUseCase;
import com.smxplore.users.application.port.in.UpdateProfileUseCase;
import com.smxplore.users.application.port.out.AccountInvitationNotifier;
import com.smxplore.users.application.port.out.AnomalyRepository;
import com.smxplore.users.application.port.out.AnomalyThresholdRepository;
import com.smxplore.users.application.port.out.AuditLogRepository;
import com.smxplore.users.application.port.out.FailedLoginSignal;
import com.smxplore.users.application.port.out.PasswordHasher;
import com.smxplore.users.application.port.out.PasswordResetNotifier;
import com.smxplore.users.application.port.out.PasswordResetTokenRepository;
import com.smxplore.users.application.port.out.ProviderReviewNotifier;
import com.smxplore.users.application.port.out.RequestRateSignal;
import com.smxplore.users.application.port.out.ReviewRepository;
import com.smxplore.users.application.port.out.ServiceHealthSignal;
import com.smxplore.users.application.port.out.TokenGenerator;
import com.smxplore.users.application.port.out.TokenHasher;
import com.smxplore.users.application.port.out.TokenIssuer;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.application.service.AdminResetPasswordService;
import com.smxplore.users.application.service.AnomalyDetectionService;
import com.smxplore.users.application.service.AuditTrailRecorder;
import com.smxplore.users.application.service.BootstrapAdminService;
import com.smxplore.users.application.service.ChangeOwnPasswordService;
import com.smxplore.users.application.service.ChangeUserRoleService;
import com.smxplore.users.application.service.ChangeUserStatusService;
import com.smxplore.users.application.service.ClassifyAnomalyService;
import com.smxplore.users.application.service.CreateInternalUserService;
import com.smxplore.users.application.service.CreateReviewService;
import com.smxplore.users.application.service.DeleteUserService;
import com.smxplore.users.application.service.ForgotPasswordService;
import com.smxplore.users.application.service.GetAnomalyService;
import com.smxplore.users.application.service.GetCurrentUserService;
import com.smxplore.users.application.service.GetPublicProfileService;
import com.smxplore.users.application.service.GetReputationSummaryService;
import com.smxplore.users.application.service.GetUserDetailService;
import com.smxplore.users.application.service.ListAnomaliesService;
import com.smxplore.users.application.service.ListAnomalyThresholdsService;
import com.smxplore.users.application.service.ListAuditEntriesService;
import com.smxplore.users.application.service.ListReportedReviewsService;
import com.smxplore.users.application.service.ListReviewsService;
import com.smxplore.users.application.service.LoginService;
import com.smxplore.users.application.service.ModerateReviewService;
import com.smxplore.users.application.service.RegisterProviderService;
import com.smxplore.users.application.service.RegisterTouristService;
import com.smxplore.users.application.service.ReportReviewService;
import com.smxplore.users.application.service.ResetPasswordService;
import com.smxplore.users.application.service.ResolveAnomalyService;
import com.smxplore.users.application.service.RespondReviewService;
import com.smxplore.users.application.service.ReviewProviderService;
import com.smxplore.users.application.service.SearchUsersService;
import com.smxplore.users.application.service.UpdateAnomalyThresholdService;
import com.smxplore.users.application.service.UpdateProfileService;
import com.smxplore.users.domain.policy.AccountInvitationPolicy;
import com.smxplore.users.domain.policy.PasswordResetPolicy;
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
    public AccountInvitationPolicy accountInvitationPolicy(AccountInvitationProperties properties) {
        return new AccountInvitationPolicy(properties.ttl());
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
            UserRepository users,
            PasswordHasher passwordHasher,
            TokenIssuer tokenIssuer,
            FailedLoginSignal failedLogins,
            Clock clock) {
        return new LoginService(users, passwordHasher, tokenIssuer, failedLogins, clock);
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
    public SearchUsersUseCase searchUsersUseCase(UserRepository users) {
        return new SearchUsersService(users);
    }

    @Bean
    public GetUserDetailUseCase getUserDetailUseCase(UserRepository users) {
        return new GetUserDetailService(users);
    }

    @Bean
    public AuditTrailRecorder auditTrailRecorder(UserRepository users, AuditLogRepository auditLogRepository) {
        return new AuditTrailRecorder(users, auditLogRepository);
    }

    @Bean
    public ChangeUserStatusUseCase changeUserStatusUseCase(
            UserRepository users, AuditTrailRecorder auditTrailRecorder, TransactionPort transactions, Clock clock) {
        return new ChangeUserStatusService(users, auditTrailRecorder, transactions, clock);
    }

    @Bean
    public ChangeUserRoleUseCase changeUserRoleUseCase(
            UserRepository users, AuditTrailRecorder auditTrailRecorder, TransactionPort transactions, Clock clock) {
        return new ChangeUserRoleService(users, auditTrailRecorder, transactions, clock);
    }

    @Bean
    public DeleteUserUseCase deleteUserUseCase(
            UserRepository users,
            PasswordResetTokenRepository resetTokens,
            PasswordHasher passwordHasher,
            AuditTrailRecorder auditTrailRecorder,
            TransactionPort transactions,
            Clock clock) {
        return new DeleteUserService(users, resetTokens, passwordHasher, auditTrailRecorder, transactions, clock);
    }

    @Bean
    public ReviewProviderUseCase reviewProviderUseCase(
            UserRepository users,
            AuditTrailRecorder auditTrailRecorder,
            ProviderReviewNotifier providerReviewNotifier,
            TransactionPort transactions,
            Clock clock) {
        return new ReviewProviderService(users, auditTrailRecorder, providerReviewNotifier, transactions, clock);
    }

    @Bean
    public CreateInternalUserUseCase createInternalUserUseCase(
            UserRepository users,
            PasswordResetTokenRepository resetTokens,
            PasswordHasher passwordHasher,
            TokenGenerator tokenGenerator,
            TokenHasher tokenHasher,
            AccountInvitationNotifier accountInvitationNotifier,
            AccountInvitationPolicy accountInvitationPolicy,
            AuditTrailRecorder auditTrailRecorder,
            TransactionPort transactions,
            Clock clock) {
        return new CreateInternalUserService(
                users,
                resetTokens,
                passwordHasher,
                tokenGenerator,
                tokenHasher,
                accountInvitationNotifier,
                accountInvitationPolicy,
                auditTrailRecorder,
                transactions,
                clock);
    }

    @Bean
    public AdminResetPasswordUseCase adminResetPasswordUseCase(
            UserRepository users,
            PasswordResetTokenRepository resetTokens,
            PasswordResetNotifier passwordResetNotifier,
            TokenGenerator tokenGenerator,
            TokenHasher tokenHasher,
            PasswordResetPolicy passwordResetPolicy,
            AuditTrailRecorder auditTrailRecorder,
            TransactionPort transactions,
            Clock clock) {
        return new AdminResetPasswordService(
                users,
                resetTokens,
                passwordResetNotifier,
                tokenGenerator,
                tokenHasher,
                passwordResetPolicy,
                auditTrailRecorder,
                transactions,
                clock);
    }

    @Bean
    public BootstrapAdminUseCase bootstrapAdminUseCase(
            UserRepository users, PasswordHasher passwordHasher, TransactionPort transactions, Clock clock) {
        return new BootstrapAdminService(users, passwordHasher, transactions, clock);
    }

    @Bean
    public CreateReviewUseCase createReviewUseCase(ReviewRepository reviews, UserRepository users, Clock clock) {
        return new CreateReviewService(reviews, users, clock);
    }

    @Bean
    public ListReviewsUseCase listReviewsUseCase(ReviewRepository reviews) {
        return new ListReviewsService(reviews);
    }

    @Bean
    public GetReputationSummaryUseCase getReputationSummaryUseCase(ReviewRepository reviews) {
        return new GetReputationSummaryService(reviews);
    }

    @Bean
    public RespondReviewUseCase respondReviewUseCase(ReviewRepository reviews, Clock clock) {
        return new RespondReviewService(reviews, clock);
    }

    @Bean
    public ReportReviewUseCase reportReviewUseCase(ReviewRepository reviews, Clock clock) {
        return new ReportReviewService(reviews, clock);
    }

    @Bean
    public ListReportedReviewsUseCase listReportedReviewsUseCase(ReviewRepository reviews) {
        return new ListReportedReviewsService(reviews);
    }

    @Bean
    public ModerateReviewUseCase moderateReviewUseCase(
            ReviewRepository reviews, AuditTrailRecorder auditTrailRecorder, Clock clock) {
        return new ModerateReviewService(reviews, auditTrailRecorder, clock);
    }

    @Bean
    public ListAnomaliesUseCase listAnomaliesUseCase(AnomalyRepository anomalies) {
        return new ListAnomaliesService(anomalies);
    }

    @Bean
    public GetAnomalyUseCase getAnomalyUseCase(AnomalyRepository anomalies) {
        return new GetAnomalyService(anomalies);
    }

    @Bean
    public ClassifyAnomalyUseCase classifyAnomalyUseCase(
            AnomalyRepository anomalies,
            AuditTrailRecorder auditTrailRecorder,
            TransactionPort transactions,
            Clock clock) {
        return new ClassifyAnomalyService(anomalies, auditTrailRecorder, transactions, clock);
    }

    @Bean
    public ResolveAnomalyUseCase resolveAnomalyUseCase(
            AnomalyRepository anomalies,
            AuditTrailRecorder auditTrailRecorder,
            TransactionPort transactions,
            Clock clock) {
        return new ResolveAnomalyService(anomalies, auditTrailRecorder, transactions, clock);
    }

    @Bean
    public ListAnomalyThresholdsUseCase listAnomalyThresholdsUseCase(
            AnomalyThresholdRepository thresholds, Clock clock) {
        return new ListAnomalyThresholdsService(thresholds, clock);
    }

    @Bean
    public UpdateAnomalyThresholdUseCase updateAnomalyThresholdUseCase(
            AnomalyThresholdRepository thresholds,
            AuditTrailRecorder auditTrailRecorder,
            TransactionPort transactions,
            Clock clock) {
        return new UpdateAnomalyThresholdService(thresholds, auditTrailRecorder, transactions, clock);
    }

    @Bean
    public DetectAnomaliesUseCase detectAnomaliesUseCase(
            AnomalyRepository anomalies,
            AnomalyThresholdRepository thresholds,
            FailedLoginSignal failedLogins,
            RequestRateSignal requestRate,
            ServiceHealthSignal health,
            Clock clock) {
        return new AnomalyDetectionService(anomalies, thresholds, failedLogins, requestRate, health, clock);
    }
}
