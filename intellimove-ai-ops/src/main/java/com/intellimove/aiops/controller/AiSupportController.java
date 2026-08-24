package com.intellimove.aiops.controller;

import com.intellimove.aiops.service.AiSupportService;
import com.intellimove.common.dto.ApiResponse;
import com.intellimove.common.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai/support")
@RequiredArgsConstructor
public class AiSupportController {

    private final AiSupportService aiSupportService;

    /**
     * Process a customer support query.
     * The customerId is extracted from the JWT token, not from user input.
     * This prevents customers from accessing other customers' data.
     */
    @PostMapping("/query")
    public ResponseEntity<ApiResponse<AiSupportService.SupportResponse>> processQuery(
            @RequestHeader(value = "X-Session-Id", defaultValue = "default") String sessionId,
            @Valid @RequestBody QueryRequest request) {
        String customerId = SecurityUtils.getCurrentUserIdString();
        if (customerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication required"));
        }
        AiSupportService.SupportResponse response = aiSupportService.processQuery(
                sessionId, customerId, request.getQuery());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/conversation/{sessionId}")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getConversationHistory(
            @PathVariable String sessionId) {
        return ResponseEntity.ok(ApiResponse.success(
                aiSupportService.getConversationHistory(sessionId)));
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "status", "UP",
                "service", "ai-support",
                "llmEnabled", false
        )));
    }

    @Data
    public static class QueryRequest {
        @NotBlank(message = "Query is required")
        private String query;
    }
}
