# codex_readme_mcpserver

Актуально для состояния проекта на **2026-03-16**.

## 1. Назначение модуля
`mcpserver` — embedded MCP server runtime, который встраивается в Android-приложение и обслуживает MCP endpoint `/mcp`.

Текущая модель:
- несколько независимых локальных серверов могут использовать один и тот же runtime-код,
- конкретный набор tools задается через `McpToolRegistry`,
- в `app` модуле поднимаются минимум два инстанса: `local-github-mcp` и `local-utility-mcp`.

## 2. MCP поведение сервера
- endpoint: `POST /mcp` и `GET /mcp` (health/debug).
- JSON-RPC методы: `initialize`, `notifications/initialized`, `tools/list`, `tools/call`.
- parse payload сделан устойчивым к нестандартным оберткам (urlencoded, NDJSON, `data:` и т.д.).
- Возврат tool call в формате MCP (`content`, `structuredContent`, `isError`).

## 3. Разделение инструментов по registry
### 3.1 `GithubMcpToolRegistry`
Инструменты: `github_get_user`, `github_get_repo`, `github_list_repo_issues`, `github_schedule_user_stars_tracking`, `github_get_user_stars_stats`, `github_stop_user_stars_tracking`.

### 3.2 `UtilityMcpToolRegistry`
Инструменты: `summarize_github_report`, `save_summary_to_file` (плюс alias `summarize_github_user_profile`).

## 4. Полный каталог классов и методов по файлам
Ниже перечислены декларации из каждого Kotlin-файла `mcpserver/src/main/kotlin`.

### `mcpserver/src/main/kotlin/com/example/mcpserver/EmbeddedMcpServer.kt`
Embedded HTTP/JSON-RPC MCP сервер c endpoint /mcp.

- `L27`: `class EmbeddedMcpServer(`
- `L47`: `fun start() {`
- `L83`: `fun stop() {`
- `L88`: `fun url(): String = "http://$host:$actualPort/mcp"`
- `L90`: `private fun successResponse(id: Any?, result: Map<String, Any?>): String {`
- `L100`: `private fun errorResponse(id: Any?, code: Int, message: String): String {`
- `L113`: `private fun parseJsonRpcPayload(payloadText: String): Map<String, Any?>? {`
- `L196`: `private fun parseFirstJsonValue(raw: String): Any? {`
- `L204`: `private fun extractJsonObject(raw: String): String? {`
- `L211`: `private fun extractFirstBalancedJsonObject(raw: String): String? {`
- `L229`: `private fun logInfo(message: String) {`
- `L233`: `private fun logError(message: String, throwable: Throwable? = null) {`
- `L238`: `private fun isPortAvailable(port: Int): Boolean {`
- `L246`: `private fun findEphemeralPort(): Int {`
- `L255`: `private fun createServer(port: Int): EmbeddedServer<*, *> {`
- `L384`: `private fun waitUntilEndpointReady(port: Int, timeoutMs: Long = 2_500L): Boolean {`
- `L393`: `private fun isOurEndpointReady(port: Int): Boolean {`

### `mcpserver/src/main/kotlin/com/example/mcpserver/GithubApiClient.kt`
Клиент GitHub REST API.

- `L11`: `class GithubApiClient(`
- `L29`: `suspend fun getUser(username: String): Result<Map<String, Any?>> = runCatching {`
- `L35`: `suspend fun getRepo(owner: String, repo: String): Result<Map<String, Any?>> = runCatching {`
- `L41`: `suspend fun listRepoIssues(owner: String, repo: String): Result<List<Map<String, Any?>>> = runCatching {`
- `L49`: `suspend fun listUserRepos(username: String): Result<List<Map<String, Any?>>> = runCatching {`
- `L57`: `private fun applyDefaultHeaders(builder: io.ktor.client.request.HttpRequestBuilder) {`

### `mcpserver/src/main/kotlin/com/example/mcpserver/GithubMcpToolRegistry.kt`
Реестр GitHub/Tracking tools.

- `L5`: `class GithubMcpToolRegistry(`
- `L9`: `override fun listTools(): List<Map<String, Any?>> = listOf(`
- `L213`: `private fun successResult(text: String, structured: Any?): Map<String, Any?> {`
- `L221`: `private fun errorResult(message: String): Map<String, Any?> {`
- `L229`: `private fun parseNumber(value: Any?): Double? {`

### `mcpserver/src/main/kotlin/com/example/mcpserver/GithubTrackingTools.kt`
Контракт tracking-инструментов и noop-реализация.

- `L3`: `data class TrackingToolResult(`
- `L9`: `interface GithubTrackingTools {`
- `L10`: `suspend fun scheduleUserMetricTracking(`
- `L19`: `suspend fun getUserMetricStats(`
- `L26`: `suspend fun stopUserMetricTracking(`
- `L31`: `object NoopGithubTrackingTools : GithubTrackingTools {`

### `mcpserver/src/main/kotlin/com/example/mcpserver/LocalMcpServerManager.kt`
Lifecycle-обертка запуска/остановки embedded MCP сервера.

- `L3`: `enum class LocalServerStatus {`
- `L7`: `class LocalMcpServerManager(`
- `L16`: `fun start(): String {`
- `L30`: `fun stop() {`
- `L35`: `fun status(): LocalServerStatus = status`
- `L37`: `fun lastError(): String? = lastError`
- `L39`: `fun currentUrl(): String = embeddedServer.url()`

### `mcpserver/src/main/kotlin/com/example/mcpserver/McpToolRegistry.kt`
Базовый интерфейс реестра MCP инструментов.

- `L3`: `interface McpToolRegistry {`
- `L4`: `fun listTools(): List<Map<String, Any?>>`
- `L5`: `suspend fun callTool(name: String, arguments: Map<String, Any?>): Map<String, Any?>`

### `mcpserver/src/main/kotlin/com/example/mcpserver/SummaryStorageTools.kt`
Контракт сохранения summary и noop-реализация.

- `L3`: `interface SummaryStorageTools {`
- `L4`: `suspend fun saveSummaryToFile(`
- `L11`: `object NoopSummaryStorageTools : SummaryStorageTools {`

### `mcpserver/src/main/kotlin/com/example/mcpserver/UtilityMcpToolRegistry.kt`
Реестр utility tools (summary/save).

- `L6`: `class UtilityMcpToolRegistry(`
- `L19`: `override fun listTools(): List<Map<String, Any?>> = listOf(`
- `L145`: `private fun successResult(text: String, structured: Any?): Map<String, Any?> {`
- `L153`: `private fun errorResult(message: String): Map<String, Any?> {`
- `L161`: `private fun normalizeJsonObject(value: Any?): Map<String, Any?>? {`
- `L171`: `private fun normalizeJsonArray(value: Any?): List<Map<String, Any?>>? {`
- `L186`: `private fun parseJsonMap(raw: String): Map<String, Any?>? {`
- `L193`: `private fun parseJsonArray(raw: String): List<Map<String, Any?>>? {`
- `L206`: `private fun Any?.asText(): String = this?.toString().orEmpty()`
- `L208`: `private fun Any?.asLong(): Long? {`

