package com.codegym.controller.assistant;

import com.codegym.dto.assistant.AssistantChatRequest;
import com.codegym.dto.assistant.AssistantChatResponse;
import com.codegym.dto.assistant.AssistantChatRequest;
import com.codegym.dto.assistant.AssistantChatResponse;
import com.codegym.service.assistant.ClinicAssistantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class ClinicAssistantController {
    private final ClinicAssistantService assistantService;

    public ClinicAssistantController(ClinicAssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/chat")
    public AssistantChatResponse chat(@Valid @RequestBody AssistantChatRequest request) {
        return assistantService.reply(request.message().trim());
    }
}
