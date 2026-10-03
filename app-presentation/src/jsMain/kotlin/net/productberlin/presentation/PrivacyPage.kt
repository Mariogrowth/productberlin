package net.productberlin.presentation

import react.FC
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.article
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.main
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.section
import react.dom.html.ReactHTML.ul
import web.cssom.ClassName

/** Operator identity shown on the privacy page. Fill in before this page is published. */
private object Operator {
    const val NAME = "[Operator name]"
    const val ADDRESS = "[Street and number, postcode Berlin, Germany]"
    const val EMAIL = "[contact email]"
}

/** Plain-language privacy notice for the website and the weekly email. */
val PrivacyPage =
    FC<Props> {
        main {
            className = ClassName("privacy")
            a {
                className = ClassName("privacy-home")
                href = "/"
                +"← Product.berlin"
            }
            article {
                h1 { +"Privacy policy" }
                p { +"Last updated: 3 October 2026" }
                section {
                    h2 { +"Who is responsible" }
                    p { +"${Operator.NAME}, ${Operator.ADDRESS}. Email: ${Operator.EMAIL}." }
                }
                section {
                    h2 { +"Weekly email" }
                    p {
                        +(
                            "If you sign up, we store your email address to send you one email a week with the " +
                                "ranking. We only add you after you confirm by clicking the link in the confirmation " +
                                "email (double opt-in). The legal basis is your consent (Art. 6(1)(a) GDPR)."
                        )
                    }
                    p {
                        +(
                            "Our email provider is Brevo (Sendinblue SAS, France), which stores your address and " +
                                "sends the emails on our behalf under a data processing agreement. We keep your address " +
                                "until you unsubscribe, which you can do at any time through the link in every email " +
                                "or by writing to us."
                        )
                    }
                }
                section {
                    h2 { +"Visiting the website" }
                    ul {
                        li {
                            +(
                                "Hosting: the site runs on Cloudflare, which processes technical data such as your IP " +
                                    "address to deliver and protect it. We do not use analytics or advertising cookies."
                            )
                        }
                        li {
                            +(
                                "Company logos are loaded by your browser from Brandfetch (cdn.brandfetch.io), which " +
                                    "receives your IP address and the page you came from."
                            )
                        }
                        li {
                            +(
                                "News links lead to Google News and the publishers' sites, which have their own " +
                                    "privacy policies."
                            )
                        }
                    }
                    p { +"The legal basis is our legitimate interest in running a secure, working website (Art. 6(1)(f) GDPR)." }
                }
                section {
                    h2 { +"Your rights" }
                    p {
                        +(
                            "You can ask for access to, correction or deletion of your data, restrict or object to its " +
                                "processing, receive it in a portable format, and withdraw your consent at any time. " +
                                "You can also complain to a data protection authority, such as the Berlin Commissioner " +
                                "for Data Protection and Freedom of Information."
                        )
                    }
                }
            }
        }
    }
