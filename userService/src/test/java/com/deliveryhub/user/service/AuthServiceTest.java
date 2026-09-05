package com.deliveryhub.user.service;

import com.deliveryhub.user.dto.JwtResponse;
import com.deliveryhub.user.dto.LoginRequest;
import com.deliveryhub.user.dto.RegisterRequest;
import com.deliveryhub.user.dto.UserDto;
import com.deliveryhub.user.entity.Role;
import com.deliveryhub.user.entity.User;
import com.deliveryhub.user.exception.EmailAlreadyUsedException;
import com.deliveryhub.user.mapper.UserMapper;
import com.deliveryhub.user.repository.UserRepository;
import com.deliveryhub.user.security.JwtUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

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

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("хеширует пароль и назначает роль USER при регистрации")
    void hashesPasswordAndAssignsRole() {
        RegisterRequest request = registerRequest();
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(userMapper.toEntity(request)).thenReturn(User.builder().email(EMAIL).name("Test").build());
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn("$2a$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toDto(any(User.class))).thenReturn(new UserDto());

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        assertThat(captor.getValue().getPassword()).isEqualTo("$2a$hashed");
        assertThat(captor.getValue().getPassword()).isNotEqualTo(RAW_PASSWORD);
        assertThat(captor.getValue().getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("роль из запроса нельзя подставить: всегда USER")
    void alwaysRegistersAsUser() {
        RegisterRequest request = registerRequest();
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(userMapper.toEntity(request)).thenReturn(User.builder().email(EMAIL).role(Role.ADMIN).build());
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn("$2a$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toDto(any(User.class))).thenReturn(new UserDto());

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("бросает EmailAlreadyUsedException вместо RuntimeException при занятом email")
    void rejectsDuplicateEmail() {
        RegisterRequest request = registerRequest();
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyUsedException.class)
                .hasMessageContaining(EMAIL);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("возвращает токен и профиль при успешном входе")
    void returnsTokenOnLogin() {
        User user = User.builder().id(7L).email(EMAIL).name("Test").role(Role.USER).build();
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(user, null, List.of());

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtUtils.generateToken(user)).thenReturn("jwt-token");

        JwtResponse response = authService.login(loginRequest());

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getType()).isEqualTo("Bearer");
        assertThat(response.getId()).isEqualTo(7L);
        assertThat(response.getEmail()).isEqualTo(EMAIL);
        assertThat(response.getRole()).isEqualTo("USER");
    }

    @Test
    @DisplayName("пробрасывает ошибку аутентификации при неверном пароле")
    void propagatesBadCredentials() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(loginRequest()))
                .isInstanceOf(BadCredentialsException.class);
    }

    private static RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(EMAIL);
        request.setPassword(RAW_PASSWORD);
        request.setName("Test");
        return request;
    }

    private static LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setEmail(EMAIL);
        request.setPassword(RAW_PASSWORD);
        return request;
    }
}
