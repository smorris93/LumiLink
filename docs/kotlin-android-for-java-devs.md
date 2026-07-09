# Kotlin & Android for Java devs — a LumiLink reader's guide

A quick map of the Kotlin/Android idioms used in this codebase, aimed at someone who knows Java
but is new to Kotlin. Read this once and the source should read easily.

## Kotlin language

| Kotlin | What it means (vs Java) |
|---|---|
| `val x = 1` / `var y = 2` | `val` = final (immutable) reference; `var` = reassignable. Prefer `val`. |
| `String?` | A **nullable** type. Plain `String` can never be null — the compiler enforces it. |
| `a?.b` | Safe call: evaluates to null if `a` is null instead of throwing an NPE. |
| `a ?: b` | "Elvis": use `a`, or `b` if `a` is null. |
| `class Foo : Bar()` | `Foo extends Bar`. The `()` invokes Bar's constructor. |
| `data class` | Auto-generates `equals`/`hashCode`/`toString`/`copy` from its properties. |
| `object Foo` | A singleton. `Foo.bar()` calls a method on the one instance. |
| `sealed interface` | A closed set of subtypes (all in one file) → `when` can be exhaustive with no `else`. |
| `companion object { }` | Where "static" members live (Kotlin has no `static`). |
| `fun f() = expr` | Single-expression function; the body *is* the return value. |
| trailing lambda | `foo { ... }` passes a lambda as the last argument — used everywhere in Compose. |
| `lateinit var` | A non-null property initialized after construction (e.g. in `onCreate`). |
| `internal` | Visible within this module only (an implementation-detail visibility). |

## Coroutines (async without threads)

- `suspend fun` — a function that can *pause* without blocking a thread. Callable only from another
  `suspend` function or a coroutine. We use it for all network/disk I/O.
- `withContext(Dispatchers.IO) { ... }` — run this block on the background I/O thread pool.
- `viewModelScope.launch { ... }` — start a coroutine tied to a ViewModel; auto-cancelled when the
  ViewModel is destroyed. This is how the UI kicks off async work.
- `Flow<T>` — a stream of values over time (think a cold, coroutine-native `Iterable`/`Observable`).
- `StateFlow<T>` — a Flow that always has a current value; the UI observes it and recomposes on change.

## Android + Jetpack Compose

- **Single Activity**: `MainActivity` hosts *all* UI via Compose; screens are functions, not Activities.
- **`@Composable` function** — describes UI declaratively. When state it reads changes, Compose
  re-invokes ("recomposes") it. You never manually update views.
- **State**: `remember { mutableStateOf(...) }` holds UI state across recompositions;
  `collectAsStateWithLifecycle()` turns a `StateFlow` into observable Compose state.
- **`ViewModel`** — holds screen state/logic and survives rotation. Screens read its `StateFlow`s
  and call its functions; they never touch the network layer directly (MVVM).
- **`Scaffold`** — Material page skeleton (top bar, content, bottom bar, snackbar).
- **Manual DI (`AppContainer`)** — one class constructs all the singletons (no Hilt/Dagger magic).
  Screens fetch it via `appContainer()`.

## How a LumiLink screen is wired (the pattern to recognize)

```
Screen (@Composable)
  → reads viewModel.uiState (a StateFlow) via collectAsStateWithLifecycle()
  → renders from that immutable state
  → user taps → calls a viewModel function
      → viewModel launches a coroutine → calls a Repository/Manager (suspend)
          → updates its private MutableStateFlow
              → the StateFlow emits → the screen recomposes
```

That one loop describes every interactive screen in the app.
