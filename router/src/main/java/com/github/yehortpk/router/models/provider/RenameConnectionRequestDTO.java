package com.github.yehortpk.router.models.provider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameConnectionRequestDTO(@NotBlank @Size(max = 255) String title) {
}
