package com.deliveryhub.user.service;

import com.deliveryhub.user.dto.LoginRequest;
import com.deliveryhub.user.dto.RegisterRequest;
import com.deliveryhub.user.dto.TokenResponse;
import com.deliveryhub.user.dto.UserDto;
import com.deliveryhub.user.entity.User;
import com.deliveryhub.user.exception.EmailAlreadyUsedException;
import com.deliveryhub.user.exception.InvalidRefreshTokenException;
import com.deliveryhub.user.mapper.UserMapper;
import com.deliveryhub.user.repository.UserRepository;
import com.deliveryhub.user.security.AccessTokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final AccessTokens accessTokens;
    private final RefreshTokens refreshTokens;
    private final Clock clock;

    public AuthService(UserRepository userRepository,
                       UserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       AccessTokens accessTokens,
                       RefreshTokens refreshTokens,
                       Clock clock) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    @Transactional
    public UserDto register(RegisterRequest registerRequest) {
        if (userRepository.existsByEmail(registerRequest.email())) {
            throw new EmailAlreadyUsedException(registerRequest.email());
        }

        User user = User.register(
                registerRequest.email(),
                passwordEncoder.encode(registerRequest.password()),
                registerRequest.name(),
                registerRequest.phone(),
                clock.instant());

        User savedUser = userRepository.save(user);
        log.info("Registered user id={}", savedUser.getId());
        return userMapper.toDto(savedUser);
    }

    public TokenResponse login(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.email(), loginRequest.password()));

        User user = (User) authentication.getPrincipal();
        return tokensFor(user, refreshTokens.issueFor(user.getId()));
    }

    public TokenResponse refresh(String refreshToken) {
        RefreshTokens.Rotation rotation = refreshTokens.rotate(refreshToken);
        User user = userRepository.findById(rotation.userId())
                .orElseThrow(() -> new InvalidRefreshTokenException("Owner of the refresh token no longer exists"));
        return tokensFor(user, rotation.refreshToken());
    }

    public void logout(String refreshToken) {
        refreshTokens.revoke(refreshToken);
    }

    private TokenResponse tokensFor(User user, String refreshToken) {
        return TokenResponse.bearer(accessTokens.issue(user), refreshToken, accessTokens.lifetime(), user);
    }
}
