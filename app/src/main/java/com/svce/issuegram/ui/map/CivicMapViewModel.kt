package com.svce.issuegram.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.svce.issuegram.data.mapper.IssueMapper
import com.svce.issuegram.domain.usecase.issue.GetFeedIssuesUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class CivicMapViewModel(
    getFeedIssuesUseCase: GetFeedIssuesUseCase
) : ViewModel() {

    val issues = getFeedIssuesUseCase()
        .map { list -> list.map { IssueMapper.toCivicDto(it) } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
