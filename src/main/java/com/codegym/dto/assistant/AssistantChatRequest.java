package com.codegym.dto.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssistantChatRequest(
        @NotBlank(message = "Vui lòng nhập nội dung.")
        @Size(max = 500, message = "Tin nhắn không được vượt quá 500 ký tự.")
        String message) {
}
