package net.productberlin.domain.usecase

import net.productberlin.domain.entity.WeeklyRanking
import net.productberlin.domain.repository.StartupRepository

class GetWeeklyRanking(
    private val repository: StartupRepository,
) {
    suspend operator fun invoke(): WeeklyRanking =
        repository.getWeeklyRanking().also { ranking ->
            require(
                ranking.startups
                    .map { it.id }
                    .distinct()
                    .size == ranking.startups.size,
            ) {
                "Startup IDs must be unique within a ranking"
            }
        }
}
