package com.codegym.dto.assistant;

import java.util.List;

public record AssistantChatResponse(String message, List<AssistantLink> links) {
    public AssistantChatResponse {
        links = links == null ? List.of() : List.copyOf(links);
    }

    public record AssistantLink(String label, String url) {
    }
}
