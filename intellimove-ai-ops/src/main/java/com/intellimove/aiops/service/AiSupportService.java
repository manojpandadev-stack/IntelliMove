package com.intellimove.aiops.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellimove.aiops.tools.AiSupportTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI Customer Support Agent service.
 * Uses Spring AI ChatClient with tool calling to help customers.
 * Security: Enforces customer isolation — customers can only see their own data.
 */
@Service
@Slf4j
public class AiSupportService {

    private final AiSupportTools aiSupportTools;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String systemPrompt;
    private final boolean llmEnabled;
    private final ChatClient chatClient;

    private static final String CONVERSATION_KEY = "ai:support:conversation:";
    private static final Duration CONVERSATION_TTL = Duration.ofHours(1);

    public AiSupportService(
            AiSupportTools aiSupportTools,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Qualifier("aiSupportChatClient") ChatClient chatClient,
            @Value("${ai.ops.system-prompt:You are IntelliMove customer support.}") String systemPrompt,
            @Value("${ai.ops.llm-enabled:false}") boolean llmEnabled) {
        this.aiSupportTools = aiSupportTools;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.chatClient = chatClient;
        this.systemPrompt = systemPrompt;
        this.llmEnabled = llmEnabled;
    }

    /**
     * Process a customer support query.
     * Enforces customer isolation: customerId is extracted from the JWT, not from the user's input.
     */
    public SupportResponse processQuery(String sessionId, String customerId, String query) {
        log.info("AI Support query from session {} customer {}: {}", sessionId, customerId, query);

        addToConversation(sessionId, "user", query);

        SupportResponse response;
        if (llmEnabled) {
            response = processWithLlm(sessionId, customerId, query);
        } else {
            response = processWithKeywordRouting(sessionId, customerId, query);
        }

        addToConversation(sessionId, "assistant", response.analysis());
        return response;
    }

    /**
     * Process query using Spring AI ChatClient with tool calling.
     */
    private SupportResponse processWithLlm(String sessionId, String customerId, String query) {
        try {
            // System prompt includes security instructions
            String secureSystemPrompt = systemPrompt + "\n\n" +
                    "IMPORTANT SECURITY RULES:\n" +
                    "- You are helping customer: " + customerId + "\n" +
                    "- You MUST pass customerId='" + customerId + "' to all tool calls\n" +
                    "- Never retrieve data for other customers\n" +
                    "- Never process refunds directly — only assess eligibility\n" +
                    "- Payment/financial actions require backend authorization\n";

            String response = chatClient.prompt()
                    .system(secureSystemPrompt)
                    .user(query)
                    .tools(aiSupportTools)
                    .advisors(a -> a.param("chat_memory_conversation_id", "support-" + sessionId))
                    .call()
                    .content();

            return new SupportResponse(response, Map.of("source", "LLM"), List.of("llm"), customerId);

        } catch (Exception e) {
            log.error("LLM call failed for support session {}: {}", sessionId, e.getMessage());
            return processWithKeywordFallback(sessionId, customerId, query, e.getMessage());
        }
    }

    /**
     * Process query using keyword-based tool routing.
     */
    private SupportResponse processWithKeywordRouting(String sessionId, String customerId, String query) {
        String lower = query.toLowerCase();
        Map<String, Object> toolResults = new LinkedHashMap<>();
        List<String> toolsUsed = new ArrayList<>();

        if (lower.contains("ride") || lower.contains("trip") || lower.contains("where")) {
            String rideResult = aiSupportTools.getRide("latest", customerId);
            toolResults.put("getRide", parseJson(rideResult));
            toolsUsed.add("getRide");
        }

        if (lower.contains("payment") || lower.contains("charge") || lower.contains("bill")) {
            String payResult = aiSupportTools.getPayment("latest", customerId);
            toolResults.put("getPayment", parseJson(payResult));
            toolsUsed.add("getPayment");
        }

        if (lower.contains("driver")) {
            String driverResult = aiSupportTools.getDriver("latest");
            toolResults.put("getDriver", parseJson(driverResult));
            toolsUsed.add("getDriver");
        }

        if (lower.contains("refund") || lower.contains("money back")) {
            String refundResult = aiSupportTools.getRefundEligibility("latest", "Customer request: " + query);
            toolResults.put("getRefundEligibility", parseJson(refundResult));
            toolsUsed.add("getRefundEligibility");
        }

        if (lower.contains("ticket") || lower.contains("complaint") || lower.contains("issue")) {
            String ticketResult = aiSupportTools.createSupportTicket(customerId, "latest", query, "MEDIUM");
            toolResults.put("createSupportTicket", parseJson(ticketResult));
            toolsUsed.add("createSupportTicket");
        }

        // Default: show ride info
        if (toolsUsed.isEmpty()) {
            String rideResult = aiSupportTools.getRide("latest", customerId);
            toolResults.put("getRide", parseJson(rideResult));
            toolsUsed.add("getRide");
        }

        String analysis = generateSupportAnalysis(query, toolResults, customerId);
        return new SupportResponse(analysis, toolResults, toolsUsed, customerId);
    }

    private SupportResponse processWithKeywordFallback(String sessionId, String customerId, String query, String error) {
        SupportResponse fallback = processWithKeywordRouting(sessionId, customerId, query);
        String enhanced = "⚠️ AI assistant temporarily unavailable (" + error + "). Here's what I found:\n\n"
                + fallback.analysis();
        return new SupportResponse(enhanced, fallback.toolResults(), fallback.toolsUsed(), customerId);
    }

    private String generateSupportAnalysis(String query, Map<String, Object> toolResults, String customerId) {
        StringBuilder analysis = new StringBuilder();
        analysis.append("## Support Response\n\n");

        for (Map.Entry<String, Object> entry : toolResults.entrySet()) {
            analysis.append("### ").append(entry.getKey()).append("\n");
            analysis.append("```\n").append(entry.getValue()).append("\n```\n\n");
        }

        if (toolResults.containsKey("getRide")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> ride = (Map<String, Object>) toolResults.get("getRide");
            analysis.append("Your ride ").append(ride.getOrDefault("rideId", ""))
                    .append(" is currently: **").append(ride.getOrDefault("status", "unknown")).append("**\n\n");
        }

        if (toolResults.containsKey("getRefundEligibility")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> refund = (Map<String, Object>) toolResults.get("getRefundEligibility");
            Object eligible = refund.getOrDefault("eligible", false);
            analysis.append("Refund eligibility: **")
                    .append(Boolean.TRUE.equals(eligible) ? "✅ Eligible" : "❌ Not eligible").append("**\n");
            analysis.append("Amount: $").append(refund.getOrDefault("refundAmount", 0)).append("\n\n");
        }

        return analysis.toString();
    }

    public List<Map<String, String>> getConversationHistory(String sessionId) {
        String key = CONVERSATION_KEY + sessionId;
        List<String> history = redisTemplate.opsForList().range(key, 0, -1);
        if (history == null) return List.of();

        List<Map<String, String>> messages = new ArrayList<>();
        for (String entry : history) {
            String[] parts = entry.split(":::", 2);
            if (parts.length == 2) {
                messages.add(Map.of("role", parts[0], "content", parts[1]));
            }
        }
        return messages;
    }

    private void addToConversation(String sessionId, String role, String content) {
        String key = CONVERSATION_KEY + sessionId;
        redisTemplate.opsForList().rightPush(key, role + ":::" + content);
        redisTemplate.expire(key, CONVERSATION_TTL);
    }

    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of("raw", json);
        }
    }

    public record SupportResponse(String analysis, Map<String, Object> toolResults, List<String> toolsUsed, String customerId) {}
}
