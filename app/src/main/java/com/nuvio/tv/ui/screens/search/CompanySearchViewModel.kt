package com.nuvio.tv.ui.screens.search

import androidx.lifecycle.ViewModel
import com.nuvio.tv.core.tmdb.TmdbCollectionSourceResolver
import com.nuvio.tv.data.remote.api.TmdbCompanySearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CompanySearchViewModel @Inject constructor(
    private val source: TmdbCollectionSourceResolver,
) : ViewModel() {
    suspend fun search(query: String): List<TmdbCompanySearchResult> =
        source.searchCompanies(query).filter { !it.name.isNullOrBlank() }.take(12)
}
