package net.productberlin.presentation.designsystem.components.icons

import react.FC
import react.Props
import react.dom.html.ReactHTML.span
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.svg
import react.dom.svg.StrokeLinecap
import react.dom.svg.StrokeLinejoin
import web.cssom.ClassName

external interface IconProps : Props {
    var name: IconName
}

/** Decorative by default. The enclosing control must supply a visible or accessible label. */
val Icon =
    FC<IconProps> { props ->
        span {
            className = ClassName("pb-icon")
            ariaHidden = true
            svg {
                width = 24.0
                height = 24.0
                viewBox = "0 0 24 24"
                fill = "none"
                stroke = "currentColor"
                strokeWidth = 1.5
                strokeLinecap = StrokeLinecap.round
                strokeLinejoin = StrokeLinejoin.round
                focusable = false
                path {
                    d =
                        when (props.name) {
                            IconName.Search -> "M16.5 16.5 21 21 M18 10.5a7.5 7.5 0 1 1-15 0 7.5 7.5 0 0 1 15 0"
                            IconName.Filter -> "M3 4h18l-7 8v8l-4-2v-6Z"
                            IconName.Back -> "M20 12H4m7-7-7 7 7 7"
                            IconName.Verified -> "m12 2 3 2 4 1 1 4 2 3-2 3-1 4-4 1-3 2-3-2-4-1-1-4-2-3 2-3 1-4 4-1Z M8 12l3 3 5-6"
                            IconName.List -> "M10 5h11M10 12h11M10 19h11M3 4h1v4M3 11h3l-3 4h3M3 18h3l-2 2h2"
                            IconName.Grid -> "M4 3h16a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1ZM12 3v18M3 12h18"
                            IconName.Help -> "M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0 M9 8a3 3 0 0 1 6 0c0 3-3 2-3 5M12 17h.01"
                            IconName.More -> "M4 12h.01M12 12h.01M20 12h.01"
                            IconName.Chevron -> "m7 10 5 5 5-5"
                            IconName.Check -> "m5 12 4 4L19 6"
                            IconName.Close -> "M6 6l12 12M18 6 6 18"
                        }
                }
            }
        }
    }
