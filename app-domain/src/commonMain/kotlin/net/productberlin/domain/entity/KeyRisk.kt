package net.productberlin.domain.entity

/** Whether a credential found in a site's public files is a problem. */
enum class KeyRisk {
    /** Must never be public: anyone can copy it and act or spend as the owner. */
    Secret,

    /** Only safe if restricted in the provider's settings (for example to the site's domain). */
    Depends,

    /** Designed to be shipped to browsers. */
    Public,
}
