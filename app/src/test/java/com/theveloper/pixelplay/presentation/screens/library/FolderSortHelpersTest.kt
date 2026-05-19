package com.theveloper.pixelplay.presentation.screens.library

import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.model.MusicFolder
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SortOption
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.junit.Test

/**
 * Tests for the pure folder/song sort helpers extracted from LibraryScreen.
 * Coverage focuses on the comparator chains (primary + tiebreakers) and
 * the recursive flatten so the comparator stability and depth handling
 * stay verified during the LibraryScreen decomposition.
 */
class FolderSortHelpersTest {

    private fun song(
        id: String,
        title: String,
        artist: String = "Z artist"
    ): Song = Song(
        id = id,
        title = title,
        artist = artist,
        artistId = 0L,
        album = "",
        albumId = 0L,
        path = "/m/$id.mp3",
        contentUriString = "content://m/$id",
        albumArtUriString = null,
        duration = 0L,
        mimeType = null,
        bitrate = null,
        sampleRate = null
    )

    private fun folder(
        name: String,
        songs: List<Song> = emptyList(),
        subFolders: List<MusicFolder> = emptyList(),
        path: String = "/$name"
    ): MusicFolder = MusicFolder(
        path = path,
        name = name,
        songs = songs.toPersistentList(),
        subFolders = subFolders.toPersistentList()
    )

    @Test
    fun flattenFolders_skipsFoldersWithoutDirectSongs() {
        val root = folder(
            name = "root",
            songs = emptyList(),
            subFolders = listOf(
                folder("hasSong", songs = listOf(song("1", "a")))
            )
        )
        val flattened = flattenFolders(listOf(root))
        assertThat(flattened.map { it.name }).containsExactly("hasSong")
    }

    @Test
    fun flattenFolders_recursesIntoSubFolders() {
        val grandchild = folder("gc", songs = listOf(song("1", "x")))
        val child = folder("child", songs = listOf(song("2", "y")), subFolders = listOf(grandchild))
        val root = folder("root", songs = emptyList(), subFolders = listOf(child))
        val flattened = flattenFolders(listOf(root))
        assertThat(flattened.map { it.name }).containsExactly("child", "gc").inOrder()
    }

    @Test
    fun flattenFolders_emptyInputProducesEmpty() {
        assertThat(flattenFolders(emptyList())).isEmpty()
    }

    @Test
    fun sortMusicFolders_byNameAscendingCaseInsensitive() {
        val sorted = sortMusicFoldersByOption(
            folders = listOf(folder("Beta"), folder("alpha"), folder("Gamma")),
            sortOption = SortOption.FolderNameAZ
        )
        assertThat(sorted.map { it.name }).containsExactly("alpha", "Beta", "Gamma").inOrder()
    }

    @Test
    fun sortMusicFolders_byNameDescending() {
        val sorted = sortMusicFoldersByOption(
            folders = listOf(folder("Beta"), folder("alpha"), folder("Gamma")),
            sortOption = SortOption.FolderNameZA
        )
        assertThat(sorted.map { it.name }).containsExactly("Gamma", "Beta", "alpha").inOrder()
    }

    @Test
    fun sortMusicFolders_bySongCountWithTiebreakOnName() {
        val sorted = sortMusicFoldersByOption(
            folders = listOf(
                folder("Beta", songs = listOf(song("1", "a"))),
                folder("alpha", songs = listOf(song("2", "b"))),
                folder("Gamma", songs = listOf(song("3", "c"), song("4", "d")))
            ),
            sortOption = SortOption.FolderSongCountAsc
        )
        // First two have count==1: tiebreak by lowercase name (alpha < Beta).
        assertThat(sorted.map { it.name }).containsExactly("alpha", "Beta", "Gamma").inOrder()
    }

    @Test
    fun sortMusicFolders_bySongCountDescending() {
        val sorted = sortMusicFoldersByOption(
            folders = listOf(
                folder("low", songs = listOf(song("1", "a"))),
                folder("high", songs = listOf(song("2", "b"), song("3", "c")))
            ),
            sortOption = SortOption.FolderSongCountDesc
        )
        assertThat(sorted.map { it.name }).containsExactly("high", "low").inOrder()
    }

    @Test
    fun sortMusicFolders_bySubdirCountAscending() {
        val sorted = sortMusicFoldersByOption(
            folders = listOf(
                folder("noSubs"),
                folder("twoSubs", subFolders = listOf(folder("a"), folder("b")))
            ),
            sortOption = SortOption.FolderSubdirCountAsc
        )
        assertThat(sorted.map { it.name }).containsExactly("noSubs", "twoSubs").inOrder()
    }

    @Test
    fun sortMusicFolders_unknownOptionFallsBackToNameAZ() {
        // Any non-folder sort option falls through the else branch.
        val sorted = sortMusicFoldersByOption(
            folders = listOf(folder("Beta"), folder("alpha")),
            sortOption = SortOption.SongDefaultOrder
        )
        assertThat(sorted.map { it.name }).containsExactly("alpha", "Beta").inOrder()
    }

    @Test
    fun sortSongsForFolderView_defaultIsAscendingTitle() {
        val sorted = sortSongsForFolderView(
            songs = listOf(song("1", "Beta"), song("2", "alpha")),
            sortOption = SortOption.SongDefaultOrder
        )
        assertThat(sorted.map { it.title }).containsExactly("alpha", "Beta").inOrder()
    }

    @Test
    fun sortSongsForFolderView_zaSortReturnsDescending() {
        val sorted = sortSongsForFolderView(
            songs = listOf(song("1", "Beta"), song("2", "alpha")),
            sortOption = SortOption.FolderNameZA
        )
        assertThat(sorted.map { it.title }).containsExactly("Beta", "alpha").inOrder()
    }
}
