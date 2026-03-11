# codex_readme_mcpserver

## 1. Назначение модуля
Модуль `mcpserver` — локальный embedded MCP-сервер, который запускается внутри Android-приложения и отдает GitHub tools через JSON-RPC endpoint `/mcp`.

Фактическая роль:
- локально поднимает HTTP сервер на `127.0.0.1:{port}/mcp`,
- реализует MCP-методы `initialize`, `tools/list`, `tools/call`,
- проксирует вызовы инструментов к GitHub REST API.

---

## 2. Архитектура внутри модуля
- `LocalMcpServerManager` — lifecycle-обертка для старта/остановки сервера.
- `EmbeddedMcpServer` — HTTP/JSON-RPC сервер (Ktor CIO).
- `GithubMcpToolRegistry` — реестр инструментов + вызов конкретного инструмента.
- `GithubApiClient` — низкоуровневый клиент GitHub REST API.

---

## 3. Файлы, классы и методы

### `/mcpserver/src/main/kotlin/com/example/mcpserver/LocalMcpServerManager.kt`

**Enum**: `LocalServerStatus`
- `STOPPED`, `STARTING`, `RUNNING`, `ERROR`.

**Класс**: `LocalMcpServerManager`
- Поля:
  - `status` (volatile),
  - `lastError` (volatile),
  - `embeddedServer`.

Методы:
- `start(): String`
  - переводит статус в `STARTING`,
  - запускает `EmbeddedMcpServer`,
  - на успехе: `RUNNING`, возвращает URL,
  - на ошибке: `ERROR`, запоминает ошибку, пробрасывает исключение.
- `stop()` — останавливает сервер, ставит `STOPPED`.
- `status()` — возвращает текущий статус.
- `lastError()` — возвращает последнюю ошибку.
- `currentUrl()` — возвращает текущий URL endpoint.

---

### `/mcpserver/src/main/kotlin/com/example/mcpserver/GithubApiClient.kt`

**Класс**: `GithubApiClient`
- Использует Ktor `HttpClient` + Moshi adapters (`Map<String, Any?>`, `List<Map<...>>`).

Методы:
- `getUser(username)`
  - GET `https://api.github.com/users/{username}`,
  - возвращает `Result<Map<String, Any?>>`.
- `getRepo(owner, repo)`
  - GET `https://api.github.com/repos/{owner}/{repo}`,
  - возвращает `Result<Map<String, Any?>>`.
- `listRepoIssues(owner, repo)`
  - GET `https://api.github.com/repos/{owner}/{repo}/issues?state=open&per_page=10`,
  - возвращает `Result<List<Map<String, Any?>>>`.

Во всех запросах проставляются заголовки:
- `Accept: application/vnd.github+json`
- `User-Agent: android-local-mcp`

---

### `/mcpserver/src/main/kotlin/com/example/mcpserver/GithubMcpToolRegistry.kt`

**Класс**: `GithubMcpToolRegistry`
- Отвечает за описание доступных tools и исполнение tool call.

Методы:
- `listTools(): List<Map<String, Any?>>`
  - возвращает 3 инструмента:
    1. `github_get_user`
    2. `github_get_repo`
    3. `github_list_repo_issues`
  - включает `description` (на русском) и `inputSchema` (required params).

- `callTool(name, arguments): Map<String, Any?>`
  - dispatch по `name`:
    - `github_get_user` (обязателен `username`),
    - `github_get_repo` (обязательны `owner`, `repo`),
    - `github_list_repo_issues` (обязательны `owner`, `repo`),
  - на успех: возвращает MCP-совместимый результат `content + structuredContent + isError=false`,
  - на ошибке: MCP error result `isError=true`.

Внутренние методы:
- `successResult(text, structured)` — формирует успешный MCP payload.
- `errorResult(message)` — формирует ошибочный MCP payload.

---

### `/mcpserver/src/main/kotlin/com/example/mcpserver/EmbeddedMcpServer.kt`

**Класс**: `EmbeddedMcpServer`
- Основной сервер JSON-RPC/MCP.
- По умолчанию host=`127.0.0.1`, preferred port=`8787`.
- Если порт занят, пытается найти свободный ephemeral порт.

Публичные методы:
- `start()`
  - стартует сервер,
  - проверяет готовность endpoint,
  - перебирает candidate ports,
  - кидает `IllegalStateException`, если не удалось запустить.
- `stop()` — останавливает engine.
- `url()` — возвращает `http://{host}:{actualPort}/mcp`.

Внутренние методы (JSON-RPC/utility):
- `successResponse(id, result)` — JSON-RPC success envelope.
- `errorResponse(id, code, message)` — JSON-RPC error envelope.
- `parseJsonRpcPayload(payloadText)`
  - устойчивый парсер payload,
  - поддерживает сырые JSON, urlencoded payload/message, NDJSON, `data:`-обертки и извлечение первого JSON-объекта.
- `parseFirstJsonValue(raw)` — lenient parsing через Moshi JsonReader.
- `extractJsonObject(raw)` — грубое извлечение `{...}`.
- `extractFirstBalancedJsonObject(raw)` — извлечение сбалансированного JSON-объекта.
- `logInfo(message)`
- `logError(message, throwable)`
- `isPortAvailable(port)`
- `findEphemeralPort()`
- `createServer(port)`
  - поднимает Ktor routing:
    - `GET /mcp` — health/debug ответ,
    - `POST /mcp` — обработка JSON-RPC:
      - `initialize`
      - `notifications/initialized`
      - `tools/list`
      - `tools/call`
      - unknown method -> `-32601`.
- `waitUntilEndpointReady(port, timeoutMs)`
- `isOurEndpointReady(port)` — GET probe, проверяет что endpoint именно нашего MCP сервера.

---

## 4. Полезные заметки

1. Сервер возвращает HTTP 200 даже для JSON-RPC ошибок (ошибка в JSON body), что корректно для многих JSON-RPC клиентов.
2. В `POST /mcp` есть подробное логирование входящих заголовков и payload-ошибок — удобно для диагностики transport проблем.
3. `tools/call` ожидает `params.arguments` как объект map.
4. Результат tool вызова всегда возвращается в MCP-формате:
   - `content` (text blocks),
   - `structuredContent` (структурированные данные),
   - `isError`.
5. Модуль собран на **Java/Kotlin 21**, что важно учитывать при интеграции с модулем `app` (Java 11).

