package com.example.data.local

import kotlinx.coroutines.flow.Flow

class InstaLensRepository(private val dao: InstaLensDao) {
    val savedLinks: Flow<List<SavedProfileLinkEntity>> = dao.getAllSavedLinks()
    val searchHistory: Flow<List<SearchHistoryEntity>> = dao.getAllSearchHistory()

    suspend fun getSavedLinksSnapshot(): List<SavedProfileLinkEntity> = dao.getAllSavedLinksSnapshot()

    suspend fun insertSavedLink(link: SavedProfileLinkEntity): Long = dao.insertSavedLink(link)

    suspend fun deleteSavedLink(id: Int) = dao.deleteSavedLinkById(id)

    suspend fun insertHistoryItem(item: SearchHistoryEntity): Long = dao.insertSearchHistory(item)

    suspend fun deleteHistoryItem(id: Int) = dao.deleteSearchHistoryById(id)

    suspend fun clearAllHistory() = dao.clearSearchHistory()
}
