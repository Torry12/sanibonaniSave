# Event Schema Registry (Scaffold)

This registry is an additive contract layer for outbox and realtime consumers.

## Files

- `docs/event-schemas/event-envelope.schema.json`: common envelope schema.
- `docs/event-schemas/core-events.registry.json`: core event list and payload contracts.

## Delivery and idempotency model

- Delivery: at-least-once
- Consumer requirement: idempotent handlers
- Dedup key preference: `event_id` + `aggregate_id` + `event_type`

## Recommended producer flow

1. Persist domain change and outbox event in one transaction.
2. Dispatcher publishes to realtime/webhook topic.
3. Consumer acknowledges and stores dedup marker.
4. Retries are safe due to idempotent upsert logic.

