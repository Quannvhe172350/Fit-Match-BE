package com.fitmatch.dto.admin;

import com.fitmatch.common.enums.Role;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignRoleRequest {

    @NotNull(message = "Role is required")
    private Role role;
}
