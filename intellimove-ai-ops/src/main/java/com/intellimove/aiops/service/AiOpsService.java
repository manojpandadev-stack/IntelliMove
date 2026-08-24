package com.intellimove.aiops.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellimove.aiops.tools.AiOpsTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * AI Operations Assistant service.
 * Uses Spring AI ChatClient with tool calling to answer admin questions.
 * Falls back to keyword-based tool routing when no LLM is configured.
 */
@Service
@Slf4j
public class AiOpsService {

    private final AiOpsTools aiOpsTools;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String systemPrompt;
    private final int maxConversationHistory;
    private final int timeoutSeconds;
    private final boolean llmEnabled;
    private final ChatClient chatClient;

    private static final String CONVERSATION_KEY = "ai:ops:conversation:";
    private static final Duration CONVERSATION_TTL = Duration.ofHours(1);

    // Keyword-based tool mapping for fallback
    private final Map<String, java.util.function.Supplier<String>> toolRegistry = new ConcurrentHashMap<>();

    public AiOpsService(
            AiOpsTools aiOpsTools,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Qualifier("aiOpsChatClient") ChatClient chatClient,
            @Value("${ai.ops.system-prompt:You are an AI operations assistant.}") String systemPrompt,
            @Value("${ai.ops.max-conversation-history:20}") int maxConversationHistory,
            @Value("${ai.ops.timeout-seconds:30}") int timeoutSeconds,
            @Value("${ai.ops.llm-enabled:false}") boolean llmEnabled) {
        this.aiOpsTools = aiOpsTools;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.chatClient = chatClient;
        this.systemPrompt = systemPrompt;
        this.maxConversationHistory = maxConversationHistory;
        this.timeoutSeconds = timeoutSeconds;
        this.llmEnabled = llmEnabled;

        // Register keyword-based fallback tools
        toolRegistry.put("getRideStatistics", aiOpsTools::getRideStatistics);
        toolRegistry.put("getDriverAvailability", aiOpsTools::getDriverAvailability);
        toolRegistry.put("getCancellationStatistics", aiOpsTools::getCancellationStatistics);
        toolRegistry.put("getDemandStatistics", aiOpsTools::getDemandStatistics);
        toolRegistry.put("getRevenueStatistics", aiOpsTools::getRevenueStatistics);
        toolRegistry.put("getPricingStatistics", aiOpsTools::getPricingStatistics);
        toolRegistry.put("searchIncidents", aiOpsTools::searchIncidents);
    }

    /**
     * Process a user query using LLM with tool calling or keyword-based fallback.
     */
    public AiResponse processQuery(String sessionId, String query) {
        log.info("AI Ops query from session {}: {} (llmEnabled={})", sessionId, query, llmEnabled);
        addToConversation(sessionId, "user", query);

        AiResponse response;
        if (llmEnabled) {
            response = processWithLlm(sessionId, query);
        } else {
            response = processWithKeywordRouting(sessionId, query);
        }

        addToConversation(sessionId, "assistant", response.analysis());
        return response;
    }

    /**
     * Process query using Spring AI ChatClient with tool calling.
     * The LLM decides which tools to call based on the query.
     */
    private AiResponse processWithLlm(String sessionId, String query) {
        try {
            // Run LLM call with a timeout to prevent indefinite blocking
            String response = java.util.concurrent.CompletableFuture.supplyAsync(() ->
                    chatClient.prompt()
                            .system(systemPrompt)
                            .user(query)
                            .tools(aiOpsTools)
                            .advisors(a -> a.param("chat_memory_conversation_id", sessionId))
                            .call()
                            .content()
            ).get(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);

            log.info("LLM response received for session {}: {} chars", sessionId, response.length());
            return new AiResponse(response, Map.of("source", "LLM"), List.of("llm"));

        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("LLM call timed out after {}s for session {}, falling back to keyword routing", timeoutSeconds, sessionId);
            return processWithKeywordFallback(sessionId, query, "LLM timed out after " + timeoutSeconds + "s");
        } catch (Exception e) {
            log.error("LLM call failed for session {}: {}", sessionId, e.getMessage());
            return processWithKeywordFallback(sessionId, query, e.getMessage());
        }
    }

    /**
     * Process query using keyword-based tool routing.
     * Used when LLM is not configured or as fallback.
     */
    private AiResponse processWithKeywordRouting(String sessionId, String query) {
        List<String> relevantTools = determineTools(query);
        Map<String, Object> toolResults = new LinkedHashMap<>();

        for (String toolName : relevantTools) {
            java.util.function.Supplier<String> tool = toolRegistry.get(toolName);
            if (tool != null) {
                try {
                    String result = tool.get();
                    toolResults.put(toolName, objectMapper.readValue(result, new TypeReference<Map<String, Object>>() {}));
                } catch (Exception e) {
                    log.error("Tool {} failed: {}", toolName, e.getMessage());
                    toolResults.put(toolName, Map.of("error", "Tool execution failed: " + e.getMessage()));
                }
            }
        }

        String response = generateAnalysis(query, toolResults);
        return new AiResponse(response, toolResults, relevantTools);
    }

    /**
     * Fallback when LLM fails — use keyword routing with error context.
     */
    private AiResponse processWithKeywordFallback(String sessionId, String query, String error) {
        AiResponse keywordResponse = processWithKeywordRouting(sessionId, query);
        String enhancedAnalysis = "⚠️ LLM unavailable (" + error + "). Results from operational tools:\n\n"
                + keywordResponse.analysis();
        return new AiResponse(enhancedAnalysis, keywordResponse.toolResults(), keywordResponse.toolsUsed());
    }

    /**
     * Determine which tools to invoke based on query keywords.
     */
    private List<String> determineTools(String query) {
        String lower = query.toLowerCase();
        List<String> tools = new ArrayList<>();

        if (lower.contains("ride") || lower.contains("trip") || lower.contains("completed"))
            tools.add("getRideStatistics");
        if (lower.contains("driver") || lower.contains("online") || lower.contains("available"))
            tools.add("getDriverAvailability");
        if (lower.contains("cancel") || lower.contains("cancellation"))
            tools.add("getCancellationStatistics");
        if (lower.contains("demand") || lower.contains("surge") || lower.contains("peak"))
            tools.add("getDemandStatistics");
        if (lower.contains("revenue") || lower.contains("payment") || lower.contains("money") || lower.contains("earning"))
            tools.add("getRevenueStatistics");
        if (lower.contains("pricing") || lower.contains("fare") || lower.contains("price"))
            tools.add("getPricingStatistics");
        if (lower.contains("incident") || lower.contains("alert") || lower.contains("issue") || lower.contains("problem"))
            tools.add("searchIncidents");

        // Default: provide ride and driver stats if no specific tool matched
        if (tools.isEmpty()) {
            tools.add("getRideStatistics");
            tools.add("getDriverAvailability");
        }

        return tools;
    }

    /**
     * Generate analysis response from tool results.
     */
    private String generateAnalysis(String query, Map<String, Object> toolResults) {
        StringBuilder analysis = new StringBuilder();
        analysis.append("## Operations Analysis\n\n");
        analysis.append("**Query:** ").append(query).append("\n\n");

        for (Map.Entry<String, Object> entry : toolResults.entrySet()) {
            analysis.append("### ").append(entry.getKey()).append("\n");
            analysis.append("```\n").append(entry.getValue()).append("\n```\n\n");
        }

        // Generate insights based on available data
        if (toolResults.containsKey("getCancellationStatistics")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cancelData = (Map<String, Object>) toolResults.get("getCancellationStatistics");
            if (cancelData.containsKey("totalCancellationsToday")) {
                analysis.append("### Key Insights\n");
                analysis.append("- Total cancellations today: ").append(cancelData.get("totalCancellationsToday")).append("\n");
                analysis.append("- Cancellation rate trend: ").append(cancelData.get("cancellationRateVsYesterday")).append("\n\n");
                analysis.append("**Recommendation:** Consider increasing driver incentives during peak hours to reduce cancellation rate.\n");
            }
        }

        if (toolResults.containsKey("getRevenueStatistics")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> revData = (Map<String, Object>) toolResults.get("getRevenueStatistics");
            if (revData.containsKey("totalRevenueToday")) {
                analysis.append("\n**Revenue:** $").append(revData.get("totalRevenueToday"))
                        .append(" (").append(revData.get("revenueVsYesterday")).append(" vs yesterday)\n");
            }
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

    public List<Map<String, Object>> getAvailableTools() {
        return List.of(
                Map.of("name", "getRideStatistics", "description", "Get ride statistics and metrics"),
                Map.of("name", "getDriverAvailability", "description", "Get driver availability and online counts"),
                Map.of("name", "getCancellationStatistics", "description", "Get cancellation statistics and reasons"),
                Map.of("name", "getDemandStatistics", "description", "Get current demand levels and surge pricing"),
                Map.of("name", "getRevenueStatistics", "description", "Get revenue and payment statistics"),
                Map.of("name", "getPricingStatistics", "description", "Get pricing and surge multiplier statistics"),
                Map.of("name", "searchIncidents", "description", "Search for operational incidents and alerts"),
                Map.of("name", "searchRides", "description", "Search rides with filters for status and date")
        );
    }

    private void addToConversation(String sessionId, String role, String content) {
        String key = CONVERSATION_KEY + sessionId;
        redisTemplate.opsForList().rightPush(key, role + ":::" + content);
        redisTemplate.expire(key, CONVERSATION_TTL);
    }

    public record AiResponse(String analysis, Map<String, Object> toolResults, List<String> toolsUsed) {}
}
