# Task FSM

## Как работает

В проект добавлен отдельный конечный автомат задачи (`Task FSM`), который хранится отдельно от истории чата и является источником правды для текущего состояния задачи.

FSM хранит:

- `phase`: `PLANNING`, `EXECUTION`, `VALIDATION`, `DONE`
- `currentStep`: типизированный шаг
- `expectedAction`: что сейчас ожидается от пользователя или системы
- `status`: `ACTIVE`, `PAUSED`, `CANCELLED`, `ERROR`
- `updatedAt`

Рабочая память (`Working Memory`) остается отдельной и не смешивается с FSM.

## Фазы и шаги

### `PLANNING`

- `CollectRequirements(missingFields, collectedFields)`
- `CreatePlan(requirements)`

### `EXECUTION`

- `ImplementFeature(featureKey, planSummary)`

### `VALIDATION`

- `RunChecks(targetFeatureKey)`

### `DONE`

- `Finished(summary)`

## События

- `UserMessage`
- `PauseRequested`
- `ResumeRequested`
- `CancelRequested`
- `ResetRequested`
- `ToolResult`

Reducer чистый:

`reduce(state, event) -> newState`

## Хранилище

FSM хранится локально в `DataStore`:

- файл: `app/src/main/java/com/example/vasganchalenge1/data/taskfsm/TaskStateStore.kt`
- key: `current_task_<taskId>`

State переживает убийство процесса и перезапуск приложения.

## Переходы

1. Новая задача стартует с:
   - `phase = PLANNING`
   - `step = CollectRequirements`
   - `expectedAction = UserReply`

2. После заполнения требований:
   - `CreatePlan`
   - `expectedAction = ToolCall(LLM_PLAN)`

3. После `ToolResult(LLM_PLAN)`:
   - `phase = EXECUTION`
   - `step = ImplementFeature`
   - `expectedAction = ToolCall(CODEGEN)`

4. После `ToolResult(CODEGEN)`:
   - `phase = VALIDATION`
   - `step = RunChecks`
   - `expectedAction = ToolCall(RUN_CHECKS)`

5. После `ToolResult(RUN_CHECKS)`:
   - если успех: `DONE`
   - если неуспех: назад в `EXECUTION`, шаг `ImplementFeature(featureKey="fixes")`

## Pause / Resume / Cancel

Команды на уровне чата:

- `pause` / `пауза`
- `resume` / `продолжить`
- `cancel` / `отмена`

Поведение:

- при `PAUSED` обычные сообщения не обрабатываются, пользователь получает подсказку написать `resume`
- `Resume` восстанавливает `ACTIVE` и продолжает с тем же `expectedAction`
- `Cancel` переводит задачу в `CANCELLED`
- `Reset task` сбрасывает FSM в initial state

## Где смотреть в UI

На `ChatScreen` добавлена `Task Debug Panel`, которая показывает:

- `Phase`
- `Step`
- `ExpectedAction`
- `Status`
- `updatedAt`

И кнопки:

- `Pause`
- `Resume`
- `Cancel`
- `Reset task`

## Как проверять

1. Открой чат внутри задачи
2. Посмотри `Task Debug Panel`
3. Отправь описание задачи
4. Проверь переходы `PLANNING -> EXECUTION -> VALIDATION -> DONE`
5. Нажми `Pause`, затем отправь обычное сообщение
6. Убедись, что задача не меняется и появляется подсказка про `resume`
7. Нажми `Resume`
8. Проверь, что FSM продолжает с сохраненного состояния
