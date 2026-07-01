# Mulesoft Review Rules

## Security
- Every HTTP Listener config must have TLS/HTTPS enabled. Plain HTTP listeners are not allowed except on explicitly named "internal-only" connectors.
- API endpoints that accept request bodies must validate input rather than passing it directly to backend systems.

## Error Handling
- Every flow that calls an external system (HTTP request, database, JMS, etc.) must have an explicit error handler, not just a default/global one.
- Error handlers should log a meaningful message including the flow name and the original error, not just a generic "An error occurred".

## Flow Design
- Flow and sub-flow names should be descriptive and use camelCase (e.g. `processOrderFlow`, not `flow1` or `Flow_2`).
- Flows referenced from other files (via flow-ref) must actually exist somewhere in the project; dangling references are a defect.
- Avoid duplicate logic across flows — if two flows perform near-identical transformation or validation steps, recommend extracting a shared sub-flow.

## API Contract Consistency
- The Swagger/OpenAPI description should accurately reflect what the implementation actually does (e.g. declared response codes, required fields).
- Every endpoint defined in the Swagger/OpenAPI spec should have a corresponding implementation in the Mulesoft flows; flag any spec endpoints with no matching implementation, and any implemented endpoints missing from the spec.
