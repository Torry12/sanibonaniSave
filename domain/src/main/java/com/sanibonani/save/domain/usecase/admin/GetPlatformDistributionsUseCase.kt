package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.Group
import javax.inject.Inject

/**
 * Encapsulates the logic for deriving geographic and segment distributions
 * from a list of groups.
 */
class GetPlatformDistributionsUseCase @Inject constructor() {

    data class PlatformDistributions(
        val provinceDistribution: List<Pair<String, Int>>,
        val marketSegments: List<Pair<String, Int>>,
        val groupNames: Map<String, String>,
        val groupProvinces: Map<String, String>
    )

    operator fun invoke(groups: List<Group>): PlatformDistributions {
        val provinceDist = groups.groupBy { it.province }
            .map { (it.key ?: "Unknown") to it.value.size }
            .sortedByDescending { it.second }

        val marketDist = groups.groupBy { it.type.name }
            .map { it.key to it.value.size }
            .sortedByDescending { it.second }

        val groupNameMap = groups.associate { (it.id ?: "") to it.name }
        val groupProvinceMap = groups.associate { (it.id ?: "") to (it.province ?: "Other") }

        return PlatformDistributions(
            provinceDistribution = provinceDist,
            marketSegments = marketDist,
            groupNames = groupNameMap,
            groupProvinces = groupProvinceMap
        )
    }
}
