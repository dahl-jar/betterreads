package com.betterreads.features.session;

import com.betterreads.crypto.PasswordByteLimit;
import com.betterreads.errors.BusinessRuleException;
import com.betterreads.logging.LogSanitizer;
import com.betterreads.security.JwtIssuer;
import com.betterreads.users.EmailNormalizer;
import com.betterreads.users.EmailVerificationIssuer;
import com.betterreads.users.User;
import com.betterreads.users.UserMapper;
import com.betterreads.users.UserRepository;
import com.betterreads.users.UserResponse;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SessionServiceImpl implements SessionService {

    private static final Logger LOG = LoggerFactory.getLogger(SessionServiceImpl.class);

    private static final String DUPLICATE_USERNAME = "Username already taken";

    private static final String DUPLICATE_EMAIL = "Email already registered";

    private static final String DUPLICATE_USERNAME_OR_EMAIL = "Username or email already registered";

    private static final String INVALID_CREDENTIALS = "Invalid credentials";

    private static final String INVALID_REFRESH_TOKEN = "Invalid refresh token";

    private static final String SESSION_NO_LONGER_VALID = "Session no longer valid";

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtIssuer jwtIssuer;

    private final UserMapper userMapper;

    private final RefreshTokenService refreshTokenService;

    private final EmailVerificationIssuer emailVerificationIssuer;

    // PMD.ExcessiveParameterList: register, login and refresh each need a different mix of these six beans.
    @SuppressWarnings("PMD.ExcessiveParameterList")
    SessionServiceImpl(
        final UserRepository userRepository,
        final PasswordEncoder passwordEncoder,
        final JwtIssuer jwtIssuer,
        final UserMapper userMapper,
        final RefreshTokenService refreshTokenService,
        final EmailVerificationIssuer emailVerificationIssuer
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
        this.userMapper = userMapper;
        this.refreshTokenService = refreshTokenService;
        this.emailVerificationIssuer = emailVerificationIssuer;
    }

    @Override
    @Transactional
    public SessionTokens register(final RegisterRequest request) {
        final String normalizedEmail = EmailNormalizer.normalize(request.email());
        PasswordByteLimit.check(request.password());

        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessRuleException(DUPLICATE_USERNAME);
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessRuleException(DUPLICATE_EMAIL);
        }

        final User user = new User();
        user.setUsername(request.username());
        user.setEmail(normalizedEmail);
        user.setPasswordHash(Objects.requireNonNull(passwordEncoder.encode(request.password())));

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
        return sessionTokensFor(saved, refreshTokenService.issue(saved.getUserId()));
    }

    @Override
    @Transactional
    public SessionTokens login(final LoginRequest request) {
        final String identifier = request.identifier().trim();
        final String email = EmailNormalizer.normalize(identifier);
        final User user = userRepository.findByUsername(identifier)
            .or(() -> userRepository.findByEmail(email))
            .orElseThrow(() -> {
                LOG.warn("Login failed: no user matches identifier");
                return new BadCredentialsException(INVALID_CREDENTIALS);
            });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            LOG.warn("Login failed: password mismatch userId={}", user.getUserId());
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }
        LOG.info("Logged in user userId={}", user.getUserId());
        return sessionTokensFor(user, refreshTokenService.issue(user.getUserId()));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse currentUser(final long userId) {
        final User user = userRepository.findById(userId)
            .orElseThrow(() -> {
                LOG.warn("Current-user lookup rejected: bearer points to deleted or missing user userId={}", userId);
                return new BadCredentialsException(SESSION_NO_LONGER_VALID);
            });
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public SessionTokens refresh(final String refreshToken) {
        final RefreshTokenRotation rotation = refreshTokenService.rotate(refreshToken)
            .orElseThrow(() -> {
                LOG.warn("Refresh rejected: token unknown, expired, or already revoked");
                return new BadCredentialsException(INVALID_REFRESH_TOKEN);
            });
        final long userId = rotation.userId();
        final User user = userRepository.findById(userId)
            .orElseThrow(() -> {
                LOG.warn("Refresh rejected: token owner is gone userId={}", userId);
                return new BadCredentialsException(INVALID_REFRESH_TOKEN);
            });
        return sessionTokensFor(user, rotation.plaintext());
    }

    @Override
    @Transactional
    public void logout(final String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private SessionTokens sessionTokensFor(final User user, final String refreshToken) {
        return new SessionTokens(
            new AuthResponse(jwtIssuer.issue(user.getUserId()), userMapper.toResponse(user)), refreshToken);
    }
}
