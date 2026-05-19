package com.theveloper.pixelplay.presentation.screens.library

import com.theveloper.pixelplay.data.model.MusicFolder
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SortOption

/**
 * Pure-function folder/song sort helpers extracted from `LibraryScreen.kt`
 * as part of the file-decomposition refactor. No Compose dependencies; can
 * be exercised directly from JVM unit tests.
 */

internal fun flattenFolders(folders: List<MusicFolder>): List<MusicFolder> {
    return folders.flatMap { folder ->
        val current = if (folder.songs.isNotEmpty()) listOf(folder) else emptyList()
        current + flattenFolders(folder.subFolders)
    }
}

internal fun sortMusicFoldersByOption(
    folders: List<MusicFolder>,
    sortOption: SortOption
): List<MusicFolder> {
    return when (sortOption) {
        SortOption.FolderNameAZ -> folders.sortedWith(
            compareBy<MusicFolder> { it.name.lowercase() }
                .thenBy { it.path }
        )
        SortOption.FolderNameZA -> folders.sortedWith(
            compareByDescending<MusicFolder> { it.name.lowercase() }
                .thenBy { it.path }
        )
        SortOption.FolderSongCountAsc -> folders.sortedWith(
            compareBy<MusicFolder> { it.totalSongCount }
                .thenBy { it.name.lowercase() }
                .thenBy { it.path }
        )
        SortOption.FolderSongCountDesc -> folders.sortedWith(
            compareByDescending<MusicFolder> { it.totalSongCount }
                .thenBy { it.name.lowercase() }
                .thenBy { it.path }
        )
        SortOption.FolderSubdirCountAsc -> folders.sortedWith(
            compareBy<MusicFolder> { it.totalSubFolderCount }
                .thenBy { it.name.lowercase() }
                .thenBy { it.path }
        )
        SortOption.FolderSubdirCountDesc -> folders.sortedWith(
            compareByDescending<MusicFolder> { it.totalSubFolderCount }
                .thenBy { it.name.lowercase() }
                .thenBy { it.path }
        )
        else -> folders.sortedWith(
            compareBy<MusicFolder> { it.name.lowercase() }
                .thenBy { it.path }
        )
    }
}

internal fun sortSongsForFolderView(songs: List<Song>, sortOption: SortOption): List<Song> {
    return when (sortOption) {
        SortOption.FolderNameZA -> songs.sortedWith(
            compareByDescending<Song> { it.title.lowercase() }
                .thenBy { it.artist.lowercase() }
                .thenBy { it.id }
        )
        else -> songs.sortedWith(
            compareBy<Song> { it.title.lowercase() }
                .thenBy { it.artist.lowercase() }
                .thenBy { it.id }
        )
    }
}
