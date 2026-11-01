package net.productberlin.presentation

import react.FC
import react.Key
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.header
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.nav
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.ul
import web.cssom.ClassName

internal external interface SiteHeaderProps : Props {
    /** The page's sections, in page order, as (label, element ID); only sections on the page are listed. */
    var sections: List<Pair<String, String>>
}

/** The site's header, set like a news site's: the wordmark and the section links above a hairline. */
internal val SiteHeader =
    FC<SiteHeaderProps> { props ->
        header {
            className = ClassName("site-header")
            div {
                className = ClassName("site-header-main front-width")
                h1 {
                    className = ClassName("site-wordmark")
                    a {
                        href = "/"
                        +"Product"
                        span {
                            className = ClassName("brand-dot")
                            +"."
                        }
                        +"berlin"
                    }
                }
                nav {
                    ariaLabel = "Sections"
                    ul {
                        props.sections.forEach { (label, target) ->
                            li {
                                key = Key(target)
                                a {
                                    href = "#$target"
                                    +label
                                }
                            }
                        }
                    }
                }
            }
        }
    }
