/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.codelabs.paging.ui

import androidx.lifecycle.*
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import com.example.android.codelabs.paging.data.GithubRepository
import com.example.android.codelabs.paging.model.Repo
import com.example.android.codelabs.paging.model.RepoSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * ViewModel for the [SearchRepositoriesActivity] screen.
 * The ViewModel works with the [GithubRepository] to get the data.
 */
@ExperimentalCoroutinesApi
class SearchRepositoriesViewModel(private val repository: GithubRepository) : ViewModel() {

   /* companion object {
        private const val VISIBLE_THRESHOLD = 5
    }*/

    private var currentQueryString: String? = null

    private var currentSearchResult: Flow<PagingData<UiModel>>? = null

    // Instead of using a LiveData object for each new query, we can just use a String, see below code in searchRepo()
//    private val queryLiveData = MutableLiveData<String>()

    // in-memory cache for result searches that survives configuration changes
    /*val repoResult: LiveData<RepoSearchResult> = queryLiveData.switchMap { queryString ->
        liveData {
            // With Paging 3.0 we don't need to convert our Flow to LiveData anymore.
            val repos = repository.getSearchResultStream(queryString).asLiveData(Dispatchers.Main)
            emitSource(repos)
        }
    }*/

    private val UiModel.RepoItem.roundedStarCount: Int
        get() = this.repo.stars / 10_000

    /**
     * Search a repository based on a query string.
     */
    fun searchRepo(queryString: String): Flow<PagingData<UiModel>> {

        var lastResult = currentSearchResult

        if (queryString == currentQueryString && lastResult != null) {
            return lastResult
        }
        // This will help us ensure that whenever we get a new search query that is the same as the current query
        currentQueryString = queryString

        // Flow<PagingData> has a handy cachedIn() method that allows us to cache the content of a Flow<PagingData>
        // in a CoroutineScope. Since we're in a ViewModel
        //
        // If you're doing any operations on the Flow, like map or filter,
        // make sure you call cachedIn after you execute these operations
        // to ensure you don't need to trigger them again.
        val newResult: Flow<PagingData<UiModel>> =
                repository.getSearchResultStream(queryString)
                        .map { pagingData -> pagingData.map { UiModel.RepoItem(it) } }
                        .map {
                            it.insertSeparators<UiModel.RepoItem, UiModel> { before, after ->
                                if (after == null) {
                                    return@insertSeparators null
                                }
                                if (before == null) {
                                    return@insertSeparators UiModel.SeparatorItem(
                                            "${after.roundedStarCount}0.000+ Stars"
                                    )
                                }

                                if (before.roundedStarCount > after.roundedStarCount) {
                                    if (after.roundedStarCount > 1) {
                                        UiModel.SeparatorItem(
                                                "${after.roundedStarCount}0.000+ Stars"
                                        )
                                    } else {
                                        UiModel.SeparatorItem("< 10.000 stars")
                                    }
                                } else {
                                    null
                                }
                            }
                        }
                        .cachedIn(viewModelScope)

        currentSearchResult = newResult
        return newResult
//        queryLiveData.postValue(queryString)
    }

    // Paging library will handle this
   /* fun listScrolled(visibleItemCount: Int, lastVisibleItemPosition: Int, totalItemCount: Int) {
        if (visibleItemCount + lastVisibleItemPosition + VISIBLE_THRESHOLD >= totalItemCount) {
            val immutableQuery = queryLiveData.value
            if (immutableQuery != null) {
                viewModelScope.launch {
                    repository.requestMore(immutableQuery)
                }
            }
        }
    }*/
}