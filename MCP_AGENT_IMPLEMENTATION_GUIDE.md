# 🛠️ MCP & Agent Integration Implementation Guide

**Quick Reference for Developers**

---

## Quick Start: Set Up MCP Server Locally

### 1. Create MCP Gradle Module

```bash
mkdir -p backend/mcp-server
cd backend/mcp-server

# Create build.gradle.kts
cat > build.gradle.kts << 'EOF'
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.google.cloud.tools.jib") version "3.4.0"  // For Docker builds
}

dependencies {
    // Core
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // HTTP server
    implementation("io.ktor:ktor-server-core:2.3.0")
    implementation("io.ktor:ktor-server-cio:2.3.0")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.0")
    
    // HTTP client (for calling Supabase)
    implementation("io.ktor:ktor-client-core:2.3.0")
    implementation("io.ktor:ktor-client-cio:2.3.0")
    
    // Logging
    implementation("io.github.oshai:kotlin-logging:5.0.0")
    implementation("org.slf4j:slf4j-api:2.0.7")
    implementation("ch.qos.logback:logback-classic:1.4.7")
}

jib {
    to {
        image = "gcr.io/sanibonani-prod/mcp-server:latest"
    }
    container {
        ports = listOf("8080")
        environment = mapOf(
            "SUPABASE_URL" to "placeholder",
            "SUPABASE_KEY" to "placeholder"
        )
    }
}
EOF
```

### 2. Create MCP Server Application

```kotlin
// backend/mcp-server/src/main/kotlin/MCPApplication.kt
package com.sanibonani.mcp

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.cio.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

fun main() {
    embeddedServer(CIO, port = 8080) {
        configureRouting()
        configureContentNegotiation()
    }.start(wait = true)
}

fun Application.configureContentNegotiation() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        })
    }
}

fun Application.configureRouting() {
    val mcpServer = MCPServer()
    
    routing {
        // Health check
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }
        
        // MCP resources
        get("/mcp/resources") {
            call.respond(mapOf("resources" to mcpServer.getResources()))
        }
        
        // MCP tools
        get("/mcp/tools") {
            call.respond(mapOf("tools" to mcpServer.getTools()))
        }
        
        // Execute tool
        post("/mcp/execute") {
            val request = call.receive<MCPRequest>()
            try {
                val result = mcpServer.executeTool(request.toolName, request.params)
                call.respond(MCPResponse(success = true, result = result, error = null))
            } catch (e: Exception) {
                call.respond(MCPResponse(success = false, result = null, error = e.message ?: "Unknown error"))
            }
        }
    }
}
```

### 3. Build & Run Locally

```bash
# Build the MCP server
./gradlew :mcp-server:build

# Run locally
./gradlew :mcp-server:run

# Test endpoints
curl http://localhost:8080/health
curl http://localhost:8080/mcp/tools
```

### 4. Deploy to Google Cloud Run

```bash
# Build Docker image
./gradlew :mcp-server:jib

# Or build manually
docker build -f backend/mcp-server/Dockerfile -t gcr.io/sanibonani-prod/mcp-server .

# Push to GCR
docker push gcr.io/sanibonani-prod/mcp-server

# Deploy to Cloud Run
gcloud run deploy mcp-server \
  --image gcr.io/sanibonani-prod/mcp-server \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --set-env-vars SUPABASE_URL=$SUPABASE_URL,SUPABASE_KEY=$SUPABASE_KEY
```

---

## Quick Start: Set Up AI Agent (Python)

### 1. Install Dependencies

```bash
pip install anthropic httpx asyncio python-dotenv
```

### 2. Create Agent File

```python
# backend/agents/sanibonani_agent.py
import anthropic
import json
import httpx
import asyncio
import os
from dotenv import load_dotenv

load_dotenv()

class SanibonaniAgent:
    def __init__(self):
        self.client = anthropic.Anthropic(api_key=os.getenv("ANTHROPIC_API_KEY"))
        self.mcp_base_url = os.getenv("MCP_SERVER_URL", "http://localhost:8080")
        self.tools = []
        self.system_prompt = ""
    
    async def initialize(self):
        """Fetch tools from MCP server"""
        async with httpx.AsyncClient() as client:
            response = await client.get(f"{self.mcp_base_url}/mcp/tools")
            tools_data = response.json()
            self.tools = tools_data.get("tools", [])
    
    async def analyze_group(self, group_id: str) -> str:
        """
        Analyze a group using the agent.
        Returns a human-friendly analysis.
        """
        
        self.system_prompt = """
        You are a financial advisor for South African savings groups.
        Analyze group health data and provide actionable recommendations.
        Keep responses concise and friendly.
        """
        
        query = f"Please analyze group {group_id} and tell me if it's financially healthy."
        
        messages = [{"role": "user", "content": query}]
        
        # Agentic loop
        for iteration in range(5):
            response = self.client.messages.create(
                model="claude-3-5-sonnet-20241022",
                max_tokens=1024,
                system=self.system_prompt,
                tools=self.tools,
                messages=messages
            )
            
            # If Claude stopped (no more tools)
            if response.stop_reason == "end_turn":
                return "".join(
                    b.text for b in response.content if hasattr(b, "text")
                )
            
            # If Claude wants to use a tool
            if response.stop_reason == "tool_use":
                tool_blocks = [b for b in response.content if b.type == "tool_use"]
                
                for tool_block in tool_blocks:
                    # Call MCP server
                    async with httpx.AsyncClient() as client:
                        response_data = await client.post(
                            f"{self.mcp_base_url}/mcp/execute",
                            json={
                                "toolName": tool_block.name,
                                "params": tool_block.input
                            }
                        )
                        tool_result = response_data.json()
                    
                    # Add to message history
                    messages.append({"role": "assistant", "content": response.content})
                    messages.append({
                        "role": "user",
                        "content": [{
                            "type": "tool_result",
                            "tool_use_id": tool_block.id,
                            "content": json.dumps(tool_result)
                        }]
                    })
                    break
        
        return "Max iterations reached"


# Example usage
async def main():
    agent = SanibonaniAgent()
    await agent.initialize()
    
    # Analyze a group
    result = await agent.analyze_group("group_123")
    print(f"Agent Analysis:\n{result}")


if __name__ == "__main__":
    asyncio.run(main())
```

### 3. Run the Agent

```bash
# Set environment
export ANTHROPIC_API_KEY="your_key_here"
export MCP_SERVER_URL="http://localhost:8080"

# Run
python backend/agents/sanibonani_agent.py
```

---

## Integration with Android App

### 1. Add Network Call

```kotlin
// app/src/main/java/com/sanibonani/save/data/remote/AIServiceClient.kt
package com.sanibonani.save.data.remote

import com.sanibonani.save.domain.model.AIResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable
data class AgentQueryRequest(
    val query: String,
    val agentType: String
)

@Serializable
data class AgentQueryResponse(
    val agent: String,
    val result: String,
    val timestamp: String
)

class AIServiceClient @Inject constructor(
    private val httpClient: HttpClient
) {
    
    suspend fun queryAgent(
        query: String,
        agentType: String
    ): Result<AIResponse> = runCatching {
        val response = httpClient.post {
            url("https://api.sanibonani.co/agents/query")
            contentType(ContentType.Application.Json)
            setBody(AgentQueryRequest(query, agentType))
        }
        
        val data = response.body<AgentQueryResponse>()
        AIResponse(
            agent = data.agent,
            result = data.result,
            timestamp = data.timestamp
        )
    }
}
```

### 2. Add ViewModel Method

```kotlin
// In AIAssistantViewModel
fun queryAgent(query: String) {
    viewModelScope.launch {
        _state.update { it.copy(isLoading = true) }
        
        aiServiceClient.queryAgent(query, selectedAgent.value)
            .onSuccess { response ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        responses = it.responses + response
                    )
                }
            }
            .onFailure { e ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.toUserMessage()
                    )
                }
            }
    }
}
```

---

## Testing MCP & Agents

### Unit Test for MCP Server

```kotlin
// mcp-server/src/test/kotlin/MCPServerTest.kt
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

class MCPServerTest {
    
    private val mcpServer = MCPServer()
    
    @Test
    fun `getResources returns expected URIs`() {
        val resources = mcpServer.getResources()
        
        assertEquals(4, resources.size)
        assert(resources.any { it.uri == "sanibonani://groups" })
        assert(resources.any { it.uri == "sanibonani://payments" })
    }
    
    @Test
    fun `getTools returns core tools`() {
        val tools = mcpServer.getTools()
        
        val toolNames = tools.map { it.name }
        assert("create_group" in toolNames)
        assert("analyze_group_health" in toolNames)
        assert("process_payment" in toolNames)
    }
    
    @Test
    fun `executeTool handles create_group`() = runBlocking {
        val params = mapOf(
            "name" to "Test Group",
            "type" to "STOKVEL",
            "adminUserId" to "admin_123",
            "members" to emptyList<String>()
        )
        
        val result = mcpServer.executeTool("create_group", params)
        assert(result.contains("id"))
    }
}
```

### Integration Test for Agent

```python
# backend/tests/test_agent.py
import pytest
import asyncio
from agents.sanibonani_agent import SanibonaniAgent

@pytest.mark.asyncio
async def test_agent_initialization():
    """Test agent can fetch tools from MCP server"""
    agent = SanibonaniAgent()
    await agent.initialize()
    
    assert len(agent.tools) > 0
    tool_names = [t["name"] for t in agent.tools]
    assert "analyze_group_health" in tool_names

@pytest.mark.asyncio
async def test_agent_analysis(monkeypatch):
    """Test agent can analyze a group"""
    # Mock the MCP server response
    async def mock_post(*args, **kwargs):
        class MockResponse:
            async def json(self):
                return {
                    "success": True,
                    "result": '{"health_score": 85, "status": "excellent"}'
                }
        return MockResponse()
    
    agent = SanibonaniAgent()
    await agent.initialize()
    
    # In real test, would call actual MCP server
    result = await agent.analyze_group("test_group_id")
    assert "health" in result.lower() or "excellent" in result.lower()
```

---

## Troubleshooting

### Issue: MCP Server Not Responding

```bash
# Check if running
curl http://localhost:8080/health

# View logs
docker logs mcp-server-container

# Restart
docker restart mcp-server-container
```

### Issue: Agent Hitting Rate Limits

```python
# Add exponential backoff
import time
from functools import wraps

def retry_with_backoff(max_retries=3):
    def decorator(func):
        def wrapper(*args, **kwargs):
            for attempt in range(max_retries):
                try:
                    return func(*args, **kwargs)
                except RateLimitError:
                    if attempt < max_retries - 1:
                        wait_time = 2 ** attempt
                        print(f"Rate limited. Waiting {wait_time}s...")
                        time.sleep(wait_time)
                    else:
                        raise
        return wrapper
    return decorator
```

### Issue: Kotlin Serialization Not Working

```kotlin
// Ensure all data classes have @Serializable
@Serializable
data class Group(
    val id: String,
    val name: String,
    val type: String
)

// For sealed classes, use @Serializable on each subclass
@Serializable
sealed class AIResponse {
    @Serializable
    data class Success(val result: String) : AIResponse()
    
    @Serializable
    data class Error(val error: String) : AIResponse()
}
```

---

## Performance Optimization Tips

### 1. Cache MCP Tool Definitions

```python
from functools import lru_cache

@lru_cache(maxsize=1)
async def get_mcp_tools():
    """Cache tools for 1 hour"""
    # fetch from MCP server
    return tools
```

### 2. Batch Agent Queries

```python
async def batch_analyze_groups(group_ids: List[str]):
    """Analyze multiple groups in parallel"""
    tasks = [agent.analyze_group(gid) for gid in group_ids]
    results = await asyncio.gather(*tasks)
    return results
```

### 3. Use Streaming for Long Responses

```kotlin
// In Ktor, stream agent responses
post("/agents/query") {
    val query = call.receive<QueryRequest>()
    
    call.response.header(HttpHeaders.ContentType, "text/event-stream")
    call.respondTextWriter {
        val agent = getAgent(query.agentType)
        agent.streamAnalysis(query.input).collect { chunk ->
            write("data: $chunk\n\n")
            flush()
        }
    }
}
```

---

## Monitoring & Debugging

### Enable Debug Logging

```kotlin
// In SanibonaniApp.kt
if (BuildConfig.DEBUG) {
    val logging = HttpClientConfig<CIOEngineConfig>()
    logging.install(Logging) {
        logger = Logger.DEFAULT
        level = LogLevel.ALL
    }
}
```

### Set Up Alerts

```python
# backend/monitoring/alerts.py
def check_mcp_health():
    """Check MCP server health every 5 minutes"""
    schedule.every(5).minutes.do(lambda: {
        try:
            resp = httpx.get(f"{MCP_URL}/health", timeout=5)
            assert resp.status_code == 200
        except Exception as e:
            send_slack_alert(f"MCP server down: {e}")
    })
```

---

## Next Steps

1. **Week 1**: Deploy MCP server to Cloud Run
2. **Week 2**: Integrate with Android app
3. **Week 3**: Deploy first agent (Insights)
4. **Week 4**: Add scheduled jobs
5. **Week 5**: Launch to production

---

**Questions?** See DEPLOYMENT_MCP_AGENT_ROADMAP.md for full details.

