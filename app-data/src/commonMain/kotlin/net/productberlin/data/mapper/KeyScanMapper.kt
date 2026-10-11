package net.productberlin.data.mapper

import net.productberlin.contract.KeyScanDto
import net.productberlin.domain.entity.KeyFinding
import net.productberlin.domain.entity.KeyScan
import net.productberlin.domain.entity.KeyType

/** Unknown key types (from a newer Worker) are skipped rather than shown without an explanation. */
fun KeyScanDto.toDomain() =
    KeyScan(
        domain = domain,
        filesScanned = filesScanned,
        sourceMaps = sourceMaps,
        findings =
            findings.mapNotNull { finding ->
                KeyType.entries.firstOrNull { it.name == finding.type }?.let { KeyFinding(it, finding.masked, finding.location) }
            },
    )
