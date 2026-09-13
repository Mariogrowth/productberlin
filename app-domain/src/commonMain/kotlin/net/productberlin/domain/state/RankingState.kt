package net.productberlin.domain.state

import net.productberlin.domain.entity.WeeklyRanking

sealed interface RankingState {
    data object Loading : RankingState

    data class Ready(
        val ranking: WeeklyRanking,
    ) : RankingState

    data object Failed : RankingState
}
