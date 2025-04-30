package com.playdata.orderingservice.ordering.dto;

import com.playdata.orderingservice.common.auth.Role;
import com.playdata.orderingservice.common.entity.Address;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter @ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResDto {

    private Long id;
    private String email;
    private String name;
    private Role role;
    private Address address;

}
