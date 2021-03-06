package com.ersiver.newsster.ui.list

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.paging.CombinedLoadStates
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadState
import androidx.preference.PreferenceManager
import com.ersiver.newsster.R
import com.ersiver.newsster.databinding.ArticleListFragmentBinding
import com.ersiver.newsster.model.Article
import com.ersiver.newsster.ui.list.ArticleAdapter.ArticleViewClick
import com.ersiver.newsster.util.DEFAULT_LANGUAGE
import com.ersiver.newsster.util.EventObserver
import com.ersiver.newsster.util.PREF_LANGUAGE_KEY
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
@ExperimentalPagingApi
class ArticleListFragment : Fragment() {
    private val viewModel by viewModels<ArticleListViewModel>()
    private var job: Job? = null
    private lateinit var adapter: ArticleAdapter
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var toolbar: Toolbar
    private var _binding: ArticleListFragmentBinding? = null
    private val binding
        get() = _binding!!

    override
    fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = ArticleListFragmentBinding.inflate(inflater, container, false)
        toolbar = binding.homeToolbar

        getPrefs()
        initAdapter()
        getNewsAndNotifyAdapter()
        showCategoryPopupMenu()
        setupNavigationToArticle()

        viewModel.categoryLiveData.observe(viewLifecycleOwner, Observer {
            updateToolbarTitle(it)
            getNewsAndNotifyAdapter()
        })

        viewModel.languageLiveData.observe(viewLifecycleOwner, Observer {
            getNewsAndNotifyAdapter()
        })

        return binding.root
    }

    private fun getPrefs() {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val language = sharedPreferences.getString(PREF_LANGUAGE_KEY, DEFAULT_LANGUAGE).toString()
        viewModel.updateLanguage(language)
    }

    private fun initAdapter() {
        adapter = ArticleAdapter(object : ArticleViewClick {
            override fun onClick(view: View, article: Article) {
                viewModel.openArticle(article)
            }
        })

        binding.articleList.adapter = adapter.withLoadStateHeaderAndFooter(
            header = NetworkStateAdapter(adapter),
            footer = NetworkStateAdapter(adapter)
        )

        adapter.addLoadStateListener { loadState ->
            binding.articleList.isVisible = loadState.refresh is LoadState.NotLoading
            binding.progress.isVisible = loadState.refresh is LoadState.Loading
            scrollToTopWhenRefreshed(loadState)
            manageErrors(loadState)
        }
    }

    private fun setupNavigationToArticle() {
        viewModel.navigateToArticleEvent.observe(
            viewLifecycleOwner,
            EventObserver { article ->
                val action =
                    ArticleListFragmentDirections.actionArticleListFragmentToArticleFragment(
                        article
                    )
                this.findNavController().navigate(action)
            })
    }

    private fun getNewsAndNotifyAdapter() {
        job?.cancel()
        job = lifecycleScope.launch {
            viewModel.loadNews().collectLatest {
                adapter.submitData(it)
            }
        }
    }

    private fun updateToolbarTitle(category: String) {
        toolbar.title = resources.getString(R.string.app_name) + " $category"
    }

    private fun scrollToTopWhenRefreshed(loadState: CombinedLoadStates) {
        if (loadState.refresh == LoadState.Loading)
            binding.articleList.scrollToPosition(0)
    }

    private fun manageErrors(loadState: CombinedLoadStates) {
        binding.retryButton.isVisible = loadState.refresh is LoadState.Error
        binding.retryButton.setOnClickListener { adapter.retry() }

        binding.errorText.isVisible = loadState.refresh is LoadState.Error
        val errorState = loadState.source.append as? LoadState.Error
            ?: loadState.source.prepend as? LoadState.Error
            ?: loadState.append as? LoadState.Error
            ?: loadState.prepend as? LoadState.Error
        errorState?.let {
            val errorText = resources.getString(R.string.error) + it.error.toString()
            binding.errorText.text = errorText
        }
    }

    private fun showCategoryPopupMenu() {
        binding.apply {
            toolbar.setOnMenuItemClickListener {
                if (it.itemId == R.id.settings) {
                    navigateToSettings()
                } else
                    displayCategoriesPicker()
                true
            }
        }
    }

    private fun navigateToSettings() {
        findNavController().navigate(
            ArticleListFragmentDirections
                .actionArticleListFragmentToSettingsFragment()
        )
    }

    private fun displayCategoriesPicker() {
        val items = resources.getStringArray(R.array.categories_titles)
        val itemsValue = resources.getStringArray(R.array.categories_values)

        MaterialAlertDialogBuilder(requireActivity(), R.style.NewssterMaterialAlertDialog)
            .setTitle(resources.getString(R.string.dialog_title))
            .setIcon(R.drawable.ic_logo)
            .setItems(items) { _, which ->
                val category = itemsValue[which]

                viewModel.updateCategory(category)
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}