package com.deliveryhub.user.service;

import com.deliveryhub.user.UserFixtures;
import com.deliveryhub.user.dto.TokenResponse;
import com.deliveryhub.user.dto.LoginRequest;
import com.deliveryhub.user.dto.RegisterRequest;
import com.deliveryhub.user.dto.UserDto;
import com.deliveryhub.user.entity.Role;
import com.deliveryhub.user.entity.User;
import com.deliveryhub.user.exception.EmailAlreadyUsedException;
import com.deliveryhub.user.mapper.UserMapper;
import com.deliveryhub.user.repository.UserRepository;
import com.deliveryhub.user.exception.InvalidRefreshTokenException;
import com.deliveryhub.user.security.AccessTokens;
import com.deliveryhub.user.security.AppUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    private static final String EMAIL = "test@example.com";
    private static final String RAW_PASSWORD = "secret123";
    private static final Instant NOW = Instant.parse("2026-07-14T10:15:30Z");

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private AccessTokens accessTokens;
    @Mock
    private RefreshTokens refreshTokens;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, userMapper, passwordEncoder, authenticationManager,
                accessTokens, refreshTokens, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("хеширует пароль и назначает роль USER при регистрации")
    void hashesPasswordAndAssignsRole() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn("$2a$hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toDto(any(User.class))).thenReturn(userDto());

        authService.register(registerRequest());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());

        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getPassword()).isEqualTo("$2a$hashed");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getPhone()).isEqualTo("+79990000000");
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("бросает EmailAlreadyUsedException при занятом email")
    void rejectsDuplicateEmail() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest()))
                .isInstanceOf(EmailAlreadyUsedException.class)
                .hasMessageContaining(EMAIL);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("приводит email к нижнему регистру при регистрации")
    void storesEmailInLowerCase() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn("$2a$hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register(new RegisterRequest("  " + EMAIL.toUpperCase() + " ", RAW_PASSWORD, "Test", null));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("гонка двух регистраций заканчивается EmailAlreadyUsedException, а не ошибкой базы")
    void translatesUniqueViolationOnRace() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn("$2a$hashed");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("users_email_key"));

        assertThatThrownBy(() -> authService.register(registerRequest()))
                .isInstanceOf(EmailAlreadyUsedException.class);
    }

    @Test
    @DisplayName("возвращает пару токенов и профиль при успешном входе")
    void returnsTokenOnLogin() {
        User user = UserFixtures.persistedUser(7L, EMAIL, Role.USER);
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(AppUserPrincipal.of(user), null, List.of());

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(accessTokens.issue(user)).thenReturn("jwt-token");
        when(accessTokens.lifetime()).thenReturn(Duration.ofMinutes(15));
        when(refreshTokens.issueFor(7L)).thenReturn("refresh-token");

        TokenResponse response = authService.login(loginRequest());

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.expiresIn()).isEqualTo(900);
        assertThat(response.type()).isEqualTo("Bearer");
        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.email()).isEqualTo(EMAIL);
        assertThat(response.role()).isEqualTo("USER");
    }

    @Test
    @DisplayName("пробрасывает ошибку аутентификации при неверном пароле")
    void propagatesBadCredentials() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(loginRequest()))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("по refresh-токену выдаёт новую пару для его владельца")
    void refreshIssuesNewPairForTokenOwner() {
        User user = UserFixtures.persistedUser(7L, EMAIL, Role.COURIER);
        when(refreshTokens.rotate("old-refresh")).thenReturn(new RefreshTokens.Rotation(7L, "new-refresh"));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(accessTokens.issue(user)).thenReturn("new-jwt");
        when(accessTokens.lifetime()).thenReturn(Duration.ofMinutes(15));

        TokenResponse response = authService.refresh("old-refresh");

        assertThat(response.accessToken()).isEqualTo("new-jwt");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        assertThat(response.role()).isEqualTo("COURIER");
    }

    @Test
    @DisplayName("не выдаёт токены, если владелец refresh-токена удалён")
    void refreshFailsWhenOwnerIsGone() {
        when(refreshTokens.rotate("old-refresh")).thenReturn(new RefreshTokens.Rotation(7L, "new-refresh"));
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("old-refresh"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(accessTokens, never()).issue(any());
    }

    @Test
    @DisplayName("выход отзывает refresh-токен")
    void logoutRevokesRefreshToken() {
        authService.logout("refresh-token");

        verify(refreshTokens).revoke("refresh-token");
    }

    private static RegisterRequest registerRequest() {
        return new RegisterRequest(EMAIL, RAW_PASSWORD, "Test", "+79990000000");
    }

    private static LoginRequest loginRequest() {
        return new LoginRequest(EMAIL, RAW_PASSWORD);
    }

    private static UserDto userDto() {
        return new UserDto(1L, EMAIL, "Test", "+79990000000", "USER", Instant.EPOCH);
    }
}
