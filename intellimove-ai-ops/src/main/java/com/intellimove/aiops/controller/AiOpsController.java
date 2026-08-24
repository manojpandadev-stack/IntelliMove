package com.intellimove.aiops.controller;

import com.intellimove.aiops.service.AiOpsService;
import com.intellimove.common.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai/ops")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AiOpsController {

    private final AiOpsService aiOpsService;

    @PostMapping("/query")
    public ResponseEntity<ApiResponse<AiOpsService.AiResponse>> processQuery(
            @RequestHeader(value = "X-Session-Id", defaultValue = "default") String sessionId,
            @Valid @RequestBody QueryRequest request) {
        AiOpsService.AiResponse response = aiOpsService.processQuery(sessionId, request.getQuery());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/tools")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAvailableTools() {
        return ResponseEntity.ok(ApiResponse.success(aiOpsService.getAvailableTools()));
    }

    @GetMapping("/conversation/{sessionId}")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getConversationHistory(
            @PathVariable String sessionId) {
        return ResponseEntity.ok(ApiResponse.success(aiOpsService.getConversationHistory(sessionId)));
    }

    @Data
    public static class QueryRequest {
        @NotBlank(message = "Query is required")
        private String query;
    }
}
