# Financial Group Architecture Implementation

This pass adds a safe, additive architecture layer for the comprehensive financial-group analysis.

## What was implemented

- Domain blueprint catalog in `domain/src/main/java/com/sanibonani/save/domain/architecture/PlatformArchitectureBlueprint.kt`
- Mapping helper in `domain/src/main/java/com/sanibonani/save/domain/architecture/FinancialModelMappings.kt`
- Agent-facing YAML context in `.github/agent-financial-platform-blueprint.yaml`

## Why this is safe

- No existing group core logic paths were removed.
- No existing repository contracts were broken.
- No runtime behavior for current group flows was changed.
- This is metadata/context scaffolding for APIs, workflows, orchestration, and future service extraction.

## How to use for code generation

1. Read `.github/agent-financial-platform-blueprint.yaml` for a compact machine-readable context.
2. For richer typed metadata at runtime, use `PlatformArchitectureBlueprintCatalog.current()`.
3. Map current `GroupType` to architecture model category with `GroupType.toFinancialGroupModel()`.

## Suggested next incremental steps

1. Add API route handlers that consume `ApiOperation` definitions for scaffold generation.
2. Add SQL migration templates based on `DatabaseTable` blueprints.
3. Add outbox event schema docs from `EventArchitecture.coreEvents`.
4. Add AI-agent task registries from `AiAgentOpportunity` definitions.

