package net.productberlin.presentation.designsystem.showcase

import net.productberlin.presentation.designsystem.components.badges.Badge
import net.productberlin.presentation.designsystem.components.buttons.Button
import net.productberlin.presentation.designsystem.components.buttons.ButtonSize
import net.productberlin.presentation.designsystem.components.buttons.ButtonVariant
import net.productberlin.presentation.designsystem.components.icons.Icon
import net.productberlin.presentation.designsystem.components.icons.IconName
import net.productberlin.presentation.designsystem.components.inputs.TextField
import net.productberlin.presentation.designsystem.components.navigation.NavigationItem
import net.productberlin.presentation.designsystem.components.text.Text
import net.productberlin.presentation.designsystem.components.text.TextStyle
import net.productberlin.presentation.designsystem.components.text.TextWeight
import net.productberlin.presentation.designsystem.components.toggles.Checkbox
import net.productberlin.presentation.designsystem.components.toggles.Radio
import net.productberlin.presentation.designsystem.components.toggles.Switch
import net.productberlin.presentation.designsystem.layouts.cards.Card
import net.productberlin.presentation.designsystem.layouts.feedback.EmptyState
import net.productberlin.presentation.designsystem.layouts.filters.FilterBar
import net.productberlin.presentation.designsystem.layouts.filters.FilterOption
import net.productberlin.presentation.designsystem.layouts.forms.EmailSignup
import net.productberlin.presentation.designsystem.layouts.forms.EmailSignupStatus
import net.productberlin.presentation.designsystem.layouts.news.NewsFeed
import net.productberlin.presentation.designsystem.layouts.news.NewsStory
import net.productberlin.presentation.designsystem.layouts.ranking.RankedResult
import net.productberlin.presentation.designsystem.layouts.ranking.RankedResults
import net.productberlin.presentation.designsystem.layouts.tables.DenseTable
import net.productberlin.presentation.designsystem.layouts.tables.TableColumn
import net.productberlin.presentation.designsystem.layouts.tables.TableRow
import react.FC
import react.Key
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.header
import react.dom.html.ReactHTML.main
import react.dom.html.ReactHTML.nav
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.section
import react.dom.html.ReactHTML.span
import react.useState
import web.cssom.ClassName
import web.dom.ElementId

private val sampleResults =
    listOf(
        RankedResult(
            "almedia",
            1,
            "Almedia",
            "The world's smartest search engine.",
            2,
            "Sample ranking: growing product adoption and new releases.",
        ),
        RankedResult(
            "perplexity",
            2,
            "Perplexity",
            "Answer engine for trusted research.",
            2,
            "Sample ranking: new research tools launched this week.",
        ),
        RankedResult(
            "algolia",
            3,
            "Algolia",
            "Search infrastructure for product teams.",
            1,
            "Sample ranking: improved developer experience.",
        ),
        RankedResult("glean", 4, "Glean", "Workplace search across every app.", 1, "Sample ranking: broader workplace integrations."),
    )

/** A mock-only component catalogue; it deliberately creates no data container or API client. */
val DesignSystemShowcase =
    FC<Props> {
        var inputValue by useState("")
        var helpShown by useState(false)
        var query by useState("")
        var category by useState("all")
        var saved by useState(true)
        var radio by useState("one")
        var enabled by useState(true)
        var actionCount by useState(0)
        var signupEmail by useState("")
        var signupSubmitted by useState(false)
        val filters =
            listOf(
                FilterOption("all", "View all"),
                FilterOption("one", "Category one"),
                FilterOption("two", "Category two"),
                FilterOption("three", "Category three"),
                FilterOption("four", "Category four"),
            )
        val filtered =
            sampleResults.filterIndexed { index, result ->
                result.name.contains(query, ignoreCase = true) &&
                    (category == "all" || filters[index + 1].id == category)
            }
        main {
            className = ClassName("pb-theme pb-showcase")
            header {
                className = ClassName("pb-showcase-header")
                div {
                    className = ClassName("pb-pattern-toolbar")
                    Text {
                        variant = TextStyle.Heading2
                        +"Product.berlin"
                    }
                    Badge { +"DESIGN SYSTEM · 1.0" }
                }
                h1 {
                    Text {
                        variant = TextStyle.Display
                        +"A precise system for product discovery."
                    }
                }
                p { +"Foundations, components, and result patterns extracted from the Product.berlin desktop experience." }
                a {
                    href = "/"
                    +"Back to the weekly ten"
                }
            }
            section {
                className = ClassName("pb-showcase-section")
                div {
                    className = ClassName("pb-section-heading")
                    span {
                        className = ClassName("pb-eyebrow")
                        +"01 · Foundations"
                    }
                    h2 {
                        Text {
                            variant = TextStyle.Heading1
                            +"Visual language"
                        }
                    }
                }
                div {
                    className = ClassName("pb-grid")
                    div {
                        className = ClassName("pb-half")
                        Card {
                            div {
                                className = ClassName("pb-stack")
                                h3 { +"Color palette" }
                                listOf(
                                    "Ink" to "#111111",
                                    "Brand" to "#1018F5",
                                    "Success" to "#16863B",
                                    "Muted" to "#666A70",
                                    "Line" to "#E5E6E3",
                                    "Canvas" to "#F7F7F5",
                                ).forEach { (name, hex) ->
                                    div {
                                        key = Key(name)
                                        className = ClassName("pb-color")
                                        span {
                                            className = ClassName("pb-swatch pb-swatch-${name.lowercase()}")
                                            ariaHidden = true
                                        }
                                        div {
                                            div {
                                                Text {
                                                    variant = TextStyle.Label
                                                    weight = TextWeight.Bold
                                                    +name
                                                }
                                            }
                                            Text {
                                                variant = TextStyle.Caption
                                                muted = true
                                                +hex
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    div {
                        className = ClassName("pb-half")
                        Card {
                            div {
                                className = ClassName("pb-stack")
                                h3 { +"Typography · Inter" }
                                listOf(
                                    TextStyle.Display to "Search, compare, decide.",
                                    TextStyle.Heading1 to "Product intelligence",
                                    TextStyle.Heading2 to "Ranked companies",
                                    TextStyle.Heading3 to "Market leaders",
                                    TextStyle.Body to "Clear, compact copy supports quick scanning.",
                                    TextStyle.Label to "Filter by category",
                                    TextStyle.Caption to "UPDATED 4 MIN AGO",
                                ).forEach { (style, sample) ->
                                    div {
                                        key = Key(style.name)
                                        className = ClassName("pb-type-sample")
                                        p {
                                            className = ClassName("pb-demo-caption")
                                            +style.name
                                        }
                                        div {
                                            Text {
                                                variant = style
                                                weight =
                                                    if (style == TextStyle.Body) TextWeight.Medium else TextWeight.Bold
                                                +sample
                                            }
                                        }
                                        div {
                                            Text {
                                                variant = style
                                                +sample
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    div {
                        className = ClassName("pb-third")
                        Card {
                            div {
                                className = ClassName("pb-stack")
                                h3 { +"Spacing & grid" }
                                p {
                                    className = ClassName("pb-showcase-note")
                                    +"4px base unit · 12-column desktop grid · 72px margins · 24px gutters"
                                }
                                Text {
                                    variant =
                                        TextStyle.Label
                                    ; +"4 / 8 / 12 / 16 / 24 / 32"
                                }
                            }
                        }
                    }
                    div {
                        className = ClassName("pb-third")
                        Card {
                            div {
                                className = ClassName("pb-stack")
                                h3 { +"Radii, borders & shadows" }
                                Badge { +"Fully rounded" }
                                Card {
                                    elevated =
                                        true
                                    ; +"Subtle elevation"
                                }
                            }
                        }
                    }
                    div {
                        className = ClassName("pb-third")
                        Card {
                            div {
                                className = ClassName("pb-stack")
                                h3 { +"Iconography" }
                                p {
                                    className = ClassName("pb-showcase-note")
                                    +"24px line icons, 1.5px stroke, round joins. Use labels for clarity."
                                }
                                div {
                                    className = ClassName("pb-inline")
                                    listOf(
                                        IconName.Search,
                                        IconName.Filter,
                                        IconName.Back,
                                        IconName.Verified,
                                        IconName.List,
                                    ).forEach { icon ->
                                        Icon {
                                            key =
                                                Key(icon.name)
                                            ; name = icon
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            section {
                className = ClassName("pb-showcase-section")
                div {
                    className = ClassName("pb-section-heading")
                    span {
                        className = ClassName("pb-eyebrow")
                        +"02 · Components"
                    }
                    h2 {
                        Text {
                            variant = TextStyle.Heading1
                            +"Reusable interface parts"
                        }
                    }
                    p {
                        className = ClassName("pb-muted")
                        +"Compact controls and quiet surfaces keep attention on ranked product information."
                    }
                }
                div {
                    className = ClassName("pb-grid")
                    div {
                        className = ClassName("pb-full pb-demo-panel")
                        h3 {
                            Text {
                                variant = TextStyle.Heading3
                                +"Buttons"
                            }
                        }
                        div {
                            className = ClassName("pb-inline")
                            ButtonVariant.entries.forEach { style ->
                                div {
                                    key = Key(style.name)
                                    p {
                                        className = ClassName("pb-demo-caption")
                                        +style.name
                                    }
                                    Button {
                                        variant = style
                                        onClick = { actionCount++ }
                                        +"Button"
                                    }
                                }
                            }
                            div {
                                p {
                                    className = ClassName("pb-demo-caption")
                                    +"Small"
                                }
                                Button {
                                    size = ButtonSize.Small
                                    variant =
                                        ButtonVariant.Secondary
                                    onClick = { actionCount++ }
                                    +"Button"
                                }
                            }
                            div {
                                p {
                                    className = ClassName("pb-demo-caption")
                                    +"Disabled"
                                }
                                Button {
                                    disabled = true
                                    +"Button"
                                }
                            }
                        }
                        p {
                            className = ClassName("pb-showcase-note")
                            +"Preview actions: $actionCount"
                        }
                    }
                    div {
                        className = ClassName("pb-half pb-demo-panel")
                        h3 {
                            Text {
                                variant = TextStyle.Heading3
                                +"Inputs"
                            }
                        }
                        div {
                            className = ClassName("pb-stack")
                            TextField {
                                label = "Default"
                                value = inputValue
                                placeholder = "Placeholder"
                                onValueChange = { inputValue = it }
                            }
                            TextField {
                                label = "With supporting action"
                                value = inputValue
                                placeholder = "Placeholder"
                                onValueChange =
                                    { inputValue = it }
                                actionLabel = "Show input help"
                                onAction = { helpShown = !helpShown }
                                supportingText =
                                    if (helpShown) {
                                        "Type a company name. This preview stays in your browser."
                                    } else {
                                        "Labels stay visible. Use concise placeholders and validate inline."
                                    }
                            }
                        }
                    }
                    div {
                        className = ClassName("pb-half pb-demo-panel")
                        h3 {
                            Text {
                                variant = TextStyle.Heading3
                                +"Search & filters"
                            }
                        }
                        div {
                            className = ClassName("pb-stack")
                            TextField {
                                label = "Product search"
                                search = true
                                value = query
                                placeholder = "Ie: Marienburger Straße 5"
                                onValueChange =
                                    { query = it }
                            }
                            FilterBar {
                                label = "Product categories"
                                options = filters
                                selectedId = category
                                onSelect = { category = it }
                            }
                            p {
                                className = ClassName("pb-showcase-note")
                                +"Keep filters adjacent to result count and preserve applied state."
                            }
                        }
                    }
                    div {
                        className = ClassName("pb-third pb-demo-panel")
                        h3 {
                            Text {
                                variant = TextStyle.Heading3
                                +"Navigation"
                            }
                        }
                        nav {
                            className = ClassName("pb-stack")
                            ariaLabel = "Preview navigation"
                            listOf("Overview", "Companies", "Categories", "Saved").forEach { item ->
                                NavigationItem {
                                    key = Key(item)
                                    label =
                                        item
                                    href = "#patterns"
                                    active = item == "Companies"
                                }
                            }
                        }
                    }
                    div {
                        className = ClassName("pb-third pb-demo-panel")
                        h3 {
                            Text {
                                variant = TextStyle.Heading3
                                +"Badges, tags & controls"
                            }
                        }
                        div {
                            className = ClassName("pb-stack")
                            div {
                                className = ClassName("pb-inline")
                                Badge {
                                    verified = true
                                    +"Verified"
                                }
                                Badge { +"Search engine" }
                            }
                            Checkbox {
                                label = "Save company"
                                checked = saved
                                onCheckedChange = { saved = it }
                            }
                            Radio {
                                label = "Weekly digest"
                                name = "digest"
                                value = "one"
                                checked = radio == "one"
                                onCheckedChange =
                                    { radio = "one" }
                            }
                            Radio {
                                label = "Monthly digest"
                                name = "digest"
                                value = "two"
                                checked = radio == "two"
                                onCheckedChange =
                                    { radio = "two" }
                            }
                            Switch {
                                label = "News updates"
                                checked = enabled
                                onCheckedChange = { enabled = it }
                            }
                        }
                    }
                    div {
                        className = ClassName("pb-third")
                        Card {
                            elevated = true
                            div {
                                className = ClassName("pb-stack")
                                h3 {
                                    Text {
                                        variant = TextStyle.Heading3
                                        +"Cards"
                                    }
                                }
                                Card {
                                    rounded = true
                                    div {
                                        className = ClassName("pb-stack")
                                        h3 {
                                            Text {
                                                variant = TextStyle.Heading3
                                                +"Heading goes here"
                                            }
                                        }
                                        p { +"Lorem ipsum dolor sit amet, consectetur adipiscing elit. Suspendisse varius enim in eros." }
                                        div {
                                            className = ClassName("pb-inline")
                                            Button {
                                                variant = ButtonVariant.Secondary
                                                size =
                                                    ButtonSize.Small
                                                onClick = { actionCount++ }
                                                +"Button"
                                            }
                                            Button {
                                                size = ButtonSize.Small
                                                onClick =
                                                    { actionCount++ }
                                                +"Button"
                                            }
                                            Button {
                                                variant = ButtonVariant.Tertiary
                                                accessibleLabel =
                                                    "More preview actions"
                                                onClick = { actionCount++ }
                                                Icon { name = IconName.More }
                                            }
                                        }
                                    }
                                }
                                p {
                                    className = ClassName("pb-showcase-note")
                                    +"Use a single border and subtle elevation only for floating content."
                                }
                            }
                        }
                    }
                }
            }
            section {
                id = ElementId("patterns")
                className = ClassName("pb-showcase-section")
                div {
                    className = ClassName("pb-section-heading")
                    span {
                        className = ClassName("pb-eyebrow")
                        +"03 · Patterns"
                    }
                    h2 {
                        Text {
                            variant = TextStyle.Heading1
                            +"Structured content in context"
                        }
                    }
                }
                div {
                    className = ClassName("pb-stack")
                    Card {
                        elevated = true
                        div {
                            className = ClassName("pb-stack")
                            h3 {
                                Text {
                                    variant = TextStyle.Heading2
                                    +"Product.berlin"
                                }
                            }
                            div {
                                className = ClassName("pb-pattern-toolbar")
                                div {
                                    className = ClassName("pb-pattern-search")
                                    TextField {
                                        label = "Search companies"
                                        search = true
                                        value =
                                            query
                                        placeholder = "Search products"
                                        onValueChange = { query = it }
                                    }
                                }
                                FilterBar {
                                    label = "Result categories"
                                    options = filters
                                    selectedId = category
                                    onSelect = { category = it }
                                }
                            }
                            if (filtered.isEmpty()) {
                                EmptyState {
                                    title = "No matching products"
                                    description = "Try another search or clear your filters."
                                    actionLabel =
                                        "Clear filters"
                                    onAction = {
                                        query = ""
                                        category = "all"
                                    }
                                }
                            } else {
                                RankedResults {
                                    title = "Top search products"
                                    summary = "${filtered.size} PRODUCTS · SAMPLE DATA"
                                    rankingLabel =
                                        "Ranked by relevance ↓"
                                    results = filtered
                                }
                            }
                            p {
                                className = ClassName("pb-showcase-note")
                                +"Example: search, filter, compare, then inspect the rationale behind each ranking."
                            }
                        }
                    }
                    Card {
                        EmailSignup {
                            title = "Get next Monday’s ten in your inbox"
                            value = signupEmail
                            placeholder = "you@company.com"
                            actionLabel = "Notify me"
                            note = "Sample form. Nothing is sent from the catalogue."
                            status = if (signupSubmitted) EmailSignupStatus.Succeeded else EmailSignupStatus.Idle
                            message = if (signupSubmitted) "Sample confirmation: check your inbox." else null
                            onValueChange = { signupEmail = it }
                            onSubmit = { signupSubmitted = true }
                        }
                    }
                    NewsFeed {
                        isMock = true
                        stories = listOf(NewsStory("sample-news", "A new product for Berlin", "Sample publisher", "2026-09-27", null))
                    }
                    Card {
                        DenseTable {
                            label = "Dense table example"
                            columns =
                                listOf(TableColumn("Company"), TableColumn("Rank", numeric = true))
                            rows =
                                sampleResults.map { TableRow(it.id, listOf(it.name, it.rank.toString())) }
                        }
                    }
                    p {
                        className = ClassName("pb-showcase-note")
                        +(
                            "Screenshot-based implementation. Unspecified radii, shadows, " +
                                "interaction states and responsive behavior are documented estimates."
                        )
                    }
                }
            }
        }
    }
