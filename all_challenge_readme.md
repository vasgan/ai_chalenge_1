# all_challenge_readme (full)

Собрано из трёх файлов:
1. `README.md`
2. `app/codex_readme_app.md`
3. `mcpserver/codex_readme_mcpserver.md`

---

## 1) README.md

# AI Challenge Assistant

## Что это за проект
Это Android-приложение ассистента, которое помогает вести рабочие диалоги и задачи в чате.

Приложение умеет:
- вести несколько профилей пользователя,
- внутри профиля вести несколько задач,
- внутри задачи открывать несколько чатов,
- запоминать важные факты и рабочий контекст,
- подключаться к MCP-серверам с инструментами,
- автоматически вызывать инструменты по команде или по обычной фразе,
- выполнять цепочки действий (pipeline) из нескольких инструментов.

## Для кого проект
- Для пользователя, который хочет общаться с ассистентом и получать ответы с учетом контекста.
- Для разработчика, который хочет расширять инструменты ассистента через MCP.

## Как устроено по-простому
В приложении есть 3 основных уровня:
1. **Профиль** — кто вы и как ассистент должен с вами общаться.
2. **Задача** — отдельная рабочая тема внутри профиля.
3. **Чат** — конкретный диалог внутри задачи.

## Что ассистент хранит в памяти
1. **Краткосрочная память** — текущие сообщения чата.
2. **Рабочая память (Working Memory)** — состояние текущей задачи: цель, решения, вопросы, шаги.
3. **Долговременная память (LongTerm)** — данные профиля: описание, язык, пользовательские поля, инварианты.

## Инструменты (MCP)
Приложение поддерживает несколько MCP-серверов одновременно.

Сейчас есть два локальных сервера:
- **github** — инструменты GitHub (пользователь, репозиторий, issues, tracking).
- **utility** — сервисные инструменты (суммаризация и сохранение отчета).

## Как вызвать инструменты
1. Явная команда:
- `/tool github:github_get_user {"username":"octocat"}`

2. Запуск pipeline:
- `/pipeline cross_server_github_report_flow {"username":"octocat","repo":"Hello-World"}`

3. Обычный язык:
- Можно писать обычным текстом, роутер сам решит, нужен ли инструмент.

## Что такое pipeline
Pipeline — это цепочка шагов, где результат одного шага передается в следующий.

Пример:
- взять данные пользователя и репозитория на GitHub сервере,
- собрать сводку на utility сервере,
- сохранить результат.

## Что видно пользователю в интерфейсе
- Экран серверов MCP с возможностью подключить несколько серверов.
- Экран чата с индикатором:
  - какой инструмент выполняется сейчас,
  - какой pipeline идет,
  - какие шаги уже завершены.

## Где смотреть технические детали
Если нужен технический уровень документации:
- `app/codex_readme_app.md`
- `mcpserver/codex_readme_mcpserver.md`
- `all_challenge_readme.md` (сводный обзор по всем readme)


---

## 2) app/codex_readme_app.md

# codex_readme_app

Актуально для состояния проекта на **2026-03-16**.

## 1. Назначение модуля
`app` — основной Android-модуль ассистента. Он объединяет UI, доменную логику, хранилища и интеграции с LLM/MCP.

Ключевые подсистемы:
- профили, задачи, чаты (`Profile -> Task -> Chat`),
- модели памяти: краткосрочная (`messages`), рабочая (`WorkingMemory`), долговременная (`LongTerm`) и `Facts`,
- Task FSM (planning/execution/validation/done + pause/resume/cancel),
- MCP multi-server клиент и tool routing,
- orchestration pipeline для multi-step и cross-server инструментов,
- tracking-сценарии (фоновые задачи через WorkManager).

## 2. Критичные runtime-потоки
### 2.1 Поток отправки сообщения в чате
Приоритет обработки входного текста:
1. `/tool ...`
2. `/pipeline ...`
3. команды FSM (`pause`/`resume`/`cancel`)
4. natural routing через `NaturalLanguageToolRouter` (если MCP доступен)
5. обычный LLM chat flow (`EchoRepository.send`)

### 2.2 MCP multi-server flow
- `McpRepository` хранит runtime нескольких серверов одновременно.
- Для каждого сервера ведутся: `serverId`, URL, статус, список tools, ошибка.
- Агрегированный реестр tools используется chat-router и pipeline-orchestrator.
- Вызов tool маршрутизируется по `preferredServerId + toolName`; при неоднозначности возвращается ошибка/clarification.

### 2.3 Cross-server pipeline
`cross_server_github_report_flow` (в `McpPipelineCatalog`):
1. `[github] github_get_user`
2. `[github] github_get_repo`
3. `[github] github_list_repo_issues`
4. `[utility] summarize_github_report`
5. `[utility] save_summary_to_file`

### 2.4 UI-индикаторы tool-пайплайна
В `ChatUiState` и `ChatScreen` есть отдельный debug/status блок:
- `ToolWorkMode` (`IDLE` / `TOOL_CALL_IN_PROGRESS` / `PIPELINE_IN_PROGRESS`),
- текущий активный tool (`server/tool`),
- прогресс шагов pipeline,
- последние выполненные tools (`recentToolActivities`).

## 3. Память и FSM (связанные файлы)
- Working Memory: `WorkingMemoryManager`, patch-модель и write-plan валидация.
- LongTerm Memory: `LongTermMemoryManager`, режимы `MANUAL/AUTO` и patch-обновления.
- Task FSM: `TaskStateModels` + `TaskReducer` + `TaskFsmManager` + `TaskStateStore` + `TaskStateJson`.

## 4. MCP/DI-конфигурация (связанные файлы)
- `LocalMcpServerModule`: отдельные провайдеры для github/utility local серверов.
- `McpQualifiers`: `@GithubServer`, `@UtilityServer`.
- `McpModule`: Ktor HTTP client для MCP client SDK.

## 5. Полный каталог классов и методов по файлам
Ниже перечислены декларации (классы/интерфейсы/enum/функции) из каждого Kotlin-файла `app/src/main/java`.

### `app/src/main/java/com/example/vasganchalenge1/App.kt`
Application-класс для инициализации Hilt.

- `L8`: `class App : Application()`

### `app/src/main/java/com/example/vasganchalenge1/MainActivity.kt`
Главная Activity, хост Compose и навигации.

- `L11`: `class MainActivity : ComponentActivity() {`
- `L13`: `override fun onCreate(savedInstanceState: Bundle?) {`

### `app/src/main/java/com/example/vasganchalenge1/data/DataClass.kt`
Data/Domain файл (модели, репозитории, сервисы, менеджеры памяти, оркестраторы).

- `L7`: `data class ChatRequest(`
- `L15`: `data class Message(`
- `L20`: `data class ChatResponse(`
- `L25`: `data class Usage(`
- `L31`: `data class Choice(`
- `L35`: `data class RunMetric(`
- `L43`: `data class MemoryField(`
- `L48`: `enum class LongTermMode {`
- `L52`: `data class LongTermMemory(`
- `L60`: `data class LongTermMemoryPatch(`
- `L68`: `data class LongTermMemoryWritePlan(`
- `L74`: `enum class WorkingMemoryStatus {`
- `L78`: `data class WorkingMemoryState(`
- `L90`: `data class WorkingMemoryPatch(`
- `L106`: `data class WorkingMemoryWritePlan(`
- `L112`: `data class Profile(`
- `L122`: `data class TaskItem(`
- `L131`: `data class Chat(`
- `L146`: `data class UiChatMessage(`
- `L153`: `enum class Role { USER, ASSISTANT, TOOL }`
- `L157`: `private fun nextUiChatMessageId(): Long = uiChatMessageIdSeed.incrementAndGet()`

### `app/src/main/java/com/example/vasganchalenge1/data/mcp/AndroidSummaryStorageTools.kt`
Data/Domain файл (модели, репозитории, сервисы, менеджеры памяти, оркестраторы).

- `L17`: `class AndroidSummaryStorageTools @Inject constructor(`

### `app/src/main/java/com/example/vasganchalenge1/data/network/ApiModels.kt`
Data/Domain файл (модели, репозитории, сервисы, менеджеры памяти, оркестраторы).

- `L5`: `data class EchoRequest(`
- `L10`: `data class EchoResponse(`

### `app/src/main/java/com/example/vasganchalenge1/data/network/ApiService.kt`
Data/Domain файл (модели, репозитории, сервисы, менеджеры памяти, оркестраторы).

- `L8`: `interface ApiService {`
- `L10`: `suspend fun chatCompletion(`

### `app/src/main/java/com/example/vasganchalenge1/data/pipeline/McpPipelineModels.kt`
Модели pipeline, каталог пайплайнов и шагов.

- `L6`: `data class PipelineExecutionResult(`
- `L13`: `data class PipelineStepResult(`
- `L23`: `data class PipelineStepDefinition(`
- `L29`: `data class McpPipelineDescriptor(`
- `L40`: `object McpPipelineCatalog {`
- `L123`: `fun availableFor(toolNames: Set<String>): List<McpPipelineDescriptor> {`

### `app/src/main/java/com/example/vasganchalenge1/data/pipeline/McpPipelineOrchestrator.kt`
Оркестратор multi-step pipeline (в т.ч. cross-server).

- `L15`: `class McpPipelineOrchestrator @Inject constructor(`
- `L20`: `fun availablePipelines(availableTools: List<McpTool>): List<McpPipelineDescriptor> {`
- `L25`: `fun findPipeline(`
- `L32`: `suspend fun execute(`
- `L72`: `private suspend fun executeCrossServerGithubReportFlow(`
- `L90`: `fun push(step: PipelineStepResult) {`
- `L199`: `private suspend fun executeGithubUserTrackingFlow(`
- `L216`: `fun push(step: PipelineStepResult) {`
- `L295`: `private suspend fun executeStep(`
- `L340`: `private fun extractIssuesJson(rawStructured: String?): String {`
- `L347`: `private fun parseArgs(argumentsJson: String): JsonObject {`
- `L353`: `private fun JsonObject.string(key: String): String? {`
- `L357`: `private fun JsonObject.int(key: String): Int? {`
- `L361`: `private fun JsonObject.double(key: String): Double? {`
- `L365`: `private fun JsonObject.bool(key: String): Boolean? {`

### `app/src/main/java/com/example/vasganchalenge1/data/repositories/ChatHistoryRepository.kt`
Data/Domain файл (модели, репозитории, сервисы, менеджеры памяти, оркестраторы).

- `L19`: `class ChatHistoryRepository @Inject constructor(`
- `L40`: `suspend fun saveHistory(messages: List<UiChatMessage>) {`
- `L47`: `suspend fun clear() {`

### `app/src/main/java/com/example/vasganchalenge1/data/repositories/ChatStoreRepository.kt`
Data/Domain файл (модели, репозитории, сервисы, менеджеры памяти, оркестраторы).

- `L25`: `class ChatStoreRepository @Inject constructor(`
- `L39`: `suspend fun saveAll(profiles: List<Profile>) {`
- `L44`: `suspend fun createProfile(title: String, longTermMode: LongTermMode): Profile {`
- `L54`: `suspend fun createTask(profileId: String, title: String): TaskItem {`
- `L68`: `suspend fun createChat(taskId: String, title: String): Chat {`
- `L88`: `suspend fun deleteChat(chatId: String) {`
- `L105`: `suspend fun updateChat(updated: Chat) {`
- `L122`: `suspend fun createBranch(sourceChatId: String, fromMessageId: Long): Chat {`
- `L170`: `suspend fun getProfile(profileId: String): Profile? =`
- `L173`: `suspend fun updateProfileLongTerm(profileId: String, longTermMemory: LongTermMemory) {`
- `L186`: `suspend fun updateProfileInvariants(profileId: String, invariants: List<String>) {`
- `L200`: `suspend fun getProfileByChatId(chatId: String): Profile? =`
- `L205`: `suspend fun getTask(taskId: String): TaskItem? =`
- `L210`: `suspend fun getTaskByChatId(chatId: String): TaskItem? =`
- `L215`: `suspend fun updateTaskWorkingMemory(taskId: String, workingMemory: WorkingMemoryState) {`
- `L233`: `suspend fun getChat(chatId: String): Chat? =`

### `app/src/main/java/com/example/vasganchalenge1/data/repositories/EchoRepository.kt`
Data/Domain файл (модели, репозитории, сервисы, менеджеры памяти, оркестраторы).

- `L19`: `class EchoRepository  @Inject constructor(`
- `L33`: `suspend fun send(`
- `L100`: `suspend fun extractFacts(`
- `L143`: `suspend fun extractWorkingMemoryWritePlan(`
- `L226`: `suspend fun detectInvariantViolation(`
- `L269`: `suspend fun extractLongTermMemoryWritePlan(`
- `L320`: `private fun fallbackParseWorkingMemoryWritePlan(json: String): WorkingMemoryWritePlan? {`
- `L373`: `private fun fallbackParseLongTermMemoryWritePlan(json: String): LongTermMemoryWritePlan? {`
- `L403`: `data class DataResponse(val content: String?, val tokensIn: Int?, val tokenOut: Int?)`
- `L405`: `private fun workingMemoryStateToJson(state: WorkingMemoryState): String {`
- `L424`: `private fun longTermMemoryToJson(state: LongTermMemory): String {`
- `L440`: `private fun extractJsonObject(raw: String): String? {`
- `L447`: `private fun stringList(value: Any?): List<String> {`
- `L451`: `private fun stringMap(value: Any?): Map<String, String> {`
- `L459`: `private fun parseStatus(value: Any?): WorkingMemoryStatus? {`
- `L464`: `private fun escapeJson(value: String): String {`

### `app/src/main/java/com/example/vasganchalenge1/data/repositories/LongTermMemoryManager.kt`
Data/Domain файл (модели, репозитории, сервисы, менеджеры памяти, оркестраторы).

- `L12`: `class LongTermMemoryManager @Inject constructor(`
- `L15`: `suspend fun getState(profileId: String): LongTermMemory {`
- `L19`: `fun validateWritePlan(plan: LongTermMemoryWritePlan): ValidationResult {`
- `L32`: `suspend fun updateByPlan(profileId: String, plan: LongTermMemoryWritePlan): ValidationResult {`
- `L46`: `fun applyPatch(current: LongTermMemory, patch: LongTermMemoryPatch): LongTermMemory {`
- `L90`: `private fun LongTermMemoryPatch.isEffectivelyEmpty(): Boolean {`
- `L97`: `private fun String.trimTo280(): String = trim().take(280)`

### `app/src/main/java/com/example/vasganchalenge1/data/repositories/McpRepository.kt`
Единый multi-server MCP repository и server-aware routing tool-вызовов.

- `L44`: `enum class McpConnectionStatus {`
- `L51`: `data class McpTool(`
- `L60`: `data class ToolResult(`
- `L69`: `data class RegisteredMcpServer(`
- `L81`: `data class McpSharedState(`
- `L110`: `class McpRepository @Inject constructor(`
- `L158`: `suspend fun connect(serverUrl: String): Result<List<McpTool>> {`
- `L167`: `suspend fun connectServer(`
- `L222`: `suspend fun connectLocal(): Result<List<McpTool>> = connectLocal(MCP_SERVER_ID_GITHUB)`
- `L224`: `suspend fun connectLocal(serverId: String): Result<List<McpTool>> = runCatching {`
- `L273`: `fun disconnect() {`
- `L277`: `fun disconnectAll() {`
- `L281`: `fun disconnect(serverId: String) {`
- `L302`: `suspend fun listTools(): Result<List<McpTool>> {`
- `L306`: `suspend fun listTools(serverId: String?): Result<List<McpTool>> = runCatching {`
- `L331`: `suspend fun callTool(`
- `L396`: `private suspend fun refreshTools(runtime: ServerRuntime): List<McpTool> {`
- `L408`: `private fun resolveToolServer(name: String, preferredServerId: String?): Result<ServerRuntime> {`
- `L447`: `private suspend fun listToolsViaRemoteMcp(`
- `L478`: `private suspend fun callToolViaRemoteMcp(`
- `L517`: `private fun listToolsViaInProcess(`
- `L543`: `private suspend fun callToolViaInProcess(`
- `L566`: `private fun parseArgumentsJson(argumentsJson: String): Map<String, Any?> {`
- `L573`: `private fun JsonElement.toAnyValue(): Any? {`
- `L587`: `private fun extractRequiredParams(schemaJson: String): List<String> {`
- `L597`: `private fun emitState() {`
- `L643`: `private fun Map<String, Any?>.toJsonObject(): JsonObject {`
- `L651`: `private fun Any?.toJsonElement(): JsonElement {`

### `app/src/main/java/com/example/vasganchalenge1/data/repositories/SettingsRepository.kt`
Data/Domain файл (модели, репозитории, сервисы, менеджеры памяти, оркестраторы).

- `L18`: `data class AppSettings(`
- `L29`: `object ContextMode {`
- `L35`: `class SettingsRepository @Inject constructor(`
- `L62`: `suspend fun save(settings: AppSettings) {`

### `app/src/main/java/com/example/vasganchalenge1/data/repositories/WorkingMemoryManager.kt`
Data/Domain файл (модели, репозитории, сервисы, менеджеры памяти, оркестраторы).

- `L10`: `sealed interface ValidationResult {`
- `L12`: `data class Invalid(val reason: String) : ValidationResult`
- `L16`: `class WorkingMemoryManager @Inject constructor(`
- `L19`: `suspend fun getState(taskId: String): WorkingMemoryState {`
- `L23`: `fun validateWritePlan(plan: WorkingMemoryWritePlan): ValidationResult {`
- `L36`: `suspend fun updateByPlan(taskId: String, plan: WorkingMemoryWritePlan): ValidationResult {`
- `L46`: `fun applyPatch(`
- `L101`: `suspend fun buildWorkingContext(taskId: String): String {`
- `L143`: `private fun normalizeList(`
- `L163`: `private fun normalizeArtifacts(`
- `L197`: `private fun WorkingMemoryPatch.isEffectivelyEmpty(): Boolean {`
- `L212`: `private fun String.trimTo280(): String = trim().take(280)`

### `app/src/main/java/com/example/vasganchalenge1/data/taskfsm/TaskFsmManager.kt`
Data/Domain файл (модели, репозитории, сервисы, менеджеры памяти, оркестраторы).

- `L7`: `class TaskFsmManager @Inject constructor(`
- `L11`: `suspend fun getOrCreate(taskId: String): TaskState {`
- `L19`: `suspend fun dispatch(taskId: String, event: TaskEvent): TaskState {`
- `L26`: `suspend fun reset(taskId: String): TaskState {`
- `L32`: `suspend fun runPendingTool(taskId: String): TaskState {`
- `L37`: `private suspend fun runPendingTool(state: TaskState): TaskState {`

### `app/src/main/java/com/example/vasganchalenge1/data/taskfsm/TaskReducer.kt`
Data/Domain файл (модели, репозитории, сервисы, менеджеры памяти, оркестраторы).

- `L3`: `object TaskReducer {`
- `L4`: `fun reduce(state: TaskState, event: TaskEvent): TaskState {`
- `L25`: `private fun reduceUserMessage(`
- `L72`: `private fun reduceToolResult(`
- `L140`: `private fun extractRequirements(text: String, known: Map<String, String>): Map<String, String> {`
- `L179`: `private fun missingRequiredFields(fields: Map<String, String>): List<String> {`

### `app/src/main/java/com/example/vasganchalenge1/data/taskfsm/TaskStateJson.kt`
Data/Domain файл (модели, репозитории, сервисы, менеджеры памяти, оркестраторы).

- `L6`: `class TaskStateJson(moshi: Moshi) {`
- `L15`: `fun toJson(state: TaskState): String {`
- `L28`: `fun fromJson(json: String): TaskState? {`
- `L47`: `private fun encodeStep(step: TaskStep): Map<String, Any?> {`
- `L78`: `private fun decodeStep(step: Map<*, *>?): TaskStep? {`
- `L107`: `private fun encodeExpectedAction(action: ExpectedAction): Map<String, Any?> {`
- `L128`: `private fun decodeExpectedAction(action: Map<*, *>?): ExpectedAction? {`
- `L149`: `private fun stringList(value: Any?): List<String> {`
- `L153`: `private fun stringMap(value: Any?): Map<String, String> {`

### `app/src/main/java/com/example/vasganchalenge1/data/taskfsm/TaskStateModels.kt`
Data/Domain файл (модели, репозитории, сервисы, менеджеры памяти, оркестраторы).

- `L3`: `enum class TaskPhase {`
- `L7`: `enum class TaskStatus {`
- `L11`: `sealed interface TaskStep {`
- `L14`: `data class CollectRequirements(`
- `L21`: `data class CreatePlan(`
- `L27`: `data class ImplementFeature(`
- `L34`: `data class RunChecks(`
- `L40`: `data class Finished(`
- `L47`: `sealed interface ExpectedAction {`
- `L50`: `data class UserReply(`
- `L57`: `data class ToolCall(`
- `L64`: `data class Idle(`
- `L71`: `data class TaskState(`
- `L80`: `sealed interface TaskEvent {`
- `L83`: `data class UserMessage(`
- `L88`: `data class PauseRequested(`
- `L92`: `data class ResumeRequested(`
- `L96`: `data class CancelRequested(`
- `L100`: `data class ResetRequested(`
- `L104`: `data class ToolResult(`
- `L112`: `fun initialTaskState(`

### `app/src/main/java/com/example/vasganchalenge1/data/taskfsm/TaskStateStore.kt`
Data/Domain файл (модели, репозитории, сервисы, менеджеры памяти, оркестраторы).

- `L17`: `class TaskStateStore @Inject constructor(`
- `L23`: `suspend fun get(taskId: String): TaskState? {`
- `L31`: `suspend fun save(state: TaskState) {`
- `L38`: `suspend fun clear(taskId: String) {`
- `L45`: `private fun keyFor(taskId: String): String = "current_task_$taskId"`

### `app/src/main/java/com/example/vasganchalenge1/data/taskfsm/TaskToolRunner.kt`
Data/Domain файл (модели, репозитории, сервисы, менеджеры памяти, оркестраторы).

- `L6`: `interface TaskToolRunner {`
- `L7`: `suspend fun run(toolName: String, hint: String, state: TaskState): String`
- `L11`: `class DefaultTaskToolRunner @Inject constructor() : TaskToolRunner {`

### `app/src/main/java/com/example/vasganchalenge1/data/toolrouting/NaturalLanguageToolRouter.kt`
LLM-based router + rule-based fallback для tool/pipeline intent.

- `L16`: `class NaturalLanguageToolRouter @Inject constructor(`
- `L22`: `suspend fun resolve(`
- `L65`: `private fun fallbackRuleBased(`
- `L180`: `private fun isSummaryPipelineRequest(normalized: String): Boolean {`
- `L199`: `private fun isTrackingPipelineRequest(normalized: String): Boolean {`
- `L220`: `private fun isScheduleTrackingRequest(normalized: String): Boolean {`
- `L236`: `private fun isStatsRequest(normalized: String): Boolean {`
- `L247`: `private fun isStopTrackingRequest(normalized: String): Boolean {`
- `L264`: `private fun extractUsername(text: String): String? {`
- `L274`: `private fun extractRepoName(text: String): String? {`
- `L289`: `private fun extractIntervalSeconds(text: String): Int? {`
- `L311`: `private fun extractDurationHours(text: String): Int? {`
- `L318`: `private fun extractStatsPeriod(normalized: String): String? {`

### `app/src/main/java/com/example/vasganchalenge1/data/toolrouting/ToolResolution.kt`
Data/Domain файл (модели, репозитории, сервисы, менеджеры памяти, оркестраторы).

- `L3`: `sealed interface ToolResolution {`
- `L6`: `data class ToolCall(`
- `L12`: `data class PipelineCall(`
- `L17`: `data class ClarificationNeeded(`

### `app/src/main/java/com/example/vasganchalenge1/data/toolrouting/ToolRouterPromptBuilder.kt`
Сборка system prompt для LLM router на основе текущих tools/pipelines.

- `L6`: `class ToolRouterPromptBuilder {`
- `L8`: `fun buildSystemPrompt(`
- `L83`: `private fun escape(value: String): String {`

### `app/src/main/java/com/example/vasganchalenge1/data/toolrouting/ToolRouterResponseParser.kt`
Парсинг и валидация JSON-ответа router-модели.

- `L13`: `class ToolRouterResponseParser(`
- `L17`: `fun parse(`
- `L39`: `private fun parseToolCall(root: JsonObject, availableTools: List<McpTool>): ToolResolution {`
- `L74`: `private fun parsePipelineCall(`
- `L99`: `private fun isMissingArgument(value: JsonElement?): Boolean {`
- `L107`: `private fun extractJsonObject(raw: String): String? {`

### `app/src/main/java/com/example/vasganchalenge1/data/tracking/AndroidGithubTrackingTools.kt`
Data/Domain файл (модели, репозитории, сервисы, менеджеры памяти, оркестраторы).

- `L11`: `class AndroidGithubTrackingTools @Inject constructor(`

### `app/src/main/java/com/example/vasganchalenge1/data/tracking/GithubTrackingService.kt`
Сервис планирования и сбора tracking-метрик через WorkManager и локальное хранилище.

- `L18`: `class GithubTrackingService private constructor(`
- `L35`: `suspend fun scheduleTracking(`
- `L157`: `suspend fun getStats(`
- `L225`: `suspend fun stopTracking(trackingId: String?): TrackingToolResult = withContext(Dispatchers.IO) {`
- `L265`: `suspend fun collectSnapshotForWorker(trackingId: String): WorkerCollectionResult = withContext(Dispatchers.IO) {`
- `L324`: `private suspend fun resolveTrackingJob(`
- `L337`: `private suspend fun collectMetric(username: String, metric: String): Pair<Double, Map<String, Any?>> {`
- `L370`: `private fun scheduleWorkNow(workName: String, trackingId: String) {`
- `L383`: `private fun scheduleNextRun(workName: String, trackingId: String, delaySeconds: Int) {`
- `L398`: `private fun formatSigned(value: Double): String {`
- `L402`: `private fun formatNumber(value: Double): String {`
- `L414`: `fun getInstance(context: Context): GithubTrackingService {`
- `L427`: `enum class WorkerCollectionResult {`

### `app/src/main/java/com/example/vasganchalenge1/data/tracking/GithubTrackingWorker.kt`
Data/Domain файл (модели, репозитории, сервисы, менеджеры памяти, оркестраторы).

- `L8`: `class GithubTrackingWorker(`

### `app/src/main/java/com/example/vasganchalenge1/data/tracking/TrackingDao.kt`
Data/Domain файл (модели, репозитории, сервисы, менеджеры памяти, оркестраторы).

- `L9`: `interface TrackingJobDao {`
- `L11`: `suspend fun upsert(job: TrackingJobEntity)`
- `L14`: `suspend fun findById(trackingId: String): TrackingJobEntity?`
- `L21`: `suspend fun findActiveByUsernameMetric(`
- `L32`: `suspend fun findAnyActive(activeStatus: String = TrackingStatus.ACTIVE): TrackingJobEntity?`
- `L35`: `suspend fun findLatestByUsername(username: String): TrackingJobEntity?`
- `L38`: `suspend fun findLatestAny(): TrackingJobEntity?`
- `L41`: `suspend fun updateStatus(trackingId: String, status: String, endedAt: Long?)`
- `L44`: `suspend fun updateLastCollectedAt(trackingId: String, lastCollectedAt: Long)`
- `L48`: `interface TrackingSnapshotDao {`
- `L50`: `suspend fun insert(snapshot: TrackingSnapshotEntity)`
- `L57`: `suspend fun findByTrackingSince(trackingId: String, fromTs: Long): List<TrackingSnapshotEntity>`
- `L60`: `suspend fun countByTracking(trackingId: String): Int`
- `L63`: `suspend fun findLatest(trackingId: String): TrackingSnapshotEntity?`

### `app/src/main/java/com/example/vasganchalenge1/data/tracking/TrackingDatabase.kt`
Data/Domain файл (модели, репозитории, сервисы, менеджеры памяти, оркестраторы).

- `L21`: `fun getInstance(context: Context): TrackingDatabase {`

### `app/src/main/java/com/example/vasganchalenge1/data/tracking/TrackingEntities.kt`
Data/Domain файл (модели, репозитории, сервисы, менеджеры памяти, оркестраторы).

- `L14`: `data class TrackingJobEntity(`
- `L44`: `data class TrackingSnapshotEntity(`

### `app/src/main/java/com/example/vasganchalenge1/data/tracking/TrackingModels.kt`
Data/Domain файл (модели, репозитории, сервисы, менеджеры памяти, оркестраторы).

- `L3`: `object TrackingMetric {`
- `L10`: `object TrackingStatus {`

### `app/src/main/java/com/example/vasganchalenge1/data/tracking/TrackingStatsAggregator.kt`
Data/Domain файл (модели, репозитории, сервисы, менеджеры памяти, оркестраторы).

- `L3`: `data class TrackingPoint(`
- `L8`: `data class AggregatedTrackingStats(`
- `L20`: `fun aggregateTrackingPoints(points: List<TrackingPoint>): AggregatedTrackingStats? {`
- `L39`: `fun parsePeriodToMillis(period: String?): Long {`
- `L60`: `fun isTrackingExpired(startedAt: Long, durationHours: Int, now: Long): Boolean {`

### `app/src/main/java/com/example/vasganchalenge1/di/JsonModule.kt`
DI-конфигурация (Hilt module/qualifier/provider).

- `L13`: `object JsonModule {`
- `L17`: `fun provideMoshi(): Moshi {`

### `app/src/main/java/com/example/vasganchalenge1/di/LocalMcpServerModule.kt`
DI-провайдеры двух локальных MCP-серверов: github и utility.

- `L17`: `object LocalMcpServerModule {`
- `L22`: `fun provideGithubMcpToolRegistry(`
- `L31`: `fun provideUtilityMcpToolRegistry(`
- `L40`: `fun provideGithubLocalMcpServerManager(`
- `L54`: `fun provideUtilityLocalMcpServerManager(`

### `app/src/main/java/com/example/vasganchalenge1/di/McpModule.kt`
DI-конфигурация (Hilt module/qualifier/provider).

- `L15`: `object McpModule {`
- `L19`: `fun provideMcpHttpClient(): HttpClient {`

### `app/src/main/java/com/example/vasganchalenge1/di/McpQualifiers.kt`
Hilt qualifiers для разделения зависимостей github/utility серверов.

- Декларации не найдены (возможно файл содержит только импорты/константы).

### `app/src/main/java/com/example/vasganchalenge1/di/NetworkModule.kt`
DI-конфигурация (Hilt module/qualifier/provider).

- `L19`: `object NetworkModule {`
- `L26`: `fun provideOkHttp(): OkHttpClient {`
- `L48`: `fun provideRetrofit(okHttp: OkHttpClient, moshi: Moshi): Retrofit =`
- `L57`: `fun provideApi(retrofit: Retrofit): ApiService =`

### `app/src/main/java/com/example/vasganchalenge1/di/SummaryToolsModule.kt`
DI-конфигурация (Hilt module/qualifier/provider).

- Декларации не найдены (возможно файл содержит только импорты/константы).

### `app/src/main/java/com/example/vasganchalenge1/di/TaskFsmModule.kt`
DI-конфигурация (Hilt module/qualifier/provider).

- Декларации не найдены (возможно файл содержит только импорты/константы).

### `app/src/main/java/com/example/vasganchalenge1/di/TrackingToolsModule.kt`
DI-конфигурация (Hilt module/qualifier/provider).

- Декларации не найдены (возможно файл содержит только импорты/константы).

### `app/src/main/java/com/example/vasganchalenge1/ui/ChatScreen.kt`
Compose UI чата, включая Task Debug Panel и MCP Tooling Block.

- `L69`: `fun MainRoute(`
- `L97`: `fun ChatScreen(`
- `L246`: `private fun McpDebugHeader(`
- `L263`: `private fun AgentToolingBlock(`
- `L321`: `private fun TaskDebugPanel(`
- `L384`: `private fun TaskPhaseStepper(`
- `L424`: `private fun PhaseStepChip(`
- `L455`: `private fun PhaseConnector(`
- `L471`: `private fun phaseLabel(phase: TaskPhase): String {`
- `L503`: `private fun MetricsHeader(metrics: List<RunMetric>) {`
- `L523`: `private fun ChatBubble(`
- `L589`: `private fun describeStep(step: TaskStep): String {`
- `L599`: `private fun describeExpectedAction(action: ExpectedAction): String {`
- `L607`: `private fun formatTimestamp(timestamp: Long): String {`

### `app/src/main/java/com/example/vasganchalenge1/ui/ChatToolRouting.kt`
UI/Presentation файл (Compose и/или ViewModel).

- Декларации не найдены (возможно файл содержит только импорты/константы).

### `app/src/main/java/com/example/vasganchalenge1/ui/ChatUiState.kt`
UI/Presentation файл (Compose и/или ViewModel).

- `L9`: `enum class ToolWorkMode {`
- `L15`: `data class McpServerDebugInfo(`
- `L22`: `data class PipelineStepDebugInfo(`
- `L31`: `data class ChatUiState(`

### `app/src/main/java/com/example/vasganchalenge1/ui/ChatViewModel.kt`
Основная оркестрация экрана чата: /tool, /pipeline, natural routing, обычный chat, FSM, WM/LTM.

- `L39`: `class ChatViewModel @Inject constructor(`
- `L112`: `fun onInputChange(v: String) {`
- `L116`: `fun createBranchFrom(messageId: Long, onDone: (String) -> Unit) {`
- `L123`: `fun pauseTask() {`
- `L127`: `fun resumeTask() {`
- `L131`: `fun cancelTask() {`
- `L135`: `fun resetTask() {`
- `L142`: `fun onSendClick() {`
- `L248`: `private fun onRegularChatMessage(text: String) {`
- `L441`: `private fun onRouterClarification(userText: String, clarification: String) {`
- `L462`: `private fun onToolCommand(rawText: String, command: McpToolCommand) {`
- `L544`: `private fun onPipelineCommand(rawText: String, command: McpPipelineCommand) {`
- `L657`: `private fun buildPipelineChatText(result: PipelineExecutionResult): String {`
- `L676`: `private fun buildToolActivity(`
- `L685`: `private fun dispatchTaskEvent(event: TaskEvent) {`
- `L704`: `private suspend fun persistChat(`
- `L722`: `private suspend fun updateFactsIfNeeded(`
- `L751`: `private fun buildRequestHistory(`
- `L767`: `private fun String.toTaskCommand(): TaskCommand? {`
- `L776`: `private fun buildLongTermMemoryJson(longTermMemory: LongTermMemory): String {`
- `L802`: `private fun jsonEntry(key: String, value: String): String {`
- `L806`: `private fun escapeJson(value: String): String {`
- `L821`: `private fun buildTaskPhasePrompt(taskState: TaskState?): String {`
- `L846`: `data class PricePer1M(val input: Double, val output: Double)`
- `L854`: `private fun calcCostUsd(model: String, promptTokens: Int, completionTokens: Int): Double {`

### `app/src/main/java/com/example/vasganchalenge1/ui/FactsScreen.kt`
UI/Presentation файл (Compose и/или ViewModel).

- `L24`: `fun FactsScreen(`
- `L83`: `private fun MemoryCard(title: String, value: String) {`
- `L96`: `private fun buildLongTermMemoryBlock(`
- `L119`: `private fun buildInvariantBlock(invariants: List<String>): String {`

### `app/src/main/java/com/example/vasganchalenge1/ui/branches/BranchesScreen.kt`
UI/Presentation файл (Compose и/или ViewModel).

- `L25`: `fun BranchesScreen(`
- `L59`: `private fun BranchCard(`

### `app/src/main/java/com/example/vasganchalenge1/ui/branches/BranchesViewModel.kt`
UI/Presentation файл (Compose и/или ViewModel).

- `L14`: `data class BranchesUiState(`
- `L21`: `class BranchesViewModel @Inject constructor(`

### `app/src/main/java/com/example/vasganchalenge1/ui/chats/ChatListScreen.kt`
UI/Presentation файл (Compose и/или ViewModel).

- `L26`: `fun ChatListScreen(`

### `app/src/main/java/com/example/vasganchalenge1/ui/chats/ChatListViewModel.kt`
UI/Presentation файл (Compose и/или ViewModel).

- `L13`: `data class ChatListUiState(`
- `L22`: `class ChatListViewModel @Inject constructor(`
- `L46`: `fun createChat(onDone: (String) -> Unit) {`
- `L53`: `fun deleteChat(chatId: String) {`

### `app/src/main/java/com/example/vasganchalenge1/ui/mcp/McpServerScreen.kt`
UI экрана MCP серверов (несколько серверов одновременно).

- `L26`: `fun McpServerScreen(`

### `app/src/main/java/com/example/vasganchalenge1/ui/mcp/McpServerViewModel.kt`
ViewModel экрана MCP серверов (multi-connect / toggle / debug tool calls).

- `L18`: `enum class McpConnectionStatusUi {`
- `L22`: `data class McpServerItemUi(`
- `L32`: `data class McpServerUiState(`
- `L41`: `class McpServerViewModel @Inject constructor(`
- `L74`: `fun setServerUrl(url: String) {`
- `L78`: `fun connectGithubLocal() {`
- `L82`: `fun connectUtilityLocal() {`
- `L86`: `fun connectAndLoadTools(serverUrl: String = _state.value.serverUrl) {`
- `L106`: `fun toggleServer(serverId: String) {`
- `L123`: `fun callGithubGetUser(username: String = "Vasgan") {`
- `L133`: `fun callGithubGetRepo(owner: String = "Vasgan", repo: String = "ai_chalenge_1") {`
- `L144`: `private fun connectLocal(serverId: String) {`
- `L153`: `private fun callTool(serverId: String, toolName: String, argsJson: String) {`
- `L170`: `private fun buildRemoteServerId(url: String): String {`
- `L175`: `private fun McpConnectionStatus.toUiStatus(): McpConnectionStatusUi {`

### `app/src/main/java/com/example/vasganchalenge1/ui/navigation/AppNavGraph.kt`
UI/Presentation файл (Compose и/или ViewModel).

- `L31`: `fun AppNavGraph() {`

### `app/src/main/java/com/example/vasganchalenge1/ui/navigation/Routes.kt`
UI/Presentation файл (Compose и/или ViewModel).

- `L3`: `object Routes {`
- `L14`: `fun profileSettings(profileId: String) = "$ProfileSettings/$profileId"`
- `L15`: `fun tasks(profileId: String) = "$Tasks/$profileId"`
- `L16`: `fun chatList(taskId: String) = "$ChatList/$taskId"`
- `L17`: `fun chat(chatId: String) = "$Chat/$chatId"`
- `L18`: `fun settings(chatId: String) = "$Settings/$chatId"`
- `L19`: `fun facts(chatId: String) = "$Facts/$chatId"`
- `L20`: `fun branches(chatId: String) = "$Branches/$chatId"`
- `L21`: `fun mcpServer(chatId: String) = "$McpServer/$chatId"`

### `app/src/main/java/com/example/vasganchalenge1/ui/profiles/ProfileListScreen.kt`
UI/Presentation файл (Compose и/или ViewModel).

- `L34`: `fun ProfileListScreen(`
- `L147`: `private fun LongTermModeOption(`

### `app/src/main/java/com/example/vasganchalenge1/ui/profiles/ProfileListViewModel.kt`
UI/Presentation файл (Compose и/или ViewModel).

- `L13`: `data class ProfileListUiState(`
- `L18`: `class ProfileListViewModel @Inject constructor(`
- `L32`: `fun createProfile(title: String, longTermMode: LongTermMode, onDone: (String) -> Unit) {`

### `app/src/main/java/com/example/vasganchalenge1/ui/profiles/ProfileSettingsScreen.kt`
UI/Presentation файл (Compose и/или ViewModel).

- `L25`: `fun ProfileSettingsScreen(`

### `app/src/main/java/com/example/vasganchalenge1/ui/profiles/ProfileSettingsViewModel.kt`
UI/Presentation файл (Compose и/или ViewModel).

- `L16`: `data class EditableMemoryField(`
- `L22`: `data class EditableInvariant(`
- `L27`: `data class ProfileSettingsUiState(`
- `L39`: `class ProfileSettingsViewModel @Inject constructor(`
- `L68`: `fun setProfileDescription(value: String) {`
- `L73`: `fun setCommunicationLanguage(value: String) {`
- `L78`: `fun addCustomField() {`
- `L85`: `fun updateCustomFieldKey(id: Long, value: String) {`
- `L94`: `fun updateCustomFieldValue(id: Long, value: String) {`
- `L103`: `fun removeCustomField(id: Long) {`
- `L110`: `fun addInvariant() {`
- `L116`: `fun updateInvariant(id: Long, value: String) {`
- `L124`: `fun removeInvariant(id: Long) {`
- `L130`: `fun save(onDone: () -> Unit) {`

### `app/src/main/java/com/example/vasganchalenge1/ui/settings/SettingsScreen.kt`
UI/Presentation файл (Compose и/или ViewModel).

- `L30`: `fun SettingsScreen(`
- `L192`: `private fun contextModeLabel(mode: String): String =`

### `app/src/main/java/com/example/vasganchalenge1/ui/settings/SettingsViewModel.kt`
UI/Presentation файл (Compose и/или ViewModel).

- `L18`: `data class SettingsUiState(`
- `L31`: `class SettingsViewModel @Inject constructor(`
- `L64`: `fun setModel(v: String) = _state.update { it.copy(model = v) }`
- `L65`: `fun setEnabled(v: Boolean) = _state.update { it.copy(enabled = v) }`
- `L66`: `fun setContextMode(v: String) = _state.update {`
- `L69`: `fun setFormat(v: String) = _state.update { it.copy(format = v) }`
- `L70`: `fun setLengthLimit(v: String) = _state.update { it.copy(lengthLimit = v) }`
- `L71`: `fun setStopSequence(v: String) = _state.update { it.copy(stopSequence = v) }`
- `L72`: `fun setMaxTokensText(v: String) = _state.update { it.copy(maxTokensText = v.filter { ch -> ch.isDigit() }) }`
- `L73`: `fun setTemperature(v: String) = _state.update { it.copy(temperature = v) }`
- `L74`: `fun save(onDone: () -> Unit) {`

### `app/src/main/java/com/example/vasganchalenge1/ui/tasks/TaskListScreen.kt`
UI/Presentation файл (Compose и/или ViewModel).

- `L32`: `fun TaskListScreen(`

### `app/src/main/java/com/example/vasganchalenge1/ui/tasks/TaskListViewModel.kt`
UI/Presentation файл (Compose и/или ViewModel).

- `L13`: `data class TaskListUiState(`
- `L20`: `class TaskListViewModel @Inject constructor(`
- `L42`: `fun createTask(title: String, onDone: (String) -> Unit) {`



---

## 3) mcpserver/codex_readme_mcpserver.md

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


