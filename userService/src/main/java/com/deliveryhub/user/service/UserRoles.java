package com.deliveryhub.user.service;

import com.deliveryhub.user.dto.UserDto;
import com.deliveryhub.user.entity.Role;
import com.deliveryhub.user.entity.User;
import com.deliveryhub.user.exception.OwnRoleChangeException;
import com.deliveryhub.user.exception.UserNotFoundException;
import com.deliveryhub.user.mapper.UserMapper;
import com.deliveryhub.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@Transactional(readOnly = true)
public class UserRoles {

    private static final Logger log = LoggerFactory.getLogger(UserRoles.class);

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final Clock clock;

    public UserRoles(UserRepository userRepository, UserMapper userMapper, Clock clock) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.clock = clock;
    }

    public UserDto findByEmail(String email) {
        String normalized = User.normalizeEmail(email);
        return userRepository.findUserByEmail(normalized)
                .map(userMapper::toDto)
                .orElseThrow(() -> UserNotFoundException.byEmail(normalized));
    }

    @Transactional
    public UserDto changeRole(Long userId, Role role, Long adminId) {
        if (userId.equals(adminId)) {
            throw new OwnRoleChangeException();
        }
        User user = userRepository.findById(userId).orElseThrow(() -> UserNotFoundException.byId(userId));
        Role previous = user.getRole();
        user.changeRole(role, clock.instant());
        log.info("Role of user id={} changed from {} to {} by admin id={}", userId, previous, role, adminId);
        return userMapper.toDto(user);
    }
}
