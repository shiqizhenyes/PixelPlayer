package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.data.model.Playlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * State holder for multi-selection functionality for playlists in LibraryScreen.
 * Manages playlist selection state with order preservation.
 *
 * Selection order is maintained - the first selected playlist is at index 0,
 * subsequent selections are appended in the order they were selected.
 */
@Singleton
class PlaylistSelectionStateHolder @Inject constructor() {

    // Guards multi-flow mutations so a reader of the four exposed StateFlows
    // observes a coherent final state. `_selectedPlaylists.update {}` alone
    // only made one flow atomic; the sibling writes for ids/count/mode could
    // race with another toggle landing in the gap. A single synchronized
    // block around the whole read-modify-write closes that gap.
    private val mutationLock = Any()

    // Internal mutable state - uses List to preserve selection order
    private val _selectedPlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    
    /**
     * Immutable flow of selected playlists, preserving selection order.
     */
    val selectedPlaylists: StateFlow<List<Playlist>> = _selectedPlaylists.asStateFlow()
    
    /**
     * Set of selected playlist IDs for efficient lookup.
     */
    private val _selectedPlaylistIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedPlaylistIds: StateFlow<Set<String>> = _selectedPlaylistIds.asStateFlow()
    
    /**
     * Whether selection mode is currently active (at least one playlist selected).
     */
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()
    
    /**
     * Current count of selected playlists.
     */
    private val _selectedCount = MutableStateFlow(0)
    val selectedCount: StateFlow<Int> = _selectedCount.asStateFlow()

    /**
     * Toggles the selection state of a playlist.
     * If already selected, removes it. If not selected, adds it to the end.
     *
     * @param playlist The playlist to toggle
     */
    fun toggleSelection(playlist: Playlist) {
        synchronized(mutationLock) {
            val currentList = _selectedPlaylists.value
            val currentIds = _selectedPlaylistIds.value
            val (newList, newIds) = if (playlist.id in currentIds) {
                currentList.filter { it.id != playlist.id } to (currentIds - playlist.id)
            } else {
                (currentList + playlist) to (currentIds + playlist.id)
            }
            updateStateLocked(newList, newIds)
        }
    }

    /**
     * Selects all playlists from the provided list.
     * Previously selected playlists that are in the new list maintain their position.
     * New playlists are appended in their list order.
     *
     * @param playlists The complete list of playlists to select
     */
    fun selectAll(playlists: List<Playlist>) {
        synchronized(mutationLock) {
            val currentIds = _selectedPlaylistIds.value
            val currentList = _selectedPlaylists.value.toMutableList()

            // Add playlists that aren't already selected
            playlists.forEach { playlist ->
                if (!currentIds.contains(playlist.id)) {
                    currentList.add(playlist)
                }
            }

            val newIds = currentList.map { it.id }.toSet()
            updateStateLocked(currentList, newIds)
        }
    }

    /**
     * Clears all selected playlists, exiting selection mode.
     */
    fun clearSelection() {
        synchronized(mutationLock) {
            updateStateLocked(emptyList(), emptySet())
        }
    }

    /**
     * Checks if a playlist is currently selected.
     *
     * @param playlistId The ID of the playlist to check
     * @return True if the playlist is selected, false otherwise
     */
    fun isSelected(playlistId: String): Boolean {
        return _selectedPlaylistIds.value.contains(playlistId)
    }

    /**
     * Gets the selection index (1-based) of a playlist for display purposes.
     * Returns null if the playlist is not selected.
     *
     * @param playlistId The ID of the playlist
     * @return 1-based selection index, or null if not selected
     */
    fun getSelectionIndex(playlistId: String): Int? {
        val index = _selectedPlaylists.value.indexOfFirst { it.id == playlistId }
        return if (index >= 0) index + 1 else null
    }

    /**
     * Removes a specific playlist from selection if it exists.
     * Useful when a playlist is deleted.
     *
     * @param playlistId The ID of the playlist to remove
     */
    fun removeFromSelection(playlistId: String) {
        synchronized(mutationLock) {
            val currentIds = _selectedPlaylistIds.value
            if (playlistId !in currentIds) return
            val newList = _selectedPlaylists.value.filter { it.id != playlistId }
            updateStateLocked(newList, currentIds - playlistId)
        }
    }

    /**
     * Updates all four state flows. Callers MUST hold [mutationLock] so the
     * four `.value =` assignments land without an interleaving mutation
     * leaving the ids/list/count/mode flows out of sync.
     */
    private fun updateStateLocked(playlists: List<Playlist>, ids: Set<String>) {
        _selectedPlaylists.value = playlists
        _selectedPlaylistIds.value = ids
        _selectedCount.value = playlists.size
        _isSelectionMode.value = playlists.isNotEmpty()
    }
}
