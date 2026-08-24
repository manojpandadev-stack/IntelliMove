# IntelliMove — AI Production Readiness

## Current Status: KEYWORD ROUTING VERIFIED, LLM BLOCKED BY ENVIRONMENT

## Architecture

```
User Query
    ↓
AiOpsService.processQuery()
    ├── LLM Enabled?
    │   ├── YES → Spring AI ChatClient → LLM → Tool Selection → Tools → Response
    │   │         (30s timeout, falls back to keyword on failure)
    │   └── NO  → Keyword-Based Routing → Tools → Analysis Response
    ↓
AiOpsTools (read-only data queries)
    ├── getRideStatistics()
    ├── getDriverAvailability()
    ├── getCancellationStatistics()
    ├── getDemandStatistics()
    ├── getRevenueStatistics()
    ├── getPricingStatistics()
    ├── searchIncidents()
    └── searchRides()
```

## Tool Authorization

| Tool | CUSTOMER | DRIVER | ADMIN |
|------|----------|--------|-------|
| getRideStatistics | ❌ | ❌ | ✅ |
| getDriverAvailability | ❌ | ❌ | ✅ |
| getCancellationStatistics | ❌ | ❌ | ✅ |
| getDemandStatistics | ❌ | ❌ | ✅ |
| getRevenueStatistics | ❌ | ❌ | ✅ |
| getPricingStatistics | ❌ | ❌ | ✅ |
| searchIncidents | ❌ | ❌ | ✅ |
| searchRides | ❌ | ❌ | ✅ |

AI Support (customer-facing) uses separate tools:
- getRide, getPayment, getDriver, getCustomer, getRefundEligibility, createSupportTicket

## LLM Provider Configuration

### Ollama (Local)

```bash
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=qwen3:8b
LLM_ENABLED=true
SPRING_AI_BASE_URL=http://localhost:11434/v1
SPRING_AI_MODEL=qwen3:8b
```

### OpenAI-Compatible

```bash
LLM_ENABLED=true
SPRING_AI_BASE_URL=https://api.openai.com/v1
SPRING_AI_API_KEY=sk-xxx
SPRING_AI_MODEL=gpt-4o
```

### Spring AI Profiles

- `ollama` — Ollama-specific configuration
- `openai` — OpenAI API configuration
- `openai-compatible` — Any OpenAI-compatible API (default)

## Resilience

| Aspect | Status |
|--------|--------|
| LLM timeout | ✅ 30s CompletableFuture |
| LLM failure fallback | ✅ Keyword routing with tool results |
| LLM unavailable | ✅ Keyword routing works without LLM |
| Application start without API key | ✅ LLM disabled by default |
| Tool execution failure | ✅ Logged and reported in response |

## Security

| Aspect | Status |
|--------|--------|
| No arbitrary SQL | ✅ Tools use pre-built queries |
| No arbitrary commands | ✅ No shell execution tools |
| No unrestricted DB access | ✅ Read-only aggregated data |
| No unauthorized data access | ✅ Role-based authorization |
| Customer isolation (Support) | ✅ Identity from JWT, not headers |
| API key not hardcoded | ✅ Environment variables only |

## Limitations

- LLM (Ollama qwen3:8b) requires ~6GB RAM — BLOCKED on current machine
- Keyword routing provides functional AI without LLM
- Tool selection is keyword-based, not semantic
- No streaming responses
- Conversation history stored in Redis with 1h TTL
