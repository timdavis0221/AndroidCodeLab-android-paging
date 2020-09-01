package com.example.android.codelabs.paging.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.example.android.codelabs.paging.api.GithubService
import com.example.android.codelabs.paging.api.IN_QUALIFIER
import com.example.android.codelabs.paging.db.RemoteKeys
import com.example.android.codelabs.paging.db.RepoDatabase
import com.example.android.codelabs.paging.model.Repo
import retrofit2.HttpException
import java.io.IOException
import java.io.InvalidObjectException

private const val GITHUB_STARTING_PAGE_INDEX = 1

@OptIn(ExperimentalPagingApi::class)
class GithubRemoteMediator(
        private val queryString: String,
        private val service: GithubService,
        private val repoDatabase: RepoDatabase
): RemoteMediator<Int, Repo>() {

    override suspend fun load(loadType: LoadType, state: PagingState<Int, Repo>): MediatorResult {

        // Find out what page we need to load from the network, based on the LoadType
        val page = when (loadType) {
            LoadType.REFRESH -> {

            }
            LoadType.PREPEND -> {

            }
            LoadType.APPEND -> {
                val remoteKeys = getRemoteKeyForLastItem(state)
                if (remoteKeys?.nextKey == null) {
                    throw InvalidObjectException("Remote key should not be null dor $loadType")
                }
                remoteKeys.nextKey
            }
        }

        val apiQuery = queryString + IN_QUALIFIER

        return try {
            val apiResponse = service.searchRepos(apiQuery, page, state.config.pageSize)
            val repos = apiResponse.items
            val endOfPaginationReached = repos.isEmpty()
            doDbOperation(loadType, page, endOfPaginationReached, repos)
            MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)
        } catch (exception: HttpException) {
            MediatorResult.Error(exception)
        } catch (exception: IOException) {
            MediatorResult.Error(exception)
        }
    }

    private suspend fun getRemoteKeyForLastItem(state: PagingState<Int, Repo>): RemoteKeys? {
        // Get the last page that was retrieved, that contained items.
        return state.pages.lastOrNull {
            // From that last page, get the last item
            it.data.isNotEmpty()
        }?.data?.lastOrNull()?.let {
            // Get the remote keys of the last item retrieved
            repo -> repoDatabase.remoteKeysDao().remoteKeysRepoId(repo.id.toString())
        }
    }

    private suspend fun doDbOperation(
            loadType: LoadType,
            page: Unit,
            endOfPaginationReached: Boolean,
            repos: List<Repo>
    ) {
        repoDatabase.withTransaction {
            // new query
            if (loadType == LoadType.REFRESH) {
                repoDatabase.remoteKeysDao().clearRemoteKeys()
                repoDatabase.reposDao().clearRepos()
            }
            val prevKey = if (page == GITHUB_STARTING_PAGE_INDEX) null else page - 1
            val nextKey = if (endOfPaginationReached) null else page + 1

            // transform List<Repo> to List<RemoteKeys> for db operation
            val keys = repos.map {
                RemoteKeys(repoId = it.id, prevKey = prevKey, nextKey = nextKey)
            }
            repoDatabase.remoteKeysDao().insertAll(keys)
            repoDatabase.reposDao().insertAll(repos)
        }
    }

}