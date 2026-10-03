package com.betterreads.features.session;

import com.betterreads.errors.BusinessRuleException;
import com.betterreads.errors.ForbiddenException;
import com.betterreads.logging.LogSanitizer;
import com.betterreads.security.JwtIssuer;
import com.betterreads.users.EmailNormalizer;
import com.betterreads.users.EmailVerificationIssuer;
import com.betterreads.users.User;
import com.betterreads.users.UserLookup;
import com.betterreads.users.UserMapper;
import com.betterreads.users.UserPasswords;
import com.betterreads.users.UserRepository;
import com.betterreads.users.UserResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SessionServiceImpl implements SessionService {

    private static final Logger LOG = LoggerFactory.getLogger(SessionServiceImpl.class);

    private static final String DUPLICATE_USERNAME = "Username already taken";

    private static final String DUPLICATE_EMAIL = "Email already registered";

    private static final String DUPLICATE_USERNAME_OR_EMAIL = "Username or email already registered";

    private static final String INVALID_CREDENTIALS = "Invalid credentials";

    private static final String EMAIL_NOT_VERIFIED = "Verify your email to log in";

    private static final String INVALID_REFRESH_TOKEN = "Invalid refresh token";

    private final UserRepository userRepository;

    private final UserPasswords userPasswords;

    private final UserLookup userLookup;

    private final JwtIssuer jwtIssuer;

    private final UserMapper userMapper;

    private final RefreshTokenService refreshTokenService;

    private final EmailVerificationIssuer emailVerificationIssuer;

    // PMD.ExcessiveParameterList: register, login, current user and refresh each need a different mix of beans.
    @SuppressWarnings("PMD.ExcessiveParameterList")
    SessionServiceImpl(
        final UserRepository userRepository,
        final UserPasswords userPasswords,
        final UserLookup userLookup,
        final JwtIssuer jwtIssuer,
        final UserMapper userMapper,
        final RefreshTokenService refreshTokenService,
        final EmailVerificationIssuer emailVerificationIssuer
    ) {
        this.userRepository = userRepository;
        this.userPasswords = userPasswords;
        this.userLookup = userLookup;
        this.jwtIssuer = jwtIssuer;
        this.userMapper = userMapper;
        this.refreshTokenService = refreshTokenService;
        this.emailVerificationIssuer = emailVerificationIssuer;
    }

    @Override
    @Transactional
    public void register(final RegisterRequest request) {
        final String normalizedEmail = EmailNormalizer.normalize(request.email());

        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessRuleException(DUPLICATE_USERNAME);
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessRuleException(DUPLICATE_EMAIL);
        }

        final User user = new User();
        user.setUsername(request.username());
        user.setEmail(normalizedEmail);
        userPasswords.setPassword(user, request.password());

        final User saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (final DataIntegrityViolationException ex) {
            LOG.warn("Registration conflicted with existing username or email username={}",
                LogSanitizer.forLog(request.username()));
            throw new BusinessRuleException(DUPLICATE_USERNAME_OR_EMAIL, ex);
        }
        emailVerificationIssuer.issueVerification(saved.getUserId(), saved.getEmail());
        LOG.info("Registered new user userId={} username={}",
            saved.getUserId(), LogSanitizer.forLog(saved.getUsername()));
    }

    @Override
    @Transactional
    public SessionTokens login(final LoginRequest request) {
        final String identifier = request.identifier().trim();
        final String email = EmailNormalizer.normalize(identifier);
        final User user = userRepository.findIdByUsername(identifier)
            .or(() -> userRepository.findIdByEmail(email))
            .flatMap(userRepository::findByIdForUpdate)
            .orElseThrow(() -> {
                LOG.warn("Login failed: no user matches identifier");
                return new BadCredentialsException(INVALID_CREDENTIALS);
            });

        if (!userPasswords.matches(user, request.password())) {
            LOG.warn("Login failed: password mismatch userId={}", user.getUserId());
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }
        if (user.getEmailVerifiedAt() == null) {
            LOG.warn("Login refused: email not verified userId={}", user.getUserId());
            throw new ForbiddenException(EMAIL_NOT_VERIFIED);
        }
        LOG.info("Logged in user userId={}", user.getUserId());
        final boolean persistent = Boolean.TRUE.equals(request.rememberMe());
        return sessionTokensFor(user, refreshTokenService.issue(user.getUserId(), persistent));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse currentUser(final long userId) {
        return userMapper.toResponse(userLookup.require(userId));
    }

    @Override
    @Transactional
    public SessionTokens refresh(final String refreshToken) {
        final RefreshTokenRotation rotation = refreshTokenService.rotate(refreshToken)
            .orElseThrow(() -> {
                LOG.warn("Refresh rejected: token unknown, expired, or already revoked");
                return new BadCredentialsException(INVALID_REFRESH_TOKEN);
            });
        return sessionTokensFor(userLookup.require(rotation.userId()), rotation.grant());
    }

    @Override
    @Transactional
    public void logout(final String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private SessionTokens sessionTokensFor(final User user, final RefreshGrant grant) {
        final String accessToken = jwtIssuer.issue(user.getUserId(), user.getCredentialVersion());
        return new SessionTokens(new AuthResponse(accessToken, userMapper.toResponse(user)), grant);
    }
}
