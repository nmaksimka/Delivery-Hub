package com.deliveryHub.user.service;

import com.deliveryHub.user.dto.JwtResponse;
import com.deliveryHub.user.dto.LoginRequest;
import com.deliveryHub.user.dto.RegisterRequest;
import com.deliveryHub.user.dto.UserDto;
import com.deliveryHub.user.entity.Role;
import com.deliveryHub.user.entity.User;
import com.deliveryHub.user.mapper.UserMapper;
import com.deliveryHub.user.repository.UserRepository;
import com.deliveryHub.user.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;

    @Transactional
    public UserDto register(RegisterRequest registerRequest) {
        if(userRepository.existsByEmail(registerRequest.getEmail())) throw new RuntimeException("Email already taken");

        User user = userMapper.toEntity(registerRequest);
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setRole(Role.USER);
        User savedUser = userRepository.save(user);
        return userMapper.toDto(savedUser);
    }

    public JwtResponse login(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);
        User user = (User) authentication.getPrincipal();
        return new JwtResponse(
                jwt, "Bearer", user.getId(), user.getEmail(), user.getName(), user.getRole().name()
        );
    }
}
