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

import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadState
import androidx.recyclerview.widget.DividerItemDecoration
import com.example.android.codelabs.paging.Injection
import com.example.android.codelabs.paging.databinding.ActivitySearchRepositoriesBinding
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@ExperimentalCoroutinesApi
class SearchRepositoriesActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchRepositoriesBinding
    private lateinit var viewModel: SearchRepositoriesViewModel
    private val adapter = ReposAdapter()

    private var searchJob: Job? = null

    /**
     *  We also want to ensure that whenever the user searches for a new query
     *  the previous query is cancelled and hold a reference to a new Job
     */
    private fun search(query: String) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            viewModel.searchRepo(query).collectLatest {
                /*value -> */adapter.submitData(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // bind activity_search_repositories.xml
        binding = ActivitySearchRepositoriesBinding.inflate(layoutInflater)

        // Trigger a reload of the PagingData
        binding.retryButton.setOnClickListener { adapter.retry() }

        val view = binding.root
        setContentView(view)

        // get the view model
        viewModel = ViewModelProvider(this, Injection.provideViewModelFactory())
                .get(SearchRepositoriesViewModel::class.java)

        // add dividers between RecyclerView's row items
        val decoration = DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        binding.list.addItemDecoration(decoration)
//        setupScrollListener()

        initAdapter()
        val query = savedInstanceState?.getString(LAST_SEARCH_QUERY) ?: DEFAULT_QUERY
       /* if (viewModel.repoResult.value == null) {
            viewModel.searchRepo(query)
        }*/
        search(query)
        initSearch(query)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(LAST_SEARCH_QUERY, binding.searchRepo.text.trim().toString())
    }

    private fun initAdapter() {
        binding.list.adapter = adapter.withLoadStateHeaderAndFooter(
                header = ReposLoadStateAdapter { adapter.retry() },
                footer = ReposLoadStateAdapter { adapter.retry() }
        )

        adapter.addLoadStateListener { loadState ->
            // only show the list if refresh succeeds.
            binding.list.isVisible = loadState.source.refresh is LoadState.NotLoading
            // show loading spinner during initial load or refresh
            binding.progressBar.isVisible = loadState.source.refresh is LoadState.Loading
            // show the retry button if initial load or refresh fails.
            binding.retryButton.isVisible = loadState.source.refresh is LoadState.Error

            // Toast on any error, regardless of whether it came from RemoteMediator or PagingSource
            val errorState = loadState.source.append as? LoadState.Error
                    ?: loadState.source.prepend as? LoadState.Error
                    ?: loadState.append as? LoadState.Error
                    ?: loadState.prepend as? LoadState.Error

            errorState?.let {
                Toast.makeText(
                        this,
                        "\"\\uD83D\\uDE28 Wooops ${it.error}\"",
                        Toast.LENGTH_LONG
                ).show()
            }
        }

       /* viewModel.repoResult.observe(this) { result ->
            when (result) {
                is RepoSearchResult.Success -> {
                    showEmptyList(result.data.isEmpty())
                    adapter.submitList(result.data)
                }
                is RepoSearchResult.Error -> {
                    Toast.makeText(
                            this,
                            "\uD83D\uDE28 Wooops $result.message}",
                            Toast.LENGTH_LONG
                    ).show()
                }
            }
        }*/
    }

    private fun initSearch(query: String) {
        binding.searchRepo.setText(query)

        binding.searchRepo.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                updateRepoListFromInput()
                true
            } else {
                false
            }
        }

        binding.searchRepo.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                updateRepoListFromInput()
                true
            } else {
                false
            }
        }
        // instead of resetting the position on new search,
        // we should reset the position when the list adapter is updated with the result of a new search
        lifecycleScope.launch {
            @OptIn(ExperimentalPagingApi::class)
            adapter.dataRefreshFlow.collect {
                binding.list.scrollToPosition(0)
            }
        }
    }

    private fun updateRepoListFromInput() {
        binding.searchRepo.text.trim().let { str ->
            if (str.isNotEmpty()) {
                // we wanted to make sure that the scroll position is reset for each new search
                // see above method initSearch to do better
//                    binding.list.scrollToPosition(0)


//                viewModel.searchRepo(it.toString())

                // replace viewModel with this.searchRepo()
                search(str.toString())
            }
        }
    }

  /*  private fun showEmptyList(show: Boolean) {
        if (show) {
            binding.emptyList.visibility = View.VISIBLE
            binding.list.visibility = View.GONE
        } else {
            binding.emptyList.visibility = View.GONE
            binding.list.visibility = View.VISIBLE
        }
    }*/

    /*private fun setupScrollListener() {
        val layoutManager = binding.list.layoutManager as LinearLayoutManager

        // Currently we use an OnScrollListener attached to the RecyclerView to know when to trigger more data.
        // Now we can let the Paging library handle list scrolling for us, so remove it !
       *//* binding.list.addOnScrollListener(object : OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val totalItemCount = layoutManager.itemCount
                val visibleItemCount = layoutManager.childCount
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()

                viewModel.listScrolled(visibleItemCount, lastVisibleItem, totalItemCount)
            }
        })*//*
    }*/

    companion object {
        private const val LAST_SEARCH_QUERY: String = "last_search_query"
        private const val DEFAULT_QUERY = "Android"
    }
}
