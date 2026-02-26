# eXbuilder6 AI Server (Spring Boot)

This is a Spring Boot backend implementation compatible with the **eXbuilder6 AI Studio** frontend.

## API Compatibility

The server exposes a `/api/generate` endpoint that follows the `web-service` provider protocol:

- **Endpoint**: `POST /api/generate`
- **Request Body**: `GenerateRequest` (prompt, stage, context, settings)
- **Response**: `GenerationResult`

## How to use with eXbuilder6 AI Studio

1. Run this Spring Boot application (default port 8080).
2. Open the AI Studio frontend.
3. Click the **Settings** (gear icon).
4. Select **Web Service** as the provider.
5. In the Web Service configuration, set the **Base URL** to:
   `http://localhost:8080/api/generate`
6. Click **Save**.
7. Enter a prompt and click **Build Component**.

## Implementation Details

- **Controller**: `GenerationController` routes the request.
- **Service**: `GenerationService` implements the logic for each generation stage:
  - `sql`: Generates DDL scripts.
  - `server`: Generates Spring Boot Controller/Service/Model files.
  - `layout`: Generates `.clx` XML.
  - `script`: Generates `.js` controller code.
- **DTOs**: Fully mapped to the frontend's `types.ts`.

## Dependencies
- Spring Web
- Lombok
- Jackson (included in Spring Web)
