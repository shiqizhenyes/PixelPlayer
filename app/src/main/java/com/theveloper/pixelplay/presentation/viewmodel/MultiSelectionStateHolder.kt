package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * State holder for multi-selection functionality in LibraryScreen tabs.
 * Manages selection state with order preservation using a LinkedHashSet internally.
 *
 * Selection order is maintained - the first selected song is at index 0,
 * subsequent selections are appended in the order they were selected.
 */
@Singleton
class MultiSelectionStateHolder @Inject constructor() {

    // Guards multi-flow mutations so a reader of the four exposed StateFlows
    // observes a coherent final state. `_selectedSongs.update {}` alone only
    // made one flow atomic; the sibling writes for ids/count/mode could race
    // with another toggle landing in the gap. A single synchronized block
    // around the whole read-modify-write closes that gap.
    private val mutationLock = Any()

    // Internal mutable state - uses List to preserve selection order
    // LinkedHashSet behavior is enforced via toggle logic
    private val _selectedSongs = MutableStateFlow<List<Song>>(emptyList())
    
    /**
     * Immutable flow of selected songs, preserving selection order.
     */
    val selectedSongs: StateFlow<List<Song>> = _selectedSongs.asStateFlow()
    
    /**
     * Set of selected song IDs for efficient lookup.
     */
    private val _selectedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedSongIds: StateFlow<Set<String>> = _selectedSongIds.asStateFlow()
    
    /**
     * Whether selection mode is currently active (at least one song selected).
     */
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()
    
    /**
     * Current count of selected songs.
     */
    private val _selectedCount = MutableStateFlow(0)
    val selectedCount: StateFlow<Int> = _selectedCount.asStateFlow()

    /**
     * Toggles the selection state of a song.
     * If already selected, removes it. If not selected, adds it to the end.
     *
     * @param song The song to toggle
     */
    fun toggleSelection(song: Song) {
        synchronized(mutationLock) {
            val currentList = _selectedSongs.value
            val currentIds = _selectedSongIds.value
            val (newList, newIds) = if (song.id in currentIds) {
                currentList.filter { it.id != song.id } to (currentIds - song.id)
            } else {
                (currentList + song) to (currentIds + song.id)
            }
            updateStateLocked(newList, newIds)
        }
    }

    /**
     * Selects all songs from the provided list.
     * Previously selected songs that are in the new list maintain their position.
     * New songs are appended in their list order.
     *
     * @param songs The complete list of songs to select
     */
    fun selectAll(songs: List<Song>) {
        synchronized(mutationLock) {
            val currentIds = _selectedSongIds.value
            val currentList = _selectedSongs.value.toMutableList()

            // Add songs that aren't already selected
            songs.forEach { song ->
                if (!currentIds.contains(song.id)) {
                    currentList.add(song)
                }
            }

            val newIds = currentList.map { it.id }.toSet()
            updateStateLocked(currentList, newIds)
        }
    }

    /**
     * Clears all selected songs, exiting selection mode.
     */
    fun clearSelection() {
        synchronized(mutationLock) {
            updateStateLocked(emptyList(), emptySet())
        }
    }

    /**
     * Checks if a song is currently selected.
     *
     * @param songId The ID of the song to check
     * @return True if the song is selected, false otherwise
     */
    fun isSelected(songId: String): Boolean {
        return _selectedSongIds.value.contains(songId)
    }

    /**
     * Gets the selection index (1-based) of a song for display purposes.
     * Returns null if the song is not selected.
     *
     * @param songId The ID of the song
     * @return 1-based selection index, or null if not selected
     */
    fun getSelectionIndex(songId: String): Int? {
        val index = _selectedSongs.value.indexOfFirst { it.id == songId }
        return if (index >= 0) index + 1 else null
    }

    /**
     * Removes a specific song from selection if it exists.
     * Useful when a song is deleted from the library.
     *
     * @param songId The ID of the song to remove
     */
    fun removeFromSelection(songId: String) {
        synchronized(mutationLock) {
            val currentIds = _selectedSongIds.value
            if (songId !in currentIds) return
            val newList = _selectedSongs.value.filter { it.id != songId }
            updateStateLocked(newList, currentIds - songId)
        }
    }

    /**
     * Updates all four state flows. Callers MUST hold [mutationLock] so the
     * four `.value =` assignments land without an interleaving mutation
     * leaving the ids/list/count/mode flows out of sync.
     */
    private fun updateStateLocked(songs: List<Song>, ids: Set<String>) {
        _selectedSongs.value = songs
        _selectedSongIds.value = ids
        _selectedCount.value = songs.size
        _isSelectionMode.value = songs.isNotEmpty()
    }
}
