# RippleChat — Refactor & Polish Prompt (give this to the LLM)

You are a senior Android architect and Compose UI designer. You are working on **RippleChat**, an existing
Kotlin + Jetpack Compose chat app (Firebase Firestore/Auth, FCM, Cloudinary media, Room cache, Hilt DI,
Retrofit to a Node/Express backend). The app works but was built feature-by-feature without a coherent
architecture, so the code is messy and the UI feels amateur and laggy. Your job is to **restructure it into
clean, modular MVVM + Clean Architecture, unify navigation into a single host with dialogs, fix the UI
jank, and redesign the look** — without breaking the existing backend contract or Firebase data model.

Work incrementally and keep the app compiling at every step. Do not invent new backend endpoints.

---

## 0. Hard constraints (do NOT break these)

- **Backend (`server.js`) is fixed.** Only these endpoints exist on the Render base URL
  `https://auth-server-imagekit-for-ripplechat.onrender.com`:
  - `POST /cloudinary-auth` → returns `{signature, timestamp, cloud_name, api_key, upload_preset}`
    (body: `{public_id, resource_type?}`)
  - `POST /send-fcm-notification` (body: `{recipientId, senderId, messageText, chatId}`; skips if recipient online)
  - `POST /cloudinary-delete` (body: `{public_id, resource_type?}`)
  Keep request/response shapes identical.
- **Firebase data model is fixed:** collections `users`, `presence`, plus existing chat/message/status docs.
  `users` docs include `fcmToken`, `name`. Don't rename fields without a migration.
- Keep **Cloudinary** uploads signed via `/cloudinary-auth` and **FCM** high-priority data messages.
- Keep **minSdk 24 / targetSdk 35**, Hilt, Room (with existing `MIGRATION_1_2`), Coil, ZXing (QR), WorkManager.
- App package id stays `com.example.ripplechat`.

---

## 1. Target architecture

Adopt **Clean Architecture + MVVM**, split into Gradle modules. Each feature is self-contained.

```
:app                         // single MainActivity, NavHost, Application, DI wiring, theme host
:core:ui                     // design system: theme, color, type, shared composables, animations
:core:common                // Result/UiState, dispatchers, extensions, constants, error mapping
:core:data                  // FirebaseSource, Retrofit/NotificationService, Cloudinary, Room db/dao
:core:domain                // shared domain models + base UseCase + repository interfaces
:feature:auth               // login / signup
:feature:dashboard          // chat list + tabs host (chats / updates / profile)
:feature:chat               // 1:1 + group chat
:feature:status             // status upload + viewers
:feature:profile            // profile view/edit
:feature:group              // new group creation
```

Per feature module use these layers:

- **model** — domain entities + DTO/entity mappers (pure Kotlin, no Android/Firebase types leaking up).
- **domain** — `repository` interfaces + **use cases** (one responsibility each, e.g. `SendMessageUseCase`,
  `ObserveChatsUseCase`, `UploadStatusUseCase`). VMs depend on use cases, never on data sources directly.
- **data** — repository implementations, mapping Firebase/Room/Retrofit ↔ domain models.
- **presentation** — `ViewModel` exposing a single immutable `UiState` `StateFlow` + a sealed `UiEvent`/`UiAction`.
- **ui** — Composables that render `UiState` and emit actions. No business logic, no Firebase calls in composables.

Rules:
- Every screen has one `data class XxxUiState` (loading/content/error/empty all expressed in it).
- ViewModels expose `StateFlow<UiState>`; one-shot effects (navigation, toasts) via `Channel`/`SharedFlow`.
- Move all validation into ViewModels/use cases, **not** `LaunchedEffect` blocks in composables.
- **Fix package hygiene first:** every file's `package` must match its folder. Today many don't —
  e.g. `AppModule` is in `...app.data.model.di`, `DashBoardVM` is under `...data.model.ui.theme.screens.home`,
  `FirebaseSource` under `...data.model.firebase`. Normalize all of these.

---

## 2. Single-activity + dialog navigation (the "Pays Compose" pattern)

Replace the screen-per-feature `NavGraph` with **one main host**:

- Keep a single `MainActivity` hosting a `NavHost`, but the **top-level destination is the Dashboard**
  (chats list + Updates + Profile tabs). Auth/splash are a separate nav graph shown only when logged out.
- **Things that are currently full screens but are really overlays should become dialogs / bottom sheets /
  modal full-screen dialogs**, not navigation destinations:
  - New Group, Status Upload, AI Assistant, Group Info, attachment picker, profile edit, QR generate/scan,
    location preview → `Dialog` / `ModalBottomSheet` / `BasicAlertDialog`.
  - Keep real **navigation destinations** only for: Auth flow, Dashboard, Chat (1:1/group), Status viewer.
- Centralize navigation in a `sealed interface Destination` + a typed nav extension; no stringly-typed routes
  scattered across files. Use Compose Navigation type-safe routes (or a single `Routes` object) consistently.
- Preserve existing deep links: `ripplechat://chat/{peerUid}/{peerName}` and the FCM notification
  navigation to `chat/{chatId}/{peerUid}/{peerName}`.

---

## 3. Performance — kill the swipe lag

The Dashboard currently puts `ProfileScreen` and `StatusScreen` (each with their own `hiltViewModel`,
Coil image loads, and Firestore listeners) **directly inside `HorizontalPager` pages**, with
`graphicsLayer` + `animateFloatAsState` running during the swipe. That's the jank. Fix it:

- Keep ViewModels **hoisted at the Dashboard level** (or activity-scoped) so swiping pages does not
  re-create VMs or re-subscribe Firestore listeners.
- Pages should observe already-collected state; don't start collecting flows inside a page that mounts on swipe.
- Remove per-frame `graphicsLayer`/`animateFloatAsState` work tied to pager offset unless it's cheap;
  prefer the pager's built-in snapping and a lightweight `beyondViewportPageCount = 1`.
- Use stable keys in every `LazyColumn`/`items` (chat id, status id); mark data classes `@Stable`/`@Immutable`.
- Move `SimpleDateFormat` and any per-item formatting out of composition (precompute in the VM / mapper).
- Use `collectAsStateWithLifecycle` and Coil with explicit `size()`/crossfade; avoid loading full-res images
  in list rows.
- Audit unnecessary recompositions (use `Modifier.then`, hoist lambdas, avoid passing unstable lambdas/lists).
- Split the **god files** so recomposition scopes are smaller: `ChatScreen.kt` (1329 LOC),
  `DashboardScreen.kt` (801), `FirebaseSource.kt` (593), `ChatVM.kt` (473) → break into focused composables/
  use cases/data sources.

---

## 4. UI / UX redesign (make it look professional)

Build a real **design system** in `:core:ui` and apply it everywhere (Profile, Dashboard, ChatScreen,
Login, Register are the worst offenders today):

- **Material 3 only.** Remove the Material 2 dependency and the M2 `SwipeToDismiss`; use the M3 equivalent.
  Define a proper `ColorScheme` (light + dark + dynamic color on Android 12+), `Typography`, `Shapes`.
- A cohesive brand: pick a primary/secondary palette, consistent corner radii, elevation, spacing scale
  (4/8/12/16/24), and reusable components: `RippleTopBar`, `RippleTextField`, `RipplePrimaryButton`,
  `Avatar`, `EmptyState`, `LoadingState`, `ChatBubble`, `SectionHeader`.
- Polished states everywhere: skeleton/shimmer loaders, empty states with illustration + CTA, error states
  with retry. No bare spinners or blank screens.
- **Animations that feel premium, not draggy:**
  - Use spring-based motion (`spring(stiffness = Medium/Low, dampingRatio = NoBouncy/LowBouncy)`), not long
    linear tweens. Keep durations ~150–300ms.
  - Shared-element / container transform for opening a chat from the list (Compose shared element transitions).
  - `AnimatedVisibility` + `animateItem()` for list insert/remove; subtle fade-through between tabs.
  - Message send: bubble enter animation; typing indicator; smooth scroll-to-latest.
  - Respect reduced-motion / keep everything 60fps; never block the main thread.
- Consistent insets/edge-to-edge, proper status/nav bar colors, keyboard handling (`imePadding`), and
  accessibility (content descriptions, min touch targets, contrast).

### Login / Register reformation (UI now; logic later)
Even though auth logic is refactored later, redesign the **screens** now to look like a professional signup:
- A branded onboarding header (logo + tagline), clear sectioning, and friendly copy.
- Register should ask clear, labeled questions: **Full name, Username, Email/phone, Password,
  Confirm password**, with inline per-field validation, password strength meter, show/hide toggle, and a
  clear primary CTA + "already have an account?" link. Login: identifier + password, "forgot password",
  "remember me". (Wire real logic later; for now keep current auth working but present it cleanly.)

---

## 5. Code quality / housekeeping

- Centralize DI: `@Module`s per layer; provide use cases; remove duplicate Retrofit/OkHttp wiring.
- Replace scattered `Log`/`Toast` with a small UI-event mechanism + a `Logger` wrapper.
- Introduce `Result<T>`/`UiState` for all async; map Firebase exceptions to user-friendly messages in one place.
- Remove dead code, duplicate dependency declarations in `build.gradle.kts`, and unused imports.
- Add KDoc on public use cases/repositories. Add basic unit tests for use cases and a couple of VM tests.
- Keep secrets in `local.properties`/`BuildConfig` (Geoapify already is); never hardcode keys.

---

## 6. How to deliver

1. Propose the module/package structure and a short migration plan first.
2. Refactor in small, compiling commits: (a) package hygiene, (b) extract `:core` modules, (c) feature
   modules + use cases, (d) single-host nav + dialogs, (e) performance fixes, (f) design system + redesign.
3. For each step, show the changed files and explain what moved and why.
4. After each milestone, confirm the app still builds and the backend/Firebase contracts are unchanged.

Begin with step 1: give me the proposed module + package layout and the migration order, then wait for go.
```
