package net.productberlin.domain.entity

/** What a headline says happened, and how much it counts towards a company's press momentum. */
enum class HeadlineEvent(
    val weight: Int,
) {
    /** A funding round, investment or valuation. */
    Funding(3),

    /** Something new exists: a product, app, service, route or offer. */
    Launch(2),

    /** Traction: revenue or ARR milestones, expansion, customers, partnerships, acquisitions, awards. */
    Growth(2),

    /** A new chief executive, finance or technology chief, a key hire or founder move. */
    People(2),

    /** Any other coverage naming the company: interviews, features, commentary. */
    Other(1),

    /** Layoffs, closures, insolvency, lawsuits, hacks or other trouble: reported, but never a reason to rank. */
    Negative(0),

    /** Share-price notes and market columns, published near-daily for listed companies. */
    Market(0),

    /** Promo codes, fee comparisons and consumer reviews about a company's product. */
    Consumer(0),
    ;

    /** Where the story appears in the weekly news; market notes and consumer content are not shown at all. */
    val newsCategory: NewsCategory?
        get() =
            when (this) {
                Funding -> NewsCategory.Funding
                Launch -> NewsCategory.Launches
                Growth -> NewsCategory.Growth
                People -> NewsCategory.People
                Negative -> NewsCategory.Trouble
                Other -> NewsCategory.Other
                Market, Consumer -> null
            }
}
