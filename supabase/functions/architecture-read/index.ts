import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { ARCHITECTURE_BLUEPRINT, parseModelId } from "./blueprint.ts";

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, x-architecture-key",
};

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: CORS_HEADERS });
  }

  if (req.method !== "GET") {
    return json({ error: "Method not allowed. Use GET." }, 405);
  }

  // Optional hardening: if secret is configured, require the matching header.
  const requiredKey = Deno.env.get("ARCHITECTURE_READ_KEY");
  if (requiredKey) {
    const provided = req.headers.get("x-architecture-key");
    if (provided !== requiredKey) {
      return json({ error: "Unauthorized." }, 401);
    }
  }

  try {
    const url = new URL(req.url);
    const action = (url.searchParams.get("action") ?? "blueprint").toLowerCase();

    switch (action) {
      case "blueprint":
        return json({
          version: ARCHITECTURE_BLUEPRINT.version,
          models: ARCHITECTURE_BLUEPRINT.models,
          event_architecture: ARCHITECTURE_BLUEPRINT.event_architecture,
        });

      case "models":
        return json({
          version: ARCHITECTURE_BLUEPRINT.version,
          items: ARCHITECTURE_BLUEPRINT.models.map((model) => ({
            id: model.id,
            summary: model.summary,
            api_count: model.api_operations.length,
            workflow_count: model.workflows.length,
            backend_function_count: model.backend_functions.length,
          })),
        });

      case "model": {
        const modelId = parseModelId(url.searchParams.get("model"));
        if (!modelId) {
          return json({ error: "Invalid or missing model query param." }, 400);
        }

        const item = ARCHITECTURE_BLUEPRINT.models.find((model) => model.id === modelId);
        if (!item) {
          return json({ error: "Model not found." }, 404);
        }
        return json(item);
      }

      case "operations": {
        const modelId = parseModelId(url.searchParams.get("model"));
        if (!modelId) {
          return json({
            items: ARCHITECTURE_BLUEPRINT.models.flatMap((model) =>
              model.api_operations.map((op) => ({ ...op, model: model.id }))
            ),
          });
        }

        const item = ARCHITECTURE_BLUEPRINT.models.find((model) => model.id === modelId);
        if (!item) {
          return json({ error: "Model not found." }, 404);
        }

        return json({
          model: item.id,
          items: item.api_operations,
        });
      }

      case "ai": {
        const modelId = parseModelId(url.searchParams.get("model"));
        if (!modelId) {
          return json({
            items: ARCHITECTURE_BLUEPRINT.models.flatMap((model) =>
              model.ai_agent_opportunities.map((item) => ({ ...item, model: model.id }))
            ),
          });
        }

        const item = ARCHITECTURE_BLUEPRINT.models.find((model) => model.id === modelId);
        if (!item) {
          return json({ error: "Model not found." }, 404);
        }

        return json({
          model: item.id,
          items: item.ai_agent_opportunities,
        });
      }

      case "events":
        return json({
          version: ARCHITECTURE_BLUEPRINT.version,
          event_architecture: ARCHITECTURE_BLUEPRINT.event_architecture,
          registry: ARCHITECTURE_BLUEPRINT.core_events_registry,
        });

      default:
        return json({ error: "Unsupported action." }, 400);
    }
  } catch (error) {
    console.error("architecture-read error", error);
    return json({ error: error instanceof Error ? error.message : "Unknown server error." }, 500);
  }
});

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
  });
}

