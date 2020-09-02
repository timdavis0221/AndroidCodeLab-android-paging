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
                getRemoteKeyClosestToCurrentPosition(state)
                        ?.nextKey?.minus(1) ?: GITHUB_STARTING_PAGE_INDEX
            }
            LoadType.PREPEND -> {
                // The LoadType is PREPEND so some data was loaded before,
                // so we should have been able to get remote keys
                // If the remoteKeys are null, then we're an invalid state and we have a bug
                val remoteKeys = getRemoteKeyForFirstItem(state)
                        ?: throw InvalidObjectException("Remote Key and the prevKey should not null")

                remoteKeys.prevKey
                        ?: return MediatorResult.Success(endOfPaginationReached = true)

//                remoteKeys.prevKey
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

    private suspend fun getRemoteKeyClosestToCurrentPosition(
            state: PagingState<Int, Repo>
    ): RemoteKeys? {
        return state.anchorPosition?.let {
            position -> state.closestItemToPosition(position)?.id
                .let { repoId ->
                    repoDatabase.remoteKeysDao().remoteKeysRepoId(repoId.toString())
                }
        }
    }

    private suspend fun getRemoteKeyForFirstItem(state: PagingState<Int, Repo>): RemoteKeys? {
        return state.pages.firstOrNull {
            it.data.isNotEmpty()
        }?.data?.firstOrNull()?.let {
            repo -> repoDatabase.remoteKeysDao().remoteKeysRepoId(repo.id.toString())
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
            page: Int,
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