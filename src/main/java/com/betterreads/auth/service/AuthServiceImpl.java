package com.betterreads.auth.service;

import com.betterreads.auth.dto.AuthResponse;
import com.betterreads.auth.dto.LoginRequest;
import com.betterreads.auth.dto.RegisterRequest;
import com.betterreads.auth.dto.UserResponse;
import com.betterreads.auth.entity.User;
import com.betterreads.auth.jwt.JwtIssuer;
import com.betterreads.auth.mapper.UserMapper;
import com.betterreads.auth.repository.UserRepository;
import com.betterreads.common.exception.BusinessRuleException;
import com.betterreads.common.exception.ResourceNotFoundException;
import com.betterreads.common.util.LogSanitizer;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link AuthService}. Delegates persistence to {@link UserRepository}, password hashing
 * to {@link PasswordEncoder}, and token signing to {@link JwtIssuer}. Login accepts either a
 * username or an email; the lookup tries username first, then falls back to email.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger LOG = LoggerFactory.getLogger(AuthServiceImpl.class);

    private static final String DUPLICATE_USERNAME = "Username already taken";

    private static final String DUPLICATE_EMAIL = "Email already registered";

    private static final String DUPLICATE_USERNAME_OR_EMAIL = "Username or email already registered";

    private static final String INVALID_CREDENTIALS = "Invalid credentials";

    private static final String USER_NOT_FOUND = "User not found";

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtIssuer jwtIssuer;

    private final UserMapper userMapper;

    /**
     * Constructor injection of the four collaborators the auth flow needs.
     */
    public AuthServiceImpl(
        final UserRepository userRepository,
        final PasswordEncoder passwordEncoder,
        final JwtIssuer jwtIssuer,
        final UserMapper userMapper
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
        this.userMapper = userMapper;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The {@code existsBy*} pre-checks return per-field errors on the normal path. The
     * {@code DataIntegrityViolationException} catch covers the race window between pre-check
     * and insert when two concurrent requests both pass and one loses the unique constraint
     * at commit time. Without the pre-checks, every duplicate register surfaces the same
     * combined-field message.
     */
    @Override
    @Transactional
    public AuthResponse register(final RegisterRequest request) {
        final String normalizedEmail = normalizeEmail(request.email());

        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessRuleException(DUPLICATE_USERNAME);
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessRuleException(DUPLICATE_EMAIL);
        }

        final User user = new User();
        user.setUsername(request.username());
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        final User saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (final DataIntegrityViolationException ex) {
            LOG.warn("auth.register.conflict username={}", LogSanitizer.forLog(request.username()));
            throw new BusinessRuleException(DUPLICATE_USERNAME_OR_EMAIL, ex);
        }
        LOG.info("auth.register.success userId={} username={}",
            saved.getUserId(), LogSanitizer.forLog(saved.getUsername()));
        return buildAuthResponse(saved);
    }

    private static String normalizeEmail(final String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(final LoginRequest request) {
        final String identifier = request.identifier().trim();
        final String normalizedEmailIdentifier = normalizeEmail(identifier);
        final User user = userRepository.findByUsername(identifier)
            .or(() -> userRepository.findByEmail(normalizedEmailIdentifier))
            .orElseThrow(() -> {
                LOG.warn("auth.login.failed reason=user-not-found");
                return new BadCredentialsException(INVALID_CREDENTIALS);
            });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            LOG.warn("auth.login.failed reason=bad-password userId={}", user.getUserId());
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }
        LOG.info("auth.login.success userId={}", user.getUserId());
        return buildAuthResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse currentUser(final long userId) {
        final User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));
        return userMapper.toResponse(user);
    }

    private AuthResponse buildAuthResponse(final User user) {
        final String token = jwtIssuer.issue(user.getUserId());
        return new AuthResponse(token, userMapper.toResponse(user));
    }
}
