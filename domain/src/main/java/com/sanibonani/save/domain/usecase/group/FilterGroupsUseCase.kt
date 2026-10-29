package com.sanibonani.save.domain.usecase.group

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import javax.inject.Inject

/**
 * The browse-groups matching rule: free text over the name plus optional province and type
 * narrowing. Results are kept in province order so the list does not reshuffle as the query
 * is typed.
 */
class FilterGroupsUseCase @Inject constructor() {

    operator fun invoke(
        groups: List<Group>,
        searchQuery: String,
        provinceFilter: String?,
        typeFilter: GroupType?
    ): List<Group> = groups.filter { group ->
        (searchQuery.isBlank() || group.name.contains(searchQuery, ignoreCase = true)) &&
            (provinceFilter == null || group.province == provinceFilter) &&
            (typeFilter == null || group.type == typeFilter)
    }

    fun byProvince(groups: List<Group>): List<Pair<String, List<Group>>> =
        groups.groupBy { it.province ?: "Other" }.toList().sortedBy { it.first }
}
