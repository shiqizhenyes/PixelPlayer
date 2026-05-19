package com.theveloper.pixelplay.di

import javax.inject.Qualifier

/**
 * Qualifier for Deezer Retrofit instance.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DeezerRetrofit

/**
 * Qualifier for Fast OkHttpClient (Short timeouts).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FastOkHttpClient

/**
 * Qualifier for Gson instance configured for backup serialization.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BackupGson

/**
 * Qualifier for application-lifetime coroutine scope.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

/**
 * Qualifier for the dedicated playback-prefs DataStore. Used to incrementally
 * split the monolithic "settings" store into per-domain stores per the
 * CODEBASE_REVIEW.md DataStore-split plan.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlaybackDataStore

/**
 * Qualifier for the default app-wide DataStore (the legacy "settings"
 * store). Existing call sites that inject `DataStore<Preferences>` without
 * a qualifier keep working; this qualifier lets new callers explicitly
 * pick the non-playback store after the migration completes.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDataStore
