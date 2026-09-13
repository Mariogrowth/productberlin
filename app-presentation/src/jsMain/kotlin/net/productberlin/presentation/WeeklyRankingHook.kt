package net.productberlin.presentation

import kotlinx.coroutines.CancellationException
import net.productberlin.domain.state.RankingState
import net.productberlin.domain.usecase.GetWeeklyRanking
import react.useEffect
import react.useState

/** The wrapper's coroutine effect cancels loading on unmount or dependency changes. */
internal fun useWeeklyRanking(
    getRanking: GetWeeklyRanking,
    attempt: Int,
): RankingState {
    var state by useState<RankingState>(RankingState.Loading)
    useEffect(getRanking, attempt) {
        state = RankingState.Loading
        state =
            try {
                RankingState.Ready(getRanking())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                RankingState.Failed
            }
    }
    return state
}
