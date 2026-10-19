package net.productberlin.domain.entity

/** The two event filters readers see. */
enum class EventKind(
    val title: String,
) {
    /** Founders, investors, pitches, demo days, product and startup community. */
    Startup("Startups & founders"),

    /** Engineering, AI and data talks, meetups and workshops. */
    Tech("Tech & AI"),
}
