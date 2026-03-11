# codex_readme_app

## 1. Назначение модуля
Модуль `app` — Android-клиент ассистента на Jetpack Compose + Hilt.

Ключевые функции:
- профили/задачи/чаты (иерархия: Profile -> Task -> Chat),
- отправка сообщений в LLM,
- контекстная память (`Facts`, `WorkingMemory`, `LongTerm`),
- Task FSM (planning/execution/validation/done),
- MCP-клиент + интеграция tool-вызовов в чат,
- ветвление диалогов.

---

## 2. Архитектура (кратко)
- `ui/*` — Compose-экраны и ViewModel.
- `data/*` — модели, репозитории, memory-менеджеры, FSM, tool-routing.
- `di/*` — Hilt-модули (Moshi/Retrofit/HttpClient/TaskToolRunner/Local MCP server manager).
- Навигация: `ui/navigation/AppNavGraph.kt` + `Routes.kt`.
- Локальное хранение: DataStore (profiles/tasks/chats/settings/FSM).

---

## 3. Точки входа

### `/app/src/main/java/com/example/vasganchalenge1/App.kt`
**Класс**: `App : Application`
- Роль: инициализация Hilt (`@HiltAndroidApp`).
- Методы: нет.

### `/app/src/main/java/com/example/vasganchalenge1/MainActivity.kt`
**Класс**: `MainActivity : ComponentActivity`
- `onCreate(savedInstanceState)` — поднимает Compose UI, оборачивает в `MaterialTheme`, запускает `AppNavGraph()`.
## 4. Data-модели

### `/app/src/main/java/com/example/vasganchalenge1/data/DataClass.kt`
**Модели API**:
- `ChatRequest`, `Message`, `ChatResponse`, `Usage`, `Choice`.
- 
примеры запуска трекинга:
Включи трекинг звезд для пользователя octocat каждые 0.5 минуты на 1 час
Покажи статистику по звездам
Останови сбор статистики звезд

- **Модели метрик/памяти**:
- `RunMetric` — метрики прогона.
- `MemoryField` — произвольная пара key/value для LongTerm.
- `LongTermMode` (`MANUAL`, `AUTO`).
- `LongTermMemory`, `LongTermMemoryPatch`, `LongTermMemoryWritePlan`.
- `WorkingMemoryStatus` (`NEW`, `IN_PROGRESS`, `BLOCKED`, `DONE`).
- `WorkingMemoryState`, `WorkingMemoryPatch`, `WorkingMemoryWritePlan`.

**Модели домена приложения**:
- `Profile`, `TaskItem`, `Chat`, `UiChatMessage`, `Role` (`USER`, `ASSISTANT`, `TOOL`).

Методы: отсутствуют (только data/enum/sealed модели).

### `/app/src/main/java/com/example/vasganchalenge1/data/network/ApiModels.kt`
- `EchoRequest`, `EchoResponse` (вспомогательные модели, сейчас не основной путь).
- Методы: отсутствуют.

### `/app/src/main/java/com/example/vasganchalenge1/data/network/ApiService.kt`
**Интерфейс**: `ApiService`
- `chatCompletion(request)` — POST `v1/chat/completions`.

---

## 5. Репозитории и менеджеры памяти

### `/app/src/main/java/com/example/vasganchalenge1/data/repositories/SettingsRepository.kt`
**Модели/константы**:
- `AppSettings`
- `ContextMode` (`LAST_10`, `FACTS`)

**Класс**: `SettingsRepository`
- `settingsFlow` — поток настроек из DataStore.
- `save(settings)` — сохраняет настройки.

### `/app/src/main/java/com/example/vasganchalenge1/data/repositories/ChatHistoryRepository.kt`
**Класс**: `ChatHistoryRepository`
- `historyFlow` — история сообщений из DataStore по ключу `chat_history`.
- `saveHistory(messages)` — сохранение истории.
- `clear()` — очистка истории.

### `/app/src/main/java/com/example/vasganchalenge1/data/repositories/ChatStoreRepository.kt`
**Класс**: `ChatStoreRepository`

Основные операции:
- `profilesFlow` — поток всех профилей.
- `saveAll(profiles)` — полная перезапись состояния.
- `createProfile(title, longTermMode)` — создать профиль.
- `createTask(profileId, title)` — создать задачу в профиле.
- `createChat(taskId, title)` — создать чат в задаче.
- `deleteChat(chatId)` — удалить чат.
- `updateChat(updated)` — обновить чат.
- `createBranch(sourceChatId, fromMessageId)` — создать ветку от checkpoint-сообщения.
- `getProfile(profileId)`
- `updateProfileLongTerm(profileId, longTermMemory)`
- `updateProfileInvariants(profileId, invariants)`
- `getProfileByChatId(chatId)`
- `getTask(taskId)`
- `getTaskByChatId(chatId)`
- `updateTaskWorkingMemory(taskId, workingMemory)`
- `getChat(chatId)`

### `/app/src/main/java/com/example/vasganchalenge1/data/repositories/EchoRepository.kt`
**Класс**: `EchoRepository`

Основные методы:
- `send(settings, history, facts, longTermMemoryJson, invariants, workingContext, taskPhasePrompt)`
  - собирает system prompt (LongTerm + invariants + WM + phase + facts),
  - отправляет в LLM,
  - возвращает `DataResponse`.
- `extractFacts(currentFacts, chunk, settings)` — обновляет Facts по старым сообщениям.
- `extractWorkingMemoryWritePlan(settings, currentState, userMessage, assistantMessage)` — извлекает JSON-план обновления WM.
- `detectInvariantViolation(settings, invariants, assistantMessage)` — проверка нарушения инвариантов.
- `extractLongTermMemoryWritePlan(settings, currentState, userMessage, assistantMessage)` — план обновления LongTerm.

Внутренние методы/хелперы:
- `fallbackParseWorkingMemoryWritePlan(json)`
- `fallbackParseLongTermMemoryWritePlan(json)`
- top-level: `workingMemoryStateToJson`, `longTermMemoryToJson`, `extractJsonObject`, `stringList`, `stringMap`, `parseStatus`, `escapeJson`.

**Модель**: `DataResponse(content, tokensIn, tokenOut)`.

### `/app/src/main/java/com/example/vasganchalenge1/data/repositories/WorkingMemoryManager.kt`
**Тип**: `ValidationResult` (`Valid`, `Invalid(reason)`).

**Класс**: `WorkingMemoryManager`
- `getState(taskId)` — получить WM задачи.
- `validateWritePlan(plan)` — валидация confidence/reason/non-empty patch.
- `updateByPlan(taskId, plan)` — применить patch и сохранить.
- `applyPatch(current, patch)` — детерминированное применение patch.
- `buildWorkingContext(taskId)` — сбор компактного `[WORKING_MEMORY]...` блока (лимит 1500).

Внутренние методы:
- `normalizeList(...)` — нормализация списков + FIFO по лимиту.
- `normalizeArtifacts(...)` — нормализация artifacts + FIFO.
- top-level extension: `WorkingMemoryPatch.isEffectivelyEmpty()`, `String.trimTo280()`.

### `/app/src/main/java/com/example/vasganchalenge1/data/repositories/LongTermMemoryManager.kt`
**Класс**: `LongTermMemoryManager`
- `getState(profileId)` — получить LongTerm профиля.
- `validateWritePlan(plan)` — валидация плана записи.
- `updateByPlan(profileId, plan)` — применяет только в `LongTermMode.AUTO`.
- `applyPatch(current, patch)` — применение patch (в т.ч. clearAll и лимиты).

Внутренние методы:
- `LongTermMemoryPatch.isEffectivelyEmpty()`
- `String.trimTo280()`.

### `/app/src/main/java/com/example/vasganchalenge1/data/repositories/McpRepository.kt`
**Модели**:
- `McpConnectionStatus`
- `McpTool(name, description, inputSchemaJson, requiredParams)`
- `ToolResult(text, structuredJson, isError)`
- `McpSharedState(serverUrl, connectionStatus, tools, localServerStatus, localServerUrl, error)`

**Класс**: `McpRepository`
- `state: StateFlow<McpSharedState>` — единый source of truth MCP состояния.
- `connect(serverUrl)` — подключение к remote/local endpoint + первичная загрузка tools.
- `connectLocal()` — старт локального сервера + connect.
- `disconnect()` — дисконнект и stop локального сервера.
- `listTools()` — повторная загрузка инструментов.
- `callTool(name, argumentsJson)` — вызов инструмента через текущую сессию.

Внутренние методы:
- `listToolsViaRemoteMcp(serverUrl)`
- `callToolViaRemoteMcp(serverUrl, toolName, arguments)`
- `listToolsViaInProcess()`
- `callToolViaInProcess(toolName, arguments)`
- `parseArgumentsJson(argumentsJson)`
- `JsonElement.toAnyValue()`
- `isLocalServerUrl(serverUrl)`
- `extractRequiredParams(schemaJson)`
- top-level: `Map<String, Any?>.toJsonObject()`, `Any?.toJsonElement()`.

---

## 6. FSM задачи (Task State Machine)

### `/app/src/main/java/com/example/vasganchalenge1/data/taskfsm/TaskStateModels.kt`
**Enums**:
- `TaskPhase`: `PLANNING`, `EXECUTION`, `VALIDATION`, `DONE`
- `TaskStatus`: `ACTIVE`, `PAUSED`, `CANCELLED`, `ERROR`

**Sealed модели**:
- `TaskStep`: `CollectRequirements`, `CreatePlan`, `ImplementFeature`, `RunChecks`, `Finished`
- `ExpectedAction`: `UserReply`, `ToolCall`, `Idle`
- `TaskEvent`: `UserMessage`, `PauseRequested`, `ResumeRequested`, `CancelRequested`, `ResetRequested`, `ToolResult`

**State**:
- `TaskState(taskId, phase, currentStep, expectedAction, status, updatedAt)`

**Функция**:
- `initialTaskState(taskId, now)` — стартовое FSM-состояние.

### `/app/src/main/java/com/example/vasganchalenge1/data/taskfsm/TaskReducer.kt`
**Object**: `TaskReducer`
- `reduce(state, event)` — чистый reducer FSM.

Внутренние методы:
- `reduceUserMessage(state, event)` — логика PLANNING/сбора требований.
- `reduceToolResult(state, event)` — переходы по результатам `LLM_PLAN/CODEGEN/RUN_CHECKS`.
- `extractRequirements(text, known)` — извлечение `goal/constraints` из текста.
- top-level: `missingRequiredFields(fields)` + `REQUIRED_FIELDS`.

### `/app/src/main/java/com/example/vasganchalenge1/data/taskfsm/TaskStateJson.kt`
**Класс**: `TaskStateJson`
- `toJson(state)` — сериализация `TaskState`.
- `fromJson(json)` — десериализация.

Внутренние методы:
- `encodeStep`, `decodeStep`
- `encodeExpectedAction`, `decodeExpectedAction`
- `stringList`, `stringMap`
- top-level: `enumValueOfOrNull`.

### `/app/src/main/java/com/example/vasganchalenge1/data/taskfsm/TaskStateStore.kt`
**Класс**: `TaskStateStore`
- `get(taskId)` — загрузить state.
- `save(state)` — сохранить state.
- `clear(taskId)` — удалить state.
- `keyFor(taskId)` — ключ DataStore.

### `/app/src/main/java/com/example/vasganchalenge1/data/taskfsm/TaskToolRunner.kt`
**Интерфейс**: `TaskToolRunner`
- `run(toolName, hint, state)`.

**Реализация**: `DefaultTaskToolRunner`
- `run(...)` — заглушечный runner для `LLM_PLAN`, `CODEGEN`, `RUN_CHECKS`.

### `/app/src/main/java/com/example/vasganchalenge1/data/taskfsm/TaskFsmManager.kt`
**Класс**: `TaskFsmManager`
- `getOrCreate(taskId)` — получить/инициализировать state.
- `dispatch(taskId, event)` — применить event + автозапуск pending tool.
- `reset(taskId)` — reset FSM.
- `runPendingTool(taskId)` — выполнить ожидаемый tool call.
- `runPendingTool(state)` — внутренняя реализация.

---

## 7. Tool routing (Natural Language -> MCP tool)

### `/app/src/main/java/com/example/vasganchalenge1/data/toolrouting/ToolResolution.kt`
`ToolResolution`:
- `NoTool`
- `ToolCall(toolName, argumentsJson)`
- `ClarificationNeeded(message)`

### `/app/src/main/java/com/example/vasganchalenge1/data/toolrouting/ToolRouterPromptBuilder.kt`
**Класс**: `ToolRouterPromptBuilder`
- `buildSystemPrompt(tools)` — системный prompt для router LLM (строгий JSON-формат ответа).
- `escape(value)` — экранирование для JSON-вставок.

### `/app/src/main/java/com/example/vasganchalenge1/data/toolrouting/ToolRouterResponseParser.kt`
**Класс**: `ToolRouterResponseParser`
- `parse(raw, availableTools)` — разбирает JSON-ответ router LLM.

Внутренние методы:
- `parseToolCall(root, availableTools)`
- `isMissingArgument(value)`
- `extractJsonObject(raw)` (в т.ч. trim markdown fences).

### `/app/src/main/java/com/example/vasganchalenge1/data/toolrouting/NaturalLanguageToolRouter.kt`
**Класс**: `NaturalLanguageToolRouter`
- `resolve(settings, userMessage, availableTools)` — вызывает LLM router, парсит ответ, применяет fallback-правила.

Внутренние методы:
- `fallbackRuleBased(userMessage, availableTools)`
- `extractUsername(text)`.

---

## 8. DI (Hilt)

### `/app/src/main/java/com/example/vasganchalenge1/di/JsonModule.kt`
- `provideMoshi()` — `Moshi` singleton.

### `/app/src/main/java/com/example/vasganchalenge1/di/LocalMcpServerModule.kt`
- `provideLocalMcpServerManager()` — singleton менеджер локального MCP сервера.

### `/app/src/main/java/com/example/vasganchalenge1/di/McpModule.kt`
- `provideMcpHttpClient()` — Ktor `HttpClient` для MCP (SSE + timeout).

### `/app/src/main/java/com/example/vasganchalenge1/di/NetworkModule.kt`
- `provideOkHttp()` — OkHttp с логированием и заголовками.
- `provideRetrofit(okHttp, moshi)`
- `provideApi(retrofit)`.

### `/app/src/main/java/com/example/vasganchalenge1/di/TaskFsmModule.kt`
- `bindTaskToolRunner(impl)` — биндинг `DefaultTaskToolRunner` -> `TaskToolRunner`.

---

## 9. UI: состояния, роутинг, экраны, VM

### `/app/src/main/java/com/example/vasganchalenge1/ui/ChatUiState.kt`
**`ChatUiState`** — единое состояние экрана чата: ids, память, инварианты, FSM debug, MCP debug, сообщения, метрики, input/error/loading.

### `/app/src/main/java/com/example/vasganchalenge1/ui/ChatToolRouting.kt`
**Модели**:
- `McpToolCommand`
- `InitialChatRoute`
- `RoutedChatAction`

**Функции**:
- `initialChatRoute(input)` — приоритет `/tool` vs NL routing.
- `routedChatAction(resolution)` — маппинг `ToolResolution` в действие.
- `parseMcpToolCommand(input)` — парсер команды `/tool ...`.

### `/app/src/main/java/com/example/vasganchalenge1/ui/ChatViewModel.kt`
**Класс**: `ChatViewModel`

Публичные методы:
- `onInputChange(v)`
- `createBranchFrom(messageId, onDone)`
- `pauseTask()` / `resumeTask()` / `cancelTask()` / `resetTask()`
- `onSendClick()` — главный pipeline отправки:
  - direct `/tool`,
  - FSM-команды (`pause/resume/cancel`),
  - natural-language tool router,
  - обычный LLM запрос,
  - запись метрик/памяти/чата.

Внутренние методы:
- `onRegularChatMessage(text)`
- `onRouterClarification(userText, clarification)`
- `onToolCommand(rawText, command)`
- `dispatchTaskEvent(event)`
- `persistChat(facts, factsMessageCount, messages, metrics)`
- `updateFactsIfNeeded(facts, factsMessageCount, fullMessages, settings)`
- `buildRequestHistory(fullMessages, settings)`

Top-level helpers:
- `String.toTaskCommand()`
- `buildLongTermMemoryJson(longTermMemory)`
- `jsonEntry(key, value)`
- `escapeJson(value)`
- `buildTaskPhasePrompt(taskState)`
- `TaskCommand` (private enum: `PAUSE`, `RESUME`, `CANCEL`)
- `PricePer1M` (вспомогательная модель цены за 1M токенов)
- `calcCostUsd(model, promptTokens, completionTokens)`.

### `/app/src/main/java/com/example/vasganchalenge1/ui/ChatScreen.kt`
**Composable/функции**:
- `MainRoute(...)` — привязка `ChatViewModel` к `ChatScreen`.
- `ChatScreen(...)` — основной экран чата.
- `McpDebugHeader(...)`
- `TaskDebugPanel(...)`
- `TaskPhaseStepper(...)`
- `PhaseStepChip(...)`
- `PhaseConnector(...)`
- `phaseLabel(phase)`
- `MetricsHeader(metrics)`
- `ChatBubble(msg, onCreateBranch)`
- `describeStep(step)`
- `describeExpectedAction(action)`
- `formatTimestamp(timestamp)`

**Enum**: `PhaseStepStyle`.

### `/app/src/main/java/com/example/vasganchalenge1/ui/FactsScreen.kt`
- `FactsScreen(...)` — экран долгосрочной/рабочей памяти и счетчиков.
- `MemoryCard(title, value)`
- `buildLongTermMemoryBlock(...)`
- `buildInvariantBlock(invariants)`

### `/app/src/main/java/com/example/vasganchalenge1/ui/branches/BranchesViewModel.kt`
- `BranchesUiState`
- `BranchesViewModel` (явных public-методов нет; состояние обновляется в `init` из `profilesFlow`).

### `/app/src/main/java/com/example/vasganchalenge1/ui/branches/BranchesScreen.kt`
- `BranchesScreen(...)`
- `BranchCard(chat, isCurrent, onOpenChat)`

### `/app/src/main/java/com/example/vasganchalenge1/ui/chats/ChatListViewModel.kt`
- `ChatListUiState`
- `ChatListViewModel`
- `createChat(onDone)`
- `deleteChat(chatId)`

### `/app/src/main/java/com/example/vasganchalenge1/ui/chats/ChatListScreen.kt`
- `ChatListScreen(...)`

### `/app/src/main/java/com/example/vasganchalenge1/ui/mcp/McpServerViewModel.kt`
- `McpConnectionStatusUi`
- `McpServerUiState`
- `McpServerViewModel`
- `setServerUrl(url)`
- `useLocalServerAndConnect()`
- `connectAndLoadTools(serverUrl)`
- `callGithubGetUser(username)`
- `callGithubGetRepo(owner, repo)`
- `callTool(toolName, argsJson)` (private)
- `McpConnectionStatus.toUiStatus()` (private extension)

### `/app/src/main/java/com/example/vasganchalenge1/ui/mcp/McpServerScreen.kt`
- `McpServerScreen(...)`

### `/app/src/main/java/com/example/vasganchalenge1/ui/profiles/ProfileListViewModel.kt`
- `ProfileListUiState`
- `ProfileListViewModel`
- `createProfile(title, longTermMode, onDone)`

### `/app/src/main/java/com/example/vasganchalenge1/ui/profiles/ProfileListScreen.kt`
- `ProfileListScreen(...)`
- `LongTermModeOption(...)` (private)

### `/app/src/main/java/com/example/vasganchalenge1/ui/profiles/ProfileSettingsViewModel.kt`
- `EditableMemoryField`, `EditableInvariant`, `ProfileSettingsUiState`
- `ProfileSettingsViewModel`
- `setProfileDescription(value)`
- `setCommunicationLanguage(value)`
- `addCustomField()`
- `updateCustomFieldKey(id, value)`
- `updateCustomFieldValue(id, value)`
- `removeCustomField(id)`
- `addInvariant()`
- `updateInvariant(id, value)`
- `removeInvariant(id)`
- `save(onDone)`

### `/app/src/main/java/com/example/vasganchalenge1/ui/profiles/ProfileSettingsScreen.kt`
- `ProfileSettingsScreen(...)`

### `/app/src/main/java/com/example/vasganchalenge1/ui/settings/SettingsViewModel.kt`
- `SettingsUiState`
- `SettingsViewModel`
- `setModel`, `setEnabled`, `setContextMode`, `setFormat`, `setLengthLimit`, `setStopSequence`, `setMaxTokensText`, `setTemperature`, `save`.

### `/app/src/main/java/com/example/vasganchalenge1/ui/settings/SettingsScreen.kt`
- `SettingsScreen(...)`
- `contextModeLabel(mode)` (private)

### `/app/src/main/java/com/example/vasganchalenge1/ui/tasks/TaskListViewModel.kt`
- `TaskListUiState`
- `TaskListViewModel`
- `createTask(title, onDone)`

### `/app/src/main/java/com/example/vasganchalenge1/ui/tasks/TaskListScreen.kt`
- `TaskListScreen(...)`

---

## 10. Навигация

### `/app/src/main/java/com/example/vasganchalenge1/ui/navigation/Routes.kt`
- Константы route-ов: `Profiles`, `ProfileSettings`, `Tasks`, `ChatList`, `Chat`, `Settings`, `Facts`, `Branches`, `McpServer`.
- Хелперы построения route с параметрами:
  - `profileSettings(profileId)`
  - `tasks(profileId)`
  - `chatList(taskId)`
  - `chat(chatId)`
  - `settings(chatId)`
  - `facts(chatId)`
  - `branches(chatId)`
  - `mcpServer(chatId)`

### `/app/src/main/java/com/example/vasganchalenge1/ui/navigation/AppNavGraph.kt`
- `AppNavGraph()` — полный граф экранов + wiring VM/Callbacks.

---

## 11. Полезные заметки для будущей разработки

1. **Security**: в `NetworkModule` сейчас хардкодится `Authorization Bearer ...`.
   - Нужно вынести в безопасный storage/BuildConfig/remote config.

2. **MCP состояние**:
   - единая точка правды — `McpRepository.state`.
   - `ChatViewModel` и `McpServerViewModel` уже подписаны на один shared state.

3. **Приоритет отправки в чате**:
   - `/tool ...` имеет максимальный приоритет,
   - затем команды FSM (`pause/resume/cancel`),
   - затем NL-router,
   - затем обычный chat flow.

4. **FSM документация**:
   - дополнительно см. `/docs/task_fsm.md`.

5. **Контекст в LLM**:
   - `ContextMode.LAST_10`: в запрос уходит только 10 последних сообщений,
   - `ContextMode.FACTS`: старые сообщения агрегируются в facts + отправляется 10 последних.

6. **LongTerm AUTO**:
   - автоматическая запись разрешена только для `LongTermMode.AUTO` (проверка в `LongTermMemoryManager`).

7. **Диагностика инвариантов**:
   - нарушение инвариантов проверяется LLM-чекером (`detectInvariantViolation`), bubble ассистента помечается в UI.

8. **Версии JVM**:
   - `app` собирается под Java 11,
   - `mcpserver` сейчас под Java 21 (это важно при classpath/bytecode проблемах).
