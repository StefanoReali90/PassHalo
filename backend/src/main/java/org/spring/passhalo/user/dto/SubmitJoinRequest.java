package org.spring.passhalo.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitJoinRequest(@NotBlank @Size(max = 64) String code) {
}
