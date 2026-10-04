package net.productberlin.data.mapper

import net.productberlin.contract.StartupDto
import net.productberlin.domain.entity.Startup

internal fun StartupDto.toDomain() =
    Startup(
        id,
        name,
        description,
        category,
        movement,
        reason,
        news.map {
            it.toDomain()
        },
        mentionCount,
        logoUrl,
        logoFallbackUrl,
    )
