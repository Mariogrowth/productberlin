package net.productberlin.domain.entity

/** The weekly news sections, in display order. */
enum class NewsCategory(
    val title: String,
) {
    Funding("Funding"),
    Launches("Launches"),
    Growth("Growth & partnerships"),
    People("People"),
    Trouble("Trouble"),

    /** Interviews, features and opinion: shown only when chosen, never in the default view. */
    Other("Other"),
}
