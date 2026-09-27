package org.spring.passhalo.user.dto;

import jakarta.validation.constraints.NotBlank;

public record SubmitStaffAccessRequest(@NotBlank String code) {
}
