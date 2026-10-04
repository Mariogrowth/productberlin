package net.productberlin.worker.mapper

import net.productberlin.contract.StartupDto
import net.productberlin.domain.entity.Startup

internal fun Startup.toDto() =
    StartupDto(
        id,
        name,
        description,
        category,
        movement,
        reason,
        news.map {
            it.toDto()
        },
        mentionCount,
        logoUrl,
        logoFallbackUrl,
    )
