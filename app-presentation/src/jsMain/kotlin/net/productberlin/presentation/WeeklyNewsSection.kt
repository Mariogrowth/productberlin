package net.productberlin.presentation

import net.productberlin.domain.entity.NewsCategory
import net.productberlin.domain.entity.WeeklyNews
import net.productberlin.presentation.designsystem.layouts.filters.FilterBar
import net.productberlin.presentation.designsystem.layouts.filters.FilterOption
import react.FC
import react.Key
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.section
import react.dom.html.ReactHTML.span
import react.useState
import web.cssom.ClassName
import web.dom.ElementId

external interface WeeklyNewsSectionProps : Props {
    var news: WeeklyNews
}

/**
 * "This week in Berlin startups", under the ranking. "All" shows every category except Other (interviews, features
 * and opinion), each under its heading; choosing a category shows only that one. Empty categories have no chip.
 */
val WeeklyNewsSection =
    FC<WeeklyNewsSectionProps> { props ->
        var selected by useState(ALL)
        val stories = props.news.stories
        val shown = stories.filter { it.category != NewsCategory.Other }
        val counts = stories.groupingBy { it.category }.eachCount()
        val options =
            listOf(FilterOption(ALL, "All (${shown.size})")) +
                NewsCategory.entries.mapNotNull { category ->
                    counts[category]?.let { FilterOption(category.name, "${category.title} ($it)") }
                }
        section {
            className = ClassName("weekly-news")
            ariaLabelledBy = ElementId("weekly-news-title")
            div {
                className = ClassName("ranking-meta")
                h2 {
                    id = ElementId("weekly-news-title")
                    +"This week in Berlin startups"
                }
                span { +props.news.weekLabel }
            }
            div {
                className = ClassName("pb-theme weekly-news-filters")
                FilterBar {
                    label = "News categories"
                    this.options = options
                    selectedId = selected
                    onSelect = { selected = it }
                }
            }
            val chosen = NewsCategory.entries.firstOrNull { it.name == selected }
            if (chosen == null) {
                if (shown.isEmpty()) {
                    p {
                        className = ClassName("status-message")
                        +"No company news this week beyond interviews and features."
                    }
                }
                for (category in NewsCategory.entries.filter { it != NewsCategory.Other }) {
                    val inCategory = shown.filter { it.category == category }
                    if (inCategory.isEmpty()) continue
                    div {
                        key = Key(category.name)
                        className = ClassName("weekly-news-group")
                        h3 { +category.title }
                        inCategory.forEach {
                            WeeklyStoryItem {
                                key = Key(it.id)
                                story = it
                            }
                        }
                    }
                }
            } else {
                div {
                    className = ClassName("weekly-news-group")
                    stories.filter { it.category == chosen }.forEach {
                        WeeklyStoryItem {
                            key = Key(it.id)
                            story = it
                        }
                    }
                }
            }
        }
    }

private const val ALL = "all"
