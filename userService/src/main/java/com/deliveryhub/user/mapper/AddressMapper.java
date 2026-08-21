package com.deliveryhub.user.mapper;

import com.deliveryhub.user.dto.AddressDto;
import com.deliveryhub.user.dto.AddressRequest;
import com.deliveryhub.user.entity.Address;
import com.deliveryhub.user.entity.AddressDetails;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AddressMapper {

    AddressDto toDto(Address address);

    AddressDetails toDetails(AddressRequest request);
}
