# 🚀 SanibonaniSave Deployment Roadmap: MCP Server & Agent Integration

**Date**: May 12, 2026  
**Version**: 1.0  
**Status**: Pre-Deployment Planning  

---

## 📋 Executive Summary

This document outlines the **step-by-step roadmap** to:
1. **Deploy SanibonaniSave** as a production-ready Android application
2. **Integrate MCP (Model Context Protocol) Server** for AI/LLM interoperability
3. **Enable AI Agent orchestration** for automated group management, insights, and compliance
4. **Establish monitoring, scalability, and continuous delivery pipelines**

The roadmap is organized into **5 phases** spanning **12-16 weeks** to production.

---

## 🎯 Phase 1: Core Deployment Preparation (Weeks 1-2)

### 1.1 Production Build & Release Configuration

**Status**: 80% Ready | **Action**: Complete release signing setup

#### Deliverables:
- [ ] Create/import release keystore for Google Play signing
  ```bash
  keytool -genkey -v -keystore release.keystore -keyalg RSA -keysize 2048 -validity 10000 -alias sanibonani_release
  ```
- [ ] Configure `build.gradle.kts` signing config:
  ```kotlin
  android {
      signingConfigs {
          release {
              storeFile = file("release.keystore")
              storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "placeholder"
              keyAlias = "sanibonani_release"
              keyPassword = System.getenv("KEY_PASSWORD") ?: "placeholder"
          }
      }
      buildTypes {
          release {
              signingConfig = signingConfigs.release
              isMinifyEnabled = true
              proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
          }
      }
  }
  ```

- [ ] Set up build variants for staging/production environments
  ```kotlin
  flavorDimensions.add("environment")
  productFlavors {
      create("staging") { dimension = "environment" }
      create("production") { dimension = "environment" }
  }
  ```

- [ ] Verify R8/ProGuard rules for Kotlin libraries (Hilt, Supabase, etc.)
  - Ensure `@Keep` annotations are present on Hilt DI modules
  - Test minified build locally: `./gradlew assembleRelease`

#### Success Criteria:
- ✅ Release APK/AAB builds successfully
- ✅ APK verifiable via `bundletool verify`
- ✅ No ProGuard warnings for core libraries

---

### 1.2 Secrets & Environment Management

**Status**: 90% Ready | **Action**: Centralize secrets in CI/CD pipeline

#### Deliverables:
- [ ] Migrate from `local.properties` to GitHub Secrets / GitLab CI Variables
  ```yaml
  # .github/workflows/deploy.yml (example)
  env:
    SUPABASE_URL: ${{ secrets.SUPABASE_URL }}
    SUPABASE_ANON_KEY: ${{ secrets.SUPABASE_ANON_KEY }}
    YOCO_PUBLIC_KEY: ${{ secrets.YOCO_PUBLIC_KEY }}
    WHATSAPP_TOKEN: ${{ secrets.WHATSAPP_TOKEN }}
    FIREBASE_CONFIG: ${{ secrets.FIREBASE_CONFIG_JSON }}
  ```

- [ ] Implement `.properties` file generation in CI:
  ```bash
  echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
  echo "SUPABASE_URL=$SUPABASE_URL" >> local.properties
  # ... append other secrets
  ```

- [ ] Create encrypted backup of keystore (store safely in vault)
- [ ] Document secret rotation policy (every 90 days for API keys)

#### Success Criteria:
- ✅ No hardcoded secrets in version control
- ✅ GitHub/GitLab Secrets configured
- ✅ CI pipeline can build without local setup

---

### 1.3 Performance & Stability Testing

**Status**: 70% Ready | **Action**: Establish baseline metrics

#### Deliverables:
- [ ] Run **release build** unit tests:
  ```bash
  ./gradlew :app:testReleaseUnitTest
  ./gradlew :data:testReleaseUnitTest
  ./gradlew :domain:testReleaseUnitTest
  ```

- [ ] Stabilize known flaky tests:
  - `ActuarialRepositoryTest.computeActuarialScalars`
  - `GroupViewModelTest.finalizeRegistrationAfterPayment`
  
  **Recommendation**: Add `@Retry(count = 3)` for timing-sensitive tests, or use explicit `Thread.sleep()` for test-specific waits.

- [ ] Measure APK size:
  ```bash
  ./gradlew bundleRelease
  bundletool build-apks --bundle=app/release/app.aab --output=test.apks
  ```
  **Target**: < 60 MB (uncompressed AAB)

- [ ] Set up Crashlytics in Firebase:
  ```kotlin
  // In SanibonaniApp.kt
  Firebase.crashlytics.setCrashlyticsCollectionEnabled(BuildConfig.RELEASE_BUILD)
  ```

#### Success Criteria:
- ✅ All tests pass in release build (3 consecutive runs)
- ✅ APK < 60 MB
- ✅ Firebase Crashlytics dashboard active

---

## 📡 Phase 2: Backend & API Hardening (Weeks 3-4)

### 2.1 Supabase Production Setup

**Status**: 85% Ready | **Action**: Migrate to production project

#### Deliverables:
- [ ] Create production Supabase project (separate from dev)
  - Enable database backups (daily)
  - Enable point-in-time recovery (14-day window)
  - Configure read replicas for high availability

- [ ] Update RLS (Row-Level Security) policies:
  ```sql
  -- Example: Members can only see their own group data
  CREATE POLICY "members_read_own_group" ON groups
    FOR SELECT USING (
      auth.uid() IN (SELECT user_id FROM group_members WHERE group_id = groups.id)
    );
  ```

- [ ] Set up API rate limiting:
  ```sql
  -- Supabase PostgREST config
  -- Add rate limiting headers: x-ratelimit-limit, x-ratelimit-remaining
  ```

- [ ] Implement connection pooling for database:
  - PgBouncer configured in Supabase settings
  - Connection pool size: 20-30 for initial capacity

- [ ] Enable audit logging:
  ```sql
  CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    table_name TEXT,
    operation TEXT, -- INSERT, UPDATE, DELETE
    old_values JSONB,
    new_values JSONB,
    changed_at TIMESTAMP DEFAULT NOW(),
    changed_by UUID REFERENCES auth.users(id)
  );
  ```

#### Success Criteria:
- ✅ Production Supabase project created & configured
- ✅ All migrations tested in production (schema matches dev)
- ✅ Backups enabled & verified
- ✅ RLS policies tested with multiple user roles

---

### 2.2 Edge Functions & Webhooks (Supabase Functions)

**Status**: 70% Ready | **Action**: Deploy critical functions

#### Deliverables:
- [ ] Deploy WhatsApp notification function:
  ```typescript
  // supabase/functions/send-whatsapp/index.ts
  import { serve } from "https://deno.land/std@0.208.0/http/server.ts"
  import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

  serve(async (req) => {
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL"),
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")
    )
    
    const { phoneNumber, message } = await req.json()
    
    // Call Meta WhatsApp Cloud API
    const response = await fetch(
      `https://graph.instagram.com/v18.0/${Deno.env.get("WHATSAPP_PHONE_ID")}/messages`,
      {
        method: "POST",
        headers: { Authorization: `Bearer ${Deno.env.get("WHATSAPP_TOKEN")}` },
        body: JSON.stringify({
          messaging_product: "whatsapp",
          to: phoneNumber,
          type: "text",
          text: { body: message }
        })
      }
    )
    
    return new Response(JSON.stringify(await response.json()))
  })
  ```

- [ ] Deploy email trigger function for group alerts
- [ ] Deploy fee enforcement cron job (daily at midnight UTC)
- [ ] Set up webhook for Yoco payment callbacks:
  ```python
  # supabase/functions/yoco-webhook/index.py
  @app.route('/yoco-webhook', methods=['POST'])
  def handle_yoco_webhook():
      payload = request.json
      if payload['status'] == 'completed':
          # Update payment record in Supabase
          supabase.table('payments').update(...).execute()
      return {"status": "ok"}
  ```

#### Success Criteria:
- ✅ All edge functions deployed to Supabase
- ✅ Webhook endpoints tested with mock payloads
- ✅ Cron jobs validated (dry-run)

---

### 2.3 Database Optimization & Indexing

**Status**: 65% Ready | **Action**: Add missing indexes

#### Deliverables:
- [ ] Create performance indexes:
  ```sql
  -- Frequently queried columns
  CREATE INDEX idx_group_members_user_id ON group_members(user_id);
  CREATE INDEX idx_members_group_id ON members(group_id);
  CREATE INDEX idx_payments_status_created ON payments(status, created_at DESC);
  CREATE INDEX idx_fees_due_date ON platform_fees(due_date) WHERE status != 'paid';
  CREATE INDEX idx_loans_approval_status ON loans(approval_status);
  ```

- [ ] Vacuum and analyze tables:
  ```sql
  VACUUM ANALYZE; -- Reclaim space and update statistics
  ```

- [ ] Monitor slow queries:
  - Enable Supabase query performance dashboard
  - Set threshold for slow queries (> 500ms)

#### Success Criteria:
- ✅ Indexes created & verified
- ✅ Query performance dashboard shows improvement (< 100ms for common queries)

---

## 🤖 Phase 3: MCP Server Integration (Weeks 5-7)

### 3.1 MCP (Model Context Protocol) Server Setup

**Status**: 0% Ready | **Action**: Create MCP protocol layer

#### What is MCP?
**MCP (Model Context Protocol)** is a standardized way for LLMs and AI agents to interact with external systems. It allows:
- **Unified interface** for multiple data sources
- **Tool definitions** (functions the AI can call)
- **Resource management** (data the AI can read)
- **Structured logging** and error handling

#### Deliverables:

**3.1.1 Create Kotlin MCP Server Library**

```kotlin
// data/src/main/java/com/sanibonani/save/data/mcp/MCPServer.kt
package com.sanibonani.save.data.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * MCP Server implementation for SanibonaniSave.
 * Exposes repository operations as standardized MCP resources and tools.
 */
class MCPServer(
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository,
    private val actuarialRepository: ActuarialRepository,
    private val paymentRepository: PaymentRepository
) {
    
    /**
     * MCP Resources: Data structures the AI can read and query
     */
    fun getResources(): List<MCPResource> = listOf(
        MCPResource(
            uri = "sanibonani://groups",
            name = "Groups",
            description = "All savings groups with metadata"
        ),
        MCPResource(
            uri = "sanibonani://groups/{id}/members",
            name = "Group Members",
            description = "Members of a specific group"
        ),
        MCPResource(
            uri = "sanibonani://groups/{id}/viability",
            name = "Group Viability",
            description = "Actuarial analysis and health metrics"
        ),
        MCPResource(
            uri = "sanibonani://payments",
            name = "Payment History",
            description = "All payments and transactions"
        )
    )
    
    /**
     * MCP Tools: Functions the AI can invoke
     */
    fun getTools(): List<MCPTool> = listOf(
        MCPTool(
            name = "create_group",
            description = "Register a new savings group",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "name" to mapOf("type" to "string"),
                    "type" to mapOf("type" to "string", "enum" to listOf("ROSCA", "STOKVEL", "BURIAL_SOCIETY")),
                    "adminUserId" to mapOf("type" to "string"),
                    "members" to mapOf("type" to "array", "items" to mapOf("type" to "string"))
                )
            )
        ),
        MCPTool(
            name = "analyze_group_health",
            description = "Get detailed actuarial analysis for a group",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "groupId" to mapOf("type" to "string")
                ),
                "required" to listOf("groupId")
            )
        ),
        MCPTool(
            name = "process_payment",
            description = "Record and process a member payment",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "memberId" to mapOf("type" to "string"),
                    "groupId" to mapOf("type" to "string"),
                    "amount" to mapOf("type" to "number"),
                    "method" to mapOf("type" to "string", "enum" to listOf("CARD", "BANK_TRANSFER", "CASH"))
                )
            )
        ),
        MCPTool(
            name = "approve_loan",
            description = "Review and approve a member loan request",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "loanId" to mapOf("type" to "string"),
                    "decision" to mapOf("type" to "string", "enum" to listOf("APPROVED", "REJECTED")),
                    "notes" to mapOf("type" to "string")
                )
            )
        )
    )
    
    /**
     * Execute a tool with provided parameters
     */
    suspend fun executeTool(toolName: String, params: Map<String, Any>): String {
        return when (toolName) {
            "create_group" -> handleCreateGroup(params)
            "analyze_group_health" -> handleAnalyzeHealth(params)
            "process_payment" -> handleProcessPayment(params)
            "approve_loan" -> handleApproveLoan(params)
            else -> throw IllegalArgumentException("Unknown tool: $toolName")
        }
    }
    
    private suspend fun handleCreateGroup(params: Map<String, Any>): String {
        val groupName = params["name"] as String
        val groupType = params["type"] as String
        val adminUserId = params["adminUserId"] as String
        
        val newGroup = Group(
            id = UUID.randomUUID().toString(),
            name = groupName,
            type = GroupType.valueOf(groupType),
            adminUserId = adminUserId,
            createdAt = Instant.now().toString()
        )
        
        return groupRepository.createGroup(newGroup)
            .fold(
                onSuccess = { Json.encodeToString(it) },
                onFailure = { mapOf("error" to it.message).toString() }
            )
    }
    
    private suspend fun handleAnalyzeHealth(params: Map<String, Any>): String {
        val groupId = params["groupId"] as String
        
        return actuarialRepository.analyzeGroupHealth(groupId)
            .fold(
                onSuccess = { Json.encodeToString(it) },
                onFailure = { mapOf("error" to it.message).toString() }
            )
    }
    
    private suspend fun handleProcessPayment(params: Map<String, Any>): String {
        val memberId = params["memberId"] as String
        val groupId = params["groupId"] as String
        val amount = (params["amount"] as Number).toDouble()
        val method = params["method"] as String
        
        val payment = Payment(
            id = UUID.randomUUID().toString(),
            memberId = memberId,
            groupId = groupId,
            amount = amount,
            method = method,
            status = "pending",
            createdAt = Instant.now().toString()
        )
        
        return paymentRepository.recordPayment(payment)
            .fold(
                onSuccess = { Json.encodeToString(it) },
                onFailure = { mapOf("error" to it.message).toString() }
            )
    }
    
    private suspend fun handleApproveLoan(params: Map<String, Any>): String {
        val loanId = params["loanId"] as String
        val decision = params["decision"] as String
        val notes = params["notes"] as String?
        
        return groupRepository.updateLoanStatus(loanId, decision, notes)
            .fold(
                onSuccess = { mapOf("status" to "approved", "loanId" to loanId).toString() },
                onFailure = { mapOf("error" to it.message).toString() }
            )
    }
}

@Serializable
data class MCPResource(
    val uri: String,
    val name: String,
    val description: String
)

@Serializable
data class MCPTool(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any>
)
```

**3.1.2 REST API for MCP Server**

```kotlin
// app/src/main/java/com/sanibonani/save/api/MCPController.kt
package com.sanibonani.save.api

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class MCPRequest(
    val toolName: String,
    val params: Map<String, Any>
)

@Serializable
data class MCPResponse(
    val success: Boolean,
    val result: String?,
    val error: String?
)

fun Application.configureMCPRouting(mcpServer: MCPServer) {
    routing {
        get("/mcp/resources") {
            call.respond(mapOf("resources" to mcpServer.getResources()))
        }
        
        get("/mcp/tools") {
            call.respond(mapOf("tools" to mcpServer.getTools()))
        }
        
        post("/mcp/execute") {
            val request = call.receive<MCPRequest>()
            try {
                val result = mcpServer.executeTool(request.toolName, request.params)
                call.respond(MCPResponse(success = true, result = result, error = null))
            } catch (e: Exception) {
                call.respond(MCPResponse(success = false, result = null, error = e.message))
            }
        }
    }
}
```

#### Success Criteria:
- ✅ MCP Server class created with resources & tools
- ✅ REST endpoints available at `/mcp/resources`, `/mcp/tools`, `/mcp/execute`
- ✅ All 4 core tools (create_group, analyze_health, process_payment, approve_loan) functional
- ✅ Tool inputs validated against JSON schema

---

### 3.2 MCP Client Integration (AI/LLM Connectivity)

**Status**: 0% Ready | **Action**: Create client wrapper for LLM integration

#### Deliverables:

```python
# backend/mcp_client.py (Python integration for Claude, GPT, etc.)
import json
import httpx
from typing import Any, Dict, List

class SanibonaniMCPClient:
    """
    MCP Client for LLM integration.
    Allows Claude, GPT-4, or other LLMs to interact with SanibonaniSave.
    """
    
    def __init__(self, base_url: str = "https://api.sanibonani.co"):
        self.base_url = base_url
        self.client = httpx.AsyncClient()
        self._resource_cache: Dict[str, Any] = {}
        self._tool_cache: List[Dict[str, Any]] = []
    
    async def fetch_resources(self) -> Dict[str, Any]:
        """Fetch available MCP resources"""
        response = await self.client.get(f"{self.base_url}/mcp/resources")
        self._resource_cache = response.json()
        return self._resource_cache
    
    async def fetch_tools(self) -> List[Dict[str, Any]]:
        """Fetch available MCP tools"""
        response = await self.client.get(f"{self.base_url}/mcp/tools")
        self._tool_cache = response.json()["tools"]
        return self._tool_cache
    
    async def call_tool(self, tool_name: str, **kwargs) -> Dict[str, Any]:
        """
        Call an MCP tool.
        
        Example:
            result = await client.call_tool(
                "create_group",
                name="My Stokvel",
                type="STOKVEL",
                adminUserId="user_123",
                members=["user_456", "user_789"]
            )
        """
        payload = {
            "toolName": tool_name,
            "params": kwargs
        }
        response = await self.client.post(
            f"{self.base_url}/mcp/execute",
            json=payload
        )
        return response.json()
    
    def get_system_prompt(self) -> str:
        """
        Generate a system prompt for Claude/GPT describing available tools.
        Use this to set up the LLM's context.
        """
        tools_desc = "\n".join([
            f"- {tool['name']}: {tool['description']}"
            for tool in self._tool_cache
        ])
        
        return f"""
You are SanibonaniAssistant, an AI agent that helps manage South African savings groups (Stokvels, ROSCAs, Burial Societies).

You have access to the following tools:

{tools_desc}

When users ask you to:
1. Create a group: Use create_group
2. Check group health: Use analyze_group_health
3. Record payments: Use process_payment
4. Approve loans: Use approve_loan

Always provide human-friendly explanations of the results.
For actuarial analysis, explain the metrics in non-technical terms.
"""

# Example: Integration with OpenAI's function_calling
import anthropic

async def chat_with_claude(user_message: str, mcp_client: SanibonaniMCPClient):
    """
    Chat with Claude using SanibonaniSave MCP tools.
    """
    # Fetch tools once at startup
    tools = await mcp_client.fetch_tools()
    
    # Format tools for Claude's function_calling
    claude_tools = [
        {
            "name": tool["name"],
            "description": tool["description"],
            "input_schema": tool["inputSchema"]
        }
        for tool in tools
    ]
    
    client = anthropic.Anthropic()
    messages = [{"role": "user", "content": user_message}]
    
    # Agentic loop
    while True:
        response = client.messages.create(
            model="claude-3-5-sonnet-20241022",
            max_tokens=1024,
            system=mcp_client.get_system_prompt(),
            tools=claude_tools,
            messages=messages
        )
        
        # If Claude wants to use a tool
        if response.stop_reason == "tool_use":
            tool_blocks = [b for b in response.content if b.type == "tool_use"]
            
            for tool_block in tool_blocks:
                tool_result = await mcp_client.call_tool(
                    tool_block.name,
                    **tool_block.input
                )
                
                # Add Claude's response and tool result back to messages
                messages.append({"role": "assistant", "content": response.content})
                messages.append({
                    "role": "user",
                    "content": [{
                        "type": "tool_result",
                        "tool_use_id": tool_block.id,
                        "content": json.dumps(tool_result)
                    }]
                })
                break  # Process one tool at a time
        else:
            # Claude finished (end_turn), extract text response
            text_response = "".join(
                b.text for b in response.content if hasattr(b, "text")
            )
            return text_response
```

#### Success Criteria:
- ✅ MCP client class created
- ✅ Claude/GPT integration tested with mock data
- ✅ System prompt generated correctly
- ✅ Tool calling loop functional (agentic loop works)

---

### 3.3 Deploy MCP Server (Backend Service)

**Status**: 0% Ready | **Action**: Host MCP server

#### Options:

**Option A: Docker + Cloud Run (Recommended)**
```dockerfile
# Dockerfile
FROM gradle:8-jdk17 as builder
WORKDIR /app
COPY . .
RUN gradle bootJar

FROM eclipse-temurin:17-jre-jammy
COPY --from=builder /app/build/libs/sanibonani-mcp-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Deploy to Google Cloud Run, AWS Lambda, or Azure Container Instances.

**Option B: Managed Backend-as-a-Service (Simpler)**
- Use Supabase Edge Functions to expose MCP endpoints
- Deploy Python client as AWS Lambda

#### Success Criteria:
- ✅ MCP Server running on cloud (accessible from internet)
- ✅ Health check endpoint: `GET /health` → `{"status": "ok"}`
- ✅ HTTPS/TLS configured
- ✅ Rate limiting & auth (API key or JWT) in place

---

## 🤖 Phase 4: AI Agent Orchestration (Weeks 8-10)

### 4.1 Autonomous Agent Design

**Status**: 0% Ready | **Action**: Create agent orchestration layer

#### Deliverables:

**4.1.1 Agent Types & Responsibilities**

```python
# backend/agents/sanibonani_agents.py
from enum import Enum
from dataclasses import dataclass
from typing import Any, Dict, List
import anthropic

class AgentType(Enum):
    """Agent types for different roles in the SanibonaniSave ecosystem"""
    
    # Compliance & Governance
    COMPLIANCE_AGENT = "compliance"  # Audits groups, enforces regulations
    RISK_AGENT = "risk"               # Monitors solvency, predicts defaults
    
    # Operations & Support
    SUPPORT_AGENT = "support"         # Answers member questions, troubleshooting
    ADMIN_ASSISTANT = "admin"         # Automates admin tasks (fee collection, reports)
    
    # Analytics & Insights
    INSIGHTS_AGENT = "insights"       # Generates business intelligence
    RECOMMENDATION_AGENT = "recommendation"  # Suggests optimizations

@dataclass
class Agent:
    """Base agent class"""
    agent_type: AgentType
    name: str
    system_prompt: str
    tools: List[Dict[str, Any]]
    memory: List[Dict[str, str]] = None
    
    def __post_init__(self):
        if self.memory is None:
            self.memory = []
    
    async def run(self, user_query: str, mcp_client) -> str:
        """Execute the agent with agentic loop"""
        client = anthropic.Anthropic()
        
        # Initialize messages with system context
        messages = [
            {"role": "user", "content": user_query}
        ]
        
        # Agentic loop (max 5 iterations to prevent runaway)
        for iteration in range(5):
            response = client.messages.create(
                model="claude-3-5-sonnet-20241022",
                max_tokens=2048,
                system=self.system_prompt,
                tools=self.tools,
                messages=messages
            )
            
            # Store in memory
            self.memory.append({
                "iteration": str(iteration),
                "query": user_query if iteration == 0 else "",
                "response": str(response)
            })
            
            # If agent finished, return result
            if response.stop_reason == "end_turn":
                return "".join(
                    b.text for b in response.content if hasattr(b, "text")
                )
            
            # Otherwise, execute tools and continue
            if response.stop_reason == "tool_use":
                tool_blocks = [b for b in response.content if b.type == "tool_use"]
                
                for tool_block in tool_blocks:
                    tool_result = await mcp_client.call_tool(
                        tool_block.name,
                        **tool_block.input
                    )
                    
                    messages.append({"role": "assistant", "content": response.content})
                    messages.append({
                        "role": "user",
                        "content": [{
                            "type": "tool_result",
                            "tool_use_id": tool_block.id,
                            "content": json.dumps(tool_result)
                        }]
                    })
        
        return "Max iterations reached. Agent loop terminated."


# Agent Definitions

COMPLIANCE_AGENT = Agent(
    agent_type=AgentType.COMPLIANCE_AGENT,
    name="Compliance Auditor",
    system_prompt="""
You are the SanibonaniSave Compliance Agent. Your role is to:
1. Audit groups for regulatory compliance (FSB guidelines for burial societies, etc.)
2. Flag violations (e.g., insufficient reserves, excessive member concentration risk)
3. Generate compliance reports
4. Recommend corrective actions

Always cite specific regulations and thresholds.
Prioritize groups at highest risk.
    """,
    tools=[]  # Will be populated from MCP server
)

RISK_AGENT = Agent(
    agent_type=AgentType.RISK_AGENT,
    name="Risk Monitor",
    system_prompt="""
You are the SanibonaniSave Risk Agent. Your role is to:
1. Analyze group financial health using actuarial metrics
2. Predict potential defaults or insolvencies
3. Alert admins to early warning signs
4. Suggest risk mitigation strategies

Use the analyze_group_health tool to get detailed metrics.
Flag groups with risk score > 60 for immediate review.
    """,
    tools=[]
)

ADMIN_ASSISTANT = Agent(
    agent_type=AgentType.ADMIN_ASSISTANT,
    name="Admin Assistant",
    system_prompt="""
You are the SanibonaniSave Admin Assistant. Your role is to:
1. Automate routine admin tasks (fee collection, payment processing)
2. Generate monthly reports for group admins
3. Handle member onboarding validation
4. Manage loan approval workflows

Be professional and clear in all communications.
Always confirm before taking actions that affect money or member status.
    """,
    tools=[]
)

INSIGHTS_AGENT = Agent(
    agent_type=AgentType.INSIGHTS_AGENT,
    name="Insights Analyst",
    system_prompt="""
You are the SanibonaniSave Insights Agent. Your role is to:
1. Analyze platform-wide trends (member growth, payment patterns, etc.)
2. Identify opportunities for engagement and retention
3. Generate business intelligence reports
4. Track KPIs (Active Groups, Member Retention, Revenue)

Present data in non-technical, actionable terms.
Use visualizations where possible (tables, summaries).
    """,
    tools=[]
)
```

**4.1.2 Agent Coordinator (Orchestrator)**

```python
# backend/orchestration/agent_coordinator.py
from typing import Dict, List, Any
import asyncio

class AgentCoordinator:
    """
    Coordinates multiple agents to work together.
    Distributes tasks, aggregates results, and manages agent memory.
    """
    
    def __init__(self, mcp_client, agents: List[Agent]):
        self.mcp_client = mcp_client
        self.agents = {agent.name: agent for agent in agents}
        self.task_queue: List[Dict[str, Any]] = []
    
    async def dispatch_task(self, task_type: str, **kwargs) -> Dict[str, Any]:
        """
        Dispatch a task to the appropriate agent(s).
        
        Examples:
        - task_type="audit_group", group_id="g123" -> COMPLIANCE_AGENT
        - task_type="analyze_risk", group_id="g123" -> RISK_AGENT
        - task_type="collect_fees" -> ADMIN_ASSISTANT
        - task_type="generate_report", report_type="monthly" -> INSIGHTS_AGENT
        """
        
        task_routing = {
            "audit_group": self.agents["Compliance Auditor"],
            "analyze_risk": self.agents["Risk Monitor"],
            "collect_fees": self.agents["Admin Assistant"],
            "approve_loan": self.agents["Admin Assistant"],
            "generate_report": self.agents["Insights Analyst"],
            "member_onboarding": self.agents["Admin Assistant"],
        }
        
        if task_type not in task_routing:
            raise ValueError(f"Unknown task type: {task_type}")
        
        agent = task_routing[task_type]
        query = self._build_query(task_type, kwargs)
        
        result = await agent.run(query, self.mcp_client)
        return {
            "agent": agent.name,
            "task": task_type,
            "result": result,
            "kwargs": kwargs
        }
    
    async def run_parallel_tasks(self, tasks: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """Run multiple tasks in parallel (for batch operations)"""
        return await asyncio.gather(*[
            self.dispatch_task(task["type"], **task.get("params", {}))
            for task in tasks
        ])
    
    async def run_scheduled_job(self, job_name: str):
        """
        Run scheduled background jobs.
        Examples: daily fee collection, nightly compliance audits, weekly insights
        """
        
        scheduled_jobs = {
            "daily_fee_collection": {
                "agent": "Admin Assistant",
                "query": "Collect all outstanding platform fees from groups due today."
            },
            "nightly_compliance_audit": {
                "agent": "Compliance Auditor",
                "query": "Audit all groups for compliance violations. Flag any at-risk groups."
            },
            "weekly_risk_analysis": {
                "agent": "Risk Monitor",
                "query": "Analyze all groups for financial risk. Generate alerts for high-risk groups."
            },
            "monthly_insights": {
                "agent": "Insights Analyst",
                "query": "Generate monthly platform insights: user growth, engagement, revenue trends."
            }
        }
        
        if job_name not in scheduled_jobs:
            raise ValueError(f"Unknown job: {job_name}")
        
        job_config = scheduled_jobs[job_name]
        agent = self.agents[job_config["agent"]]
        result = await agent.run(job_config["query"], self.mcp_client)
        
        return {
            "job": job_name,
            "agent": agent.name,
            "result": result,
            "timestamp": datetime.now().isoformat()
        }
    
    def _build_query(self, task_type: str, kwargs: Dict) -> str:
        """Build a natural language query for the agent"""
        
        queries = {
            "audit_group": f"Audit group {kwargs.get('group_id')} for FSB compliance.",
            "analyze_risk": f"Analyze financial risk for group {kwargs.get('group_id')}.",
            "collect_fees": f"Collect platform fees for {kwargs.get('count', 'all')} groups.",
            "approve_loan": f"Review and approve/reject loan {kwargs.get('loan_id')}.",
            "generate_report": f"Generate {kwargs.get('report_type', 'monthly')} report.",
            "member_onboarding": f"Validate onboarding for member {kwargs.get('member_id')}.",
        }
        
        return queries.get(task_type, "")

# Example usage
async def main():
    mcp_client = SanibonaniMCPClient()
    await mcp_client.fetch_resources()
    tools = await mcp_client.fetch_tools()
    
    # Initialize agents
    agents = [COMPLIANCE_AGENT, RISK_AGENT, ADMIN_ASSISTANT, INSIGHTS_AGENT]
    for agent in agents:
        agent.tools = tools
    
    coordinator = AgentCoordinator(mcp_client, agents)
    
    # Dispatch tasks
    result1 = await coordinator.dispatch_task("audit_group", group_id="g123")
    result2 = await coordinator.dispatch_task("analyze_risk", group_id="g123")
    
    # Run scheduled jobs
    insights = await coordinator.run_scheduled_job("monthly_insights")
    
    print(result1, result2, insights)

if __name__ == "__main__":
    asyncio.run(main())
```

#### Success Criteria:
- ✅ 4+ agent types defined with distinct system prompts
- ✅ Agent coordinator implemented & tested
- ✅ Parallel task execution working
- ✅ Scheduled job framework in place

---

### 4.2 Integration with Android App

**Status**: 0% Ready | **Action**: Add agent response handler in app

#### Deliverables:

```kotlin
// app/src/main/java/com/sanibonani/save/viewmodel/AIAssistantViewModel.kt
package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.sanibonani.save.domain.model.AIResponse
import com.sanibonani.save.data.remote.AIServiceClient

@HiltViewModel
class AIAssistantViewModel @Inject constructor(
    private val aiServiceClient: AIServiceClient
) : ViewModel() {
    
    private val _state = MutableStateFlow(AIAssistantUiState())
    val state: StateFlow<AIAssistantUiState> = _state.asStateFlow()
    
    fun askAgent(query: String, agentType: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            
            aiServiceClient.queryAgent(query, agentType)
                .onSuccess { response ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            responses = it.responses + response,
                            lastResponse = response
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
    
    fun clearHistory() {
        _state.update { it.copy(responses = emptyList(), lastResponse = null) }
    }
}

data class AIAssistantUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val responses: List<AIResponse> = emptyList(),
    val lastResponse: AIResponse? = null
)
```

```kotlin
// app/src/main/java/com/sanibonani/save/ui/screens/ai/AIAssistantScreen.kt
package com.sanibonani.save.ui.screens.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sanibonani.save.viewmodel.AIAssistantViewModel

@Composable
fun AIAssistantScreen(
    viewModel: AIAssistantViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var userInput by remember { mutableStateOf("") }
    var selectedAgent by remember { mutableStateOf("insights") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Agent selector
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf(
                "insights" to "📊 Insights",
                "compliance" to "✅ Compliance",
                "risk" to "⚠️ Risk",
                "admin" to "👨‍💼 Admin"
            ).forEach { (agent, label) ->
                Button(
                    onClick = { selectedAgent = agent },
                    modifier = Modifier
                        .weight(1f)
                        .padding(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedAgent == agent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(label, fontSize = 10.sp)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Response history
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(state.responses) { response ->
                ResponseCard(response)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        
        // Loading indicator
        if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        
        // Error message
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Input area
        Row(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = userInput,
                onValueChange = { userInput = it },
                placeholder = { Text("Ask me anything...") },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                singleLine = false
            )
            
            Button(
                onClick = {
                    if (userInput.isNotBlank()) {
                        viewModel.askAgent(userInput, selectedAgent)
                        userInput = ""
                    }
                },
                modifier = Modifier
                    .padding(start = 8.dp)
                    .height(48.dp)
            ) {
                Text("Send")
            }
        }
    }
}

@Composable
fun ResponseCard(response: AIResponse) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = response.agent,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = response.result,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = response.timestamp,
                style = MaterialTheme.typography.labelTiny,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
```

#### Success Criteria:
- ✅ AI Assistant screen created & integrated into nav graph
- ✅ Agent selection working
- ✅ Real-time response streaming (if WebSocket available)
- ✅ Response history persisted

---

## 📊 Phase 5: Monitoring, Analytics & Continuous Delivery (Weeks 11-16)

### 5.1 Observability & Monitoring Stack

**Status**: 60% Ready | **Action**: Complete monitoring setup

#### Deliverables:

- [ ] **Firebase Crashlytics**: Already configured; verify it captures:
  - Unhandled exceptions
  - Network timeouts
  - Database errors

- [ ] **Firebase Analytics**: Track key events:
  ```kotlin
  // In ViewModels
  Firebase.analytics.logEvent("group_created") {
      param("group_type", groupType)
      param("member_count", memberCount)
  }
  
  Firebase.analytics.logEvent("payment_processed") {
      param("amount", amount)
      param("method", paymentMethod)
  }
  ```

- [ ] **Supabase Logs**: Monitor database performance
  - Query duration trends
  - Connection pool saturation
  - RLS policy execution time

- [ ] **Datadog / New Relic**: (Optional but recommended)
  ```kotlin
  // Add to build.gradle.kts
  implementation("com.datadoghq:dd-sdk-android:_")
  
  // In SanibonaniApp.kt
  Datadog.initialize(context, configuration)
  ```

- [ ] **Custom Metrics Dashboard**:
  - Active groups per day
  - Member retention rate
  - Average group health score
  - Payment success rate
  - Platform fee collection rate

#### Success Criteria:
- ✅ Crashlytics capturing 100% of crashes
- ✅ Custom events logged for key actions
- ✅ Dashboard showing key KPIs in real-time

---

### 5.2 Automated Deployment Pipeline

**Status**: 30% Ready | **Action**: Set up CI/CD for play store

#### Deliverables:

**GitHub Actions Workflow**:

```yaml
# .github/workflows/deploy.yml
name: Deploy to Play Store

on:
  push:
    tags:
      - 'v[0-9]+.[0-9]+.[0-9]+'  # Trigger on version tags

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Java
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Decode keystore
        run: |
          echo ${{ secrets.KEYSTORE_B64 }} | base64 -d > release.keystore
      
      - name: Build release APK
        env:
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
          SUPABASE_URL: ${{ secrets.SUPABASE_URL }}
          SUPABASE_ANON_KEY: ${{ secrets.SUPABASE_ANON_KEY }}
          # ... other secrets
        run: |
          ./gradlew bundleRelease
      
      - name: Run tests
        run: |
          ./gradlew test --continue
      
      - name: Upload to Play Store (Internal Testing)
        uses: r0adkll/upload-google-play@v1
        with:
          serviceAccountJsonPlainText: ${{ secrets.GOOGLE_PLAY_KEY_JSON }}
          packageName: com.sanibonani.save
          releaseFiles: app/release/app.aab
          track: internal
          inAppUpdatePriority: 5
      
      - name: Create GitHub Release
        uses: actions/create-release@v1
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        with:
          tag_name: ${{ github.ref }}
          release_name: Release ${{ github.ref }}
          body: |
            See CHANGELOG.md for details
          draft: false
          prerelease: false

  promotion:
    needs: build
    runs-on: ubuntu-latest
    if: github.event_name == 'push' && contains(github.ref, 'refs/tags/')
    
    steps:
      - name: Wait 24 hours for internal testing
        run: sleep 86400
      
      - name: Promote to production
        uses: r0adkll/upload-google-play@v1
        with:
          serviceAccountJsonPlainText: ${{ secrets.GOOGLE_PLAY_KEY_JSON }}
          packageName: com.sanibonani.save
          track: production
          whatsNewDir: whatsnew
```

#### Success Criteria:
- ✅ GitHub Actions workflow created & tested
- ✅ Secrets configured in GitHub
- ✅ Tag-based deployment working (v1.0.0 → Play Store)
- ✅ Automated tests running on every push

---

### 5.3 Post-Deployment Monitoring & Rollback

**Status**: 0% Ready | **Action**: Create monitoring & rollback procedures

#### Deliverables:

**Monitoring Dashboard (Post-Launch)**:
```
Key Metrics (first 24 hours):
- Crash Rate: < 0.1%
- Session Duration: > 2 min (target)
- Payment Success Rate: > 95%
- Authentication Failures: < 1%
- API Error Rate: < 2%

If any metric breaches threshold → Auto-alert on Slack
If crashes > 50 in 1 hour → Auto-trigger rollback
```

**Rollback Procedure**:
```
1. Identify the version causing issues
2. Upload previous stable version to Play Store (internal testing track)
3. Test for 1 hour
4. Promote to production
5. Post-mortem meeting within 24 hours
```

#### Success Criteria:
- ✅ Monitoring alerts configured
- ✅ Rollback procedure documented & tested
- ✅ On-call rotation established

---

## 📝 Final Checklist: Pre-Launch (Weeks 14-16)

- [ ] **Security Audit**
  - [ ] OWASP Top 10 review
  - [ ] SSL/TLS certificates pinning
  - [ ] API key rotation
  - [ ] Secrets not in APK (verified with `strings` command)

- [ ] **Performance Testing**
  - [ ] Stress test with 10k concurrent users (Supabase Realtime)
  - [ ] Database query optimization (< 100ms p99)
  - [ ] APK size < 60 MB
  - [ ] Memory footprint < 300 MB (average)

- [ ] **Compliance**
  - [ ] Privacy Policy posted & accessible
  - [ ] GDPR compliance (data export, deletion)
  - [ ] FSB regulations for burial societies (if applicable)
  - [ ] Payment PCI DSS compliance (via YoCo)

- [ ] **Localization**
  - [ ] UI text in English + Zulu + Xhosa
  - [ ] Currency formatting (ZAR)
  - [ ] Date formatting (DD/MM/YYYY)

- [ ] **App Store Listing**
  - [ ] Screenshots prepared (6 per locale)
  - [ ] 80-character title
  - [ ] 4000-character description
  - [ ] Keywords identified
  - [ ] Feature graphics created

- [ ] **Support Infrastructure**
  - [ ] Help/FAQ screen in app
  - [ ] In-app error messages are user-friendly
  - [ ] Support email configured
  - [ ] Chatbot/Help desk integration ready

---

## 🎬 Timeline Summary

| Phase | Duration | Key Milestones |
|-------|----------|----------------|
| **Phase 1** | Weeks 1-2 | Production build signing, secrets, performance baseline |
| **Phase 2** | Weeks 3-4 | Supabase prod setup, edge functions, database optimization |
| **Phase 3** | Weeks 5-7 | MCP server created, REST API deployed, LLM client working |
| **Phase 4** | Weeks 8-10 | Agent types defined, coordinator implemented, Android integration |
| **Phase 5** | Weeks 11-16 | Monitoring stacks, CI/CD pipeline, launch readiness |
| **Launch** | Week 16+ | Public release on Google Play Store |

---

## 💡 Key Recommendations

### 1. **Phased MCP Integration**
- **MVP (Week 7)**: Read-only MCP tools (analyze_group_health, list_groups)
- **Phase 2 (Week 10)**: Write tools (create_group, process_payment)
- **Phase 3 (Week 14)**: Autonomous agents running scheduled jobs

### 2. **Agent Strategy**
- Start with **INSIGHTS_AGENT** (low-risk, high-value)
- Progress to **ADMIN_ASSISTANT** (automates routine tasks)
- Finally **COMPLIANCE_AGENT** (mission-critical)

### 3. **LLM Choice**
- **Claude 3.5 Sonnet** (Recommended): Best at complex reasoning, excellent tool use
- **GPT-4o**: Strong alternative, good cost-benefit
- **Gemini 2.0 Flash**: Lightweight option for high-volume queries

### 4. **Cost Estimation (Monthly)**

| Component | Cost |
|-----------|------|
| Supabase (prod) | $25-50 |
| Cloud Run (MCP server) | $50-100 |
| LLM API calls (agents) | $200-500 (Claude) |
| Firebase (analytics, crashlytics) | $25-50 |
| Datadog (monitoring) | $50-100 |
| **Total** | **~$400-800/month** |

### 5. **Scaling Considerations**
- **Database**: Supabase with read replicas (for reporting queries)
- **API Gateway**: Rate limiting & authentication at Cloudflare
- **Agent Orchestration**: Use message queue (Pub/Sub) for async task processing
- **Caching**: Redis for user sessions, group metadata

---

## 📞 Support & Documentation

Create these post-launch:
1. **Developer Onboarding Guide**: MCP protocol, agent setup
2. **API Documentation**: OpenAPI/Swagger for MCP endpoints
3. **Agent Prompt Library**: Reusable system prompts for each agent type
4. **Troubleshooting Guide**: Common issues & solutions

---

**Next Steps:**
1. Review this roadmap with stakeholders
2. Adjust timeline based on resource availability
3. Begin Phase 1 (Build & Secrets) immediately
4. Weekly sync to track progress against milestones

---

*Created: May 12, 2026*  
*Prepared for: SanibonaniSave Deployment & Agent Integration*

