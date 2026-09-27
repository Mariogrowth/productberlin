package net.productberlin.presentation.designsystem.layouts.ranking

import net.productberlin.presentation.designsystem.components.text.Text
import net.productberlin.presentation.designsystem.components.text.TextStyle
import react.FC
import react.Key
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.ol
import web.cssom.ClassName

external interface RankedResultsProps : Props {
    var title: String
    var summary: String
    var rankingLabel: String
    var results: List<RankedResult>
}

val RankedResults =
    FC<RankedResultsProps> { props ->
        div {
            className = ClassName("pb-results")
            div {
                className = ClassName("pb-results-header")
                div {
                    h3 {
                        Text {
                            variant = TextStyle.Heading3
                            +props.title
                        }
                    }
                    Text {
                        variant = TextStyle.Caption
                        muted = true
                        +props.summary
                    }
                }
                Text {
                    variant = TextStyle.Label
                    muted = true
                    +props.rankingLabel
                }
            }
            ol {
                ariaLabel = props.title
                props.results.forEach { item ->
                    RankedResultRow {
                        key = Key(item.id)
                        result = item
                    }
                }
            }
        }
    }
