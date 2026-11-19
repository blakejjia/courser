package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Genre
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.CourseProgressRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// --- Model Types for Sectioned Display ---

enum class SortOption { ARTIST, ALBUM, TITLE }

data class AlbumData(
    val name: String,
    val artUri: String?,
    val songs: List<Song>
)

sealed class SectionData {
    abstract val id: String

    data class ArtistSection(
        override val id: String,
        val artistName: String,
        val albums: List<AlbumData>
    ) : SectionData()

    data class AlbumSection(
        override val id: String,
        val album: AlbumData
    ) : SectionData()

    data class FlatList(
        val songs: List<Song>
    ) : SectionData() {
        override val id = "flat_list"
    }
}

/**
 * Representa un elemento visual en la lista aplanada para mejorar el rendimiento de LazyColumn.
 */
sealed class GenreDetailListItem {
    abstract val key: String

    data class ArtistHeader(
        override val key: String,
        val artistName: String,
        val artistImageUrl: String? = null
    ) : GenreDetailListItem()

    data class AlbumHeader(
        override val key: String,
        val album: AlbumData,
        val useArtistStyle: Boolean
    ) : GenreDetailListItem()

    data class SongItem(
        override val key: String,
        val song: Song,
        val isFirstInAlbum: Boolean,
        val isLastInAlbum: Boolean,
        val isLastAlbumInSection: Boolean,
        val useArtistStyle: Boolean
    ) : GenreDetailListItem()

    data class Spacer(
        override val key: String,
        val heightDp: Int,
        val useSurfaceBackground: Boolean = false
    ) : GenreDetailListItem()

    data class Divider(
        override val key: String
    ) : GenreDetailListItem()
}

data class GenreDetailUiState(
    val genre: Genre? = null,
    val songs: List<Song> = emptyList(),
    val sortedSongs: List<Song> = emptyList(), // 为播放逻辑预留的已排序副本
    val displaySections: List<SectionData> = emptyList(),
    val flattenedItems: List<GenreDetailListItem> = emptyList(),
    val sortOption: SortOption = SortOption.ARTIST,
    val isLoadingGenreName: Boolean = false,
    val isLoadingSongs: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class GenreDetailViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val savedStateHandle: SavedStateHandle,
    private val courseProgressRepository: CourseProgressRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(GenreDetailUiState())
    val uiState: StateFlow<GenreDetailUiState> = _uiState.asStateFlow()

    private var artistMap: Map<String, String?> = emptyMap()
    private var genreDetailsJob: Job? = null

    init {
        savedStateHandle.get<String>("genreId")?.let { genreId ->
            val decodedGenreId = java.net.URLDecoder.decode(genreId, "UTF-8")
            loadGenreDetails(decodedGenreId)
        } ?: run {
            _uiState.update { it.copy(error = "Genre ID not found", isLoadingGenreName = false, isLoadingSongs = false) }
        }
    }

    private data class ProcessingResult(
        val genre: Genre,
        val songs: List<Song>,
        val sortedSongs: List<Song>,
        val sections: List<SectionData>,
        val flattened: List<GenreDetailListItem>
    )

    private fun loadGenreDetails(genreId: String) {
        genreDetailsJob?.cancel()
        genreDetailsJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingGenreName = true, isLoadingSongs = true, error = null) }

            try {
                // Step 1: Fast load of the Genre object to stabilize the UI theme as early as possible.
                val initialGenre = withContext(Dispatchers.Default) {
                    val genres = musicRepository.getGenres().first()
                    genres.find { it.id.equals(genreId, ignoreCase = true) }
                }

                if (initialGenre != null) {
                    _uiState.update { it.copy(genre = initialGenre, isLoadingGenreName = false) }
                }

                val genre = initialGenre ?: Genre(
                    id = genreId,
                    name = genreId.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                    lightColorHex = "#9E9E9E", onLightColorHex = "#000000",
                    darkColorHex = "#616161", onDarkColorHex = "#FFFFFF"
                )

                val rawSongsFlow = musicRepository.getMusicByGenre(genre.name)
                val songsFlow = courseProgressRepository?.enrichSongsFlow(rawSongsFlow) ?: rawSongsFlow
                val artistsFlow = musicRepository.getArtists()

                combine(songsFlow, artistsFlow) { songs, artists ->
                    artistMap = artists.associate { it.name.trim().lowercase() to it.imageUrl }
                    val currentSort = _uiState.value.sortOption
                    val sections = buildDisplaySections(songs, currentSort)
                    val flattened = flattenSections(sections, artistMap)
                    val sorted = when (currentSort) {
                        SortOption.ARTIST -> songs.sortedBy { it.artist ?: "Unknown Artist" }
                        SortOption.ALBUM -> songs.sortedBy { it.album ?: "Unknown Album" }
                        SortOption.TITLE -> songs.sortedBy { it.title }
                    }
                    ProcessingResult(genre, songs, sorted, sections, flattened)
                }.collect { result ->
                    _uiState.update {
                        it.copy(
                            genre = result.genre,
                            songs = result.songs,
                            sortedSongs = result.sortedSongs,
                            displaySections = result.sections,
                            flattenedItems = result.flattened,
                            isLoadingGenreName = false,
                            isLoadingSongs = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = "Failed to load genre details: ${e.message}",
                        isLoadingGenreName = false,
                        isLoadingSongs = false
                    )
                }
            }
        }
    }

    /**
     * Updates the sort option and re-groups the songs.
     */
    fun updateSortOption(newSort: SortOption) {
        val currentState = _uiState.value
        if (currentState.sortOption == newSort) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSongs = true) }
            val (updatedSections, updatedFlattened, updatedSorted) = withContext(Dispatchers.Default) {
                val sections = buildDisplaySections(currentState.songs, newSort)
                val flattened = flattenSections(sections, artistMap)
                val sorted = when (newSort) {
                    SortOption.ARTIST -> currentState.songs.sortedBy { it.artist ?: "Unknown Artist" }
                    SortOption.ALBUM -> currentState.songs.sortedBy { it.album ?: "Unknown Album" }
                    SortOption.TITLE -> currentState.songs.sortedBy { it.title }
                }
                Triple(sections, flattened, sorted)
            }
            _uiState.update {
                it.copy(
                    sortOption = newSort,
                    displaySections = updatedSections,
                    flattenedItems = updatedFlattened,
                    sortedSongs = updatedSorted,
                    isLoadingSongs = false
                )
            }
        }
    }

    private fun flattenSections(sections: List<SectionData>, artistMap: Map<String, String?> = emptyMap()): List<GenreDetailListItem> {
        val items = mutableListOf<GenreDetailListItem>()
        sections.forEach { section ->
            when (section) {
                is SectionData.ArtistSection -> {
                    items.add(GenreDetailListItem.ArtistHeader(
                        key = "header_${section.id}", 
                        artistName = section.artistName,
                        artistImageUrl = artistMap[section.artistName.trim().lowercase()]
                    ))
                    section.albums.forEachIndexed { albumIndex, album ->
                        if (albumIndex > 0) {
                            items.add(GenreDetailListItem.Divider("divider_${section.id}_$albumIndex"))
                        }
                        
                        items.add(GenreDetailListItem.AlbumHeader("${section.id}_album_header_${album.name}", album, true))
                        items.add(GenreDetailListItem.Spacer("${section.id}_album_spacer_${album.name}", 10, true))
                        
                        album.songs.forEachIndexed { songIndex, song ->
                            items.add(GenreDetailListItem.SongItem(
                                key = "${section.id}_${album.name}_${song.id}",
                                song = song,
                                isFirstInAlbum = songIndex == 0,
                                isLastInAlbum = songIndex == album.songs.lastIndex,
                                isLastAlbumInSection = albumIndex == section.albums.lastIndex,
                                useArtistStyle = true
                            ))
                        }
                    }
                }
                is SectionData.AlbumSection -> {
                    val album = section.album
                    items.add(GenreDetailListItem.AlbumHeader("${section.id}_album_header_${album.name}", album, false))
                    items.add(GenreDetailListItem.Spacer("${section.id}_album_spacer_${album.name}", 10, true))
                    
                    album.songs.forEachIndexed { songIndex, song ->
                        items.add(GenreDetailListItem.SongItem(
                            key = "${section.id}_${song.id}",
                            song = song,
                            isFirstInAlbum = songIndex == 0,
                            isLastInAlbum = songIndex == album.songs.lastIndex,
                            isLastAlbumInSection = true,
                            useArtistStyle = false
                        ))
                    }
                }
                is SectionData.FlatList -> {
                    section.songs.forEach { song ->
                        items.add(GenreDetailListItem.SongItem(
                            key = "flat_${song.id}",
                            song = song,
                            isFirstInAlbum = false,
                            isLastInAlbum = false,
                            isLastAlbumInSection = false,
                            useArtistStyle = false
                        ))
                    }
                }
            }
            items.add(GenreDetailListItem.Spacer("section_spacer_${section.id}", 16))
        }
        return items
    }

    private fun buildDisplaySections(songs: List<Song>, sort: SortOption): List<SectionData> {
        return when (sort) {
            SortOption.ARTIST -> {
                val sorted = songs.sortedBy { it.artist ?: "Unknown Artist" }
                val grouped = sorted.groupBy { it.artist ?: "Unknown Artist" }
                grouped.map { (artist, artistSongs) ->
                    val albums = artistSongs.groupBy { it.album ?: "Unknown Album" }.map { (albumName, albumSongs) ->
                        val sortedAlbumSongs = albumSongs.sortedWith(
                            compareBy<Song> { it.discNumber ?: 1 }
                                .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                                .thenBy { it.title.lowercase() }
                        )
                        AlbumData(albumName, sortedAlbumSongs.firstOrNull()?.albumArtUriString, sortedAlbumSongs)
                    }
                    SectionData.ArtistSection("artist_$artist", artist, albums)
                }
            }
            SortOption.ALBUM -> {
                val sorted = songs.sortedBy { it.album ?: "Unknown Album" }
                val grouped = sorted.groupBy { it.album ?: "Unknown Album" }
                grouped.map { (album, albumSongs) ->
                    val sortedAlbumSongs = albumSongs.sortedWith(
                        compareBy<Song> { it.discNumber ?: 1 }
                            .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                            .thenBy { it.title.lowercase() }
                    )
                    SectionData.AlbumSection(
                        "album_$album",
                        AlbumData(album, sortedAlbumSongs.firstOrNull()?.albumArtUriString, sortedAlbumSongs)
                    )
                }
            }
            SortOption.TITLE -> {
                listOf(SectionData.FlatList(songs.sortedBy { it.title }))
            }
        }
    }
}

