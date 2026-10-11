package net.productberlin.domain.entity

/** What a site sends to every visitor, checked for credentials. */
data class KeyScan(
    val domain: String,
    val filesScanned: Int,
    val sourceMaps: Int,
    val findings: List<KeyFinding>,
) {
    val secrets: List<KeyFinding> get() = findings.filter { it.type.risk == KeyRisk.Secret }
}
