package net.productberlin.worker.mapper

import net.productberlin.contract.KeyFindingDto
import net.productberlin.contract.KeyScanDto
import net.productberlin.domain.entity.KeyScan

internal fun KeyScan.toDto() =
    KeyScanDto(
        domain = domain,
        filesScanned = filesScanned,
        sourceMaps = sourceMaps,
        findings = findings.map { KeyFindingDto(it.type.name, it.masked, it.location) },
    )
