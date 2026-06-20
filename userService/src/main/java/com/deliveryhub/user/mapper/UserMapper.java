package com.deliveryhub.user.mapper;

import com.deliveryhub.user.dto.UserDto;
import com.deliveryhub.user.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserDto toDto(User user);
}
