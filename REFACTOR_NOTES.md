# Outstanding architectural refactors

Tracks the multi-PR architectural items surfaced by the
`CODEBASE_REVIEW.md` audit that are too large to land surgically. Each
entry includes scope, blast radius, the target file structure, and a
sequenced plan so a follow-up session can pick up safely. Surgical
sub-tasks that were already landed are noted under the "delivered"
bullet of each section.

## 1. PlayerViewModel decomposition

**Status:** Not started. The file is currently ~5,100 lines and mixes
seven cohesive but distinct concerns.

**Target structure (under `presentation/viewmodel/player/`):**

1. `PlaybackController` — owns `MediaController` lifecycle, `Player.Listener`,
   `playPause`/`seekTo`/`nextSong`/`previousSong`/`toggleShuffle`/
   `cycleRepeatMode`/`playSongs`/`playSongsShuffled`/`playExternalUri`/
   `loadAndPlaySong`/`playAlbum`/`playArtist`/`buildResolvedPlaybackMediaItem`,
   plus `updateCurrentPlaybackQueueFromPlayer` /
   `refreshPlaybackAudioMetadata`. Roughly 1,500 lines.
2. `CastController` — Cast routes, sessions, transfer back, remote queue
   alignment, route volume, route discovery callbacks, `castSongUiSyncJob`.
   Roughly 700 lines.
3. `LibraryFacade` (or just inject `LibraryStateHolder` directly into
   `LibraryScreen` and friends) — library tab navigation, sort options,
   folder navigation, storage filter, daily mix triggers. Roughly 700 lines.
4. `AiPlaylistController` — sheet visibility, generation triggers,
   `hasActiveAiProviderApiKey` combine. Roughly 400 lines.
5. `LyricsAndMetadataController` — lyrics callbacks, sync offset, manual
   search, metadata edit, write-permission flow, delete-permission flow.
   Roughly 800 lines.
6. The residual `PlayerViewModel` should be ~600–800 lines and own:
   sheet visibility/expansion, predictive-back fractions, queue-source
   name, toast event flow, and routing to the controllers above.

**Sequence (safe, incremental):**

- Step 1 — extract `LibraryFacade` first, because most callers are
  already injecting `LibraryStateHolder` and the facade is a thin
  delegate. Replace `playerViewModel.libraryStateHolder.foo` with
  direct `libraryStateHolder.foo` at call sites.
- Step 2 — move the Cast wiring (700 lines) into a `CastController`
  injected into `PlayerViewModel`. No screens currently depend on
  PlayerViewModel for Cast except `CastBottomSheet`.
- Step 3 — extract `LyricsAndMetadataController`. Permission flows
  require coordination between Activity (for `IntentSender`) and VM —
  keep a thin permission-bridge interface to avoid `Activity` leaks.
- Step 4 — extract `AiPlaylistController`. Already mostly delegated to
  `AiStateHolder`; this step removes the API-key combine duplication.
- Step 5 — extract `PlaybackController` last; it is the largest and
  the most central.

**Blast radius:** every screen that injects `PlayerViewModel` (~30
composables). Tests: every `*ViewModelTest` plus `PlaybackStateHolderTest`.

**Delivered surgically so far:** flow consolidation
(`fullPlayerSlice`/`playerConfigSlice`), `currentSongArtists` typed as
`ImmutableList`, `imageLoader` hoist, `resolveSelectedAlbumSongs`
parallelized, `EotStateHolder` interaction documented.

## 2. LibraryScreen extraction

**Status:** First extraction landed.
`WatchTransferProgressDialog` moved to
`presentation/screens/library/WatchTransferProgressDialog.kt`. The
file is still ~3,600 lines.

**Target structure (continue under `presentation/screens/library/`):**

1. `WatchTransferProgressDialog.kt` — **landed.**
2. `LibraryNavigationPill.kt` — `LibraryNavigationPill` +
   `LibraryTabSwitcherSheet` + `LibraryTabGridItem` +
   `rememberLibraryNavigationPillTitleStyle`. ~430 lines.
3. `LibraryFoldersTab.kt` — `LibraryFoldersTab` + `FolderPlaylistItem` +
   `FolderListItem` + `flattenFolders`/`sortMusicFoldersByOption`/
   `sortSongsForFolderView`/`collectAllSongs` +
   `isDescendantFolderPath`. ~425 lines.
4. `LibraryAlbumItems.kt` — `AlbumGridItemRedesigned`/`AlbumListItem`/
   `ArtistListItem`. ~490 lines.
5. `LibrarySyncIndicators.kt` — `LibrarySyncOverlay` +
   `LibraryInlineSyncIndicator` + `CompactLibraryPagerIndicator`.
   ~150 lines.
6. `rememberLibrarySelectionState.kt` — the ~200-line multi-selection
   wiring block from the screen body.
7. Per-tab `LibrarySongsTabPage` / `LibraryAlbumsTabPage` etc. so the
   `HorizontalPager` `when (tabId)` block shrinks to ~50 lines.

**Sequence:** items above in order. Each step compiles independently
and the screen file shrinks monotonically.

**Estimated total reduction:** ~1,700 lines moved out, leaving the
screen file around ~2,000 lines focused on `Scaffold`/`TopBar`/sheets/
pager wiring.

## 3. DataStore split

**Status:** Partial. Three sibling repositories exist —
`ThemePreferencesRepository`, `EqualizerPreferencesRepository`,
`PlaylistPreferencesRepository` — but all of them and the AI repo
still share `Context.dataStore by preferencesDataStore(name = "settings")`.
117 keys live in one file. Every write to any key fires re-evaluation
on every flow subscribed to any other key.

**Target structure (under `data/preferences/`):**

- `theme.preferences_pb` — already separated logically; needs a
  dedicated DataStore file
- `playback.preferences_pb` — sleep timer prefs, cross-fade prefs,
  transition prefs, shuffle/repeat persistence
- `library.preferences_pb` — sort options, last-storage-filter, hide-
  local-media, library tab order
- `equalizer.preferences_pb` — already separated logically
- `ai.preferences_pb` — non-secret AI prefs (provider, model, prompt,
  safe-token-limit); secrets stay in EncryptedSharedPreferences
- `dev.preferences_pb` — feature flags, debug toggles
- `settings.preferences_pb` — true "general settings" that don't fit
  the above (locale, theme mode etc.)

**Sequence (one domain at a time, safe migration):**

For each domain:
1. Add `Context.<domain>DataStore by preferencesDataStore(name = "<domain>")`.
2. Add an `@Named("<domain>")` qualifier so DI doesn't collide on
   `DataStore<Preferences>`.
3. In the repository, read all old keys from the legacy store on first
   launch, write into the new store, then remove from the legacy
   store. Mark migration done via a one-time flag in the new store.
4. Update all flows in the repository to read/write the new store.
5. Verify no other repository references those keys via the legacy
   store name.

**Blast radius:** all StateHolders / ViewModels that collect prefs
flows. Tests: `*PreferencesRepositoryTest` and instrumentation tests
that cover real DataStore migration.

**Delivered surgically so far:** AI API keys moved to
EncryptedSharedPreferences with one-time migration from legacy
DataStore. `LyricsRepositoryImpl` and `LibraryStateHolder` now batch
their multi-flow reads via `awaitAll` so the cold-flow startup cost
overlaps instead of stacking sequentially.

## 4. Singleton StateHolder lifecycle reanchoring

**Status:** Partial. 9 singleton state holders take a `scope` parameter
via `initialize(scope: CoroutineScope)` from `PlayerViewModel.init`,
and unregister system callbacks in `onCleared()`. On any process
recreation where a new `PlayerViewModel` is instantiated against the
same Application singleton, there is a window between
`onCleared` (sets `isInitialized = false`) and the next `initialize`
where the holder is in a deinitialized state, and a stale `scope`
field can be used by subsequent ProcessLifecycleOwner callbacks.

Affected:
- `ConnectivityStateHolder`
- `CastTransferStateHolder`
- `CastStateHolder`
- `SearchStateHolder`
- `AiStateHolder`
- `LibraryStateHolder`
- `SleepTimerStateHolder`
- `QueueUndoStateHolder`
- `PlaylistDismissUndoStateHolder`

**Target:** Each holder injects `@AppScope` directly and uses it as
the primary scope. `initialize(scope)` is replaced with
`bind(callbacks)` which only wires up callbacks/listeners but uses
the always-alive `@AppScope` for `launch`. `onCleared` becomes
optional and only un-binds callbacks.

**Sequence:**

1. Migrate the easiest first: `SearchStateHolder`, `AiStateHolder`,
   `QueueUndoStateHolder`, `PlaylistDismissUndoStateHolder` — these
   already only `launch` into the captured scope and have no system
   listeners. Switch their captured scope to `@AppScope`.
2. `LibraryStateHolder` — same pattern, but it has flow collectors
   (`startObservingLibraryData`); make sure those are cancellable
   via a controller-scope `Job` even though the parent scope lives
   for the app.
3. `ConnectivityStateHolder`, `CastStateHolder`, `CastTransferStateHolder`,
   `SleepTimerStateHolder` — these register system callbacks. Move
   registration into `initialize()` but use `@AppScope` for any
   `.launch{}` calls and add a kill-switch flow so `onCleared` can
   pause without truly unregistering.

**Blast radius:** every singleton holder + every test that mocks them.

**Delivered surgically so far:** `MusicRepositoryImpl` switched from a
private `CoroutineScope(Dispatchers.IO + SupervisorJob())` to use
`@AppScope`. System-service lookups in `ConnectivityStateHolder`,
`SleepTimerStateHolder`, and `CastStateHolder` are now `by lazy` so
they don't run on the first-frame critical path during singleton-graph
construction.

## 5. Test coverage expansion

**Status:** Partial. New tests added:

- `ZipShareHelperSanitizationTest` — 13 cases covering path-traversal,
  leading-dot defang, length cap.
- `ArtworkTransportSanitizerTest` — oversized-input rejection, null/
  empty short-circuits, config sanity.
- `SyncWorkerHashTest` — FNV-1a determinism, avalanche, and
  zero-collision check on a 5000-input corpus.
- `FileDeletionUtilsTest` — `canDeleteFile` and `getFileInfo` paths
  exercising real files via JUnit `TemporaryFolder`.

Still missing per the review:

- `MusicService` unit tests — MediaSession callbacks, foreground-service
  lifecycle, sleep timer integration, Cast switching, Wear command
  handling. Requires Robolectric or instrumentation; service has
  ~4,500 lines and 35+ DI dependencies.
- `MediaFileHttpServerService` HTTP-route tests — Ktor `testApplication`
  block for `/song/<id>`, `/art/<id>`, auth-token validation, Range
  header parsing. Estimated 200-400 lines of test code.
- `WearCommandReceiver` JSON-fuzz tests — malformed `MessageEvent`
  payloads, missing fields, type mismatches.
- Turbine-based StateFlow emission tests for `PlayerViewModel`,
  `PlaybackStateHolder`, `LyricsStateHolder`, `ThemeStateHolder`.
- Compose UI tests for recomposition counts via `composeTestRule`.
  Per `app/performance_analysis.md`, recomposition counts are a
  critical performance metric and there are zero UI tests today.

## 6. CastBottomSheet flow consolidation

**Status:** Per-frame allocations already wrapped in `remember()` in
the recent perf commit. Full consolidation into a
`CastBottomSheetSlice` flow requires combining across 3 different
StateHolders (cast/connectivity/playback). Kotlin's `combine()` only
supports up to 5 args so the combine would need to be nested, which
the original review flagged as a smell. Plausible target: move the
13 fields into `CastTransferStateHolder` as a single derived flow,
since most of them are already fed from there.

## 7. HTTP server self-signed HTTPS

**Status:** Not started. Token theft from sniffing Cast traffic
(which runs in plaintext to the Default Media Receiver) remains
mitigated only by the IP allowlist + auth-token. A configurable
HTTPS option with a per-session self-signed cert and a pin in
`MediaInfo` would eliminate the LAN sniffing risk. Out of scope for
surgical fixes because the receiver-side cert pinning is unsupported
by the Default Media Receiver, so this needs a custom Cast receiver
app first.
