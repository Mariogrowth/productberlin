package net.productberlin.worker

import net.productberlin.worker.newsletter.EndpointResponse

/**
 * `Authorization: Bearer <COLLECTOR_TOKEN>` for the GitHub collector's internal endpoints. Returns the error response
 * to send, or null when the request is authorised. Without a configured token (at least 32 characters) the
 * endpoints are unavailable.
 */
internal fun bearerRejection(
    expectedToken: String?,
    authorization: String?,
): EndpointResponse? {
    val token = expectedToken?.takeIf { it.length >= MIN_TOKEN_LENGTH } ?: return EndpointResponse(503, """{"error":"not_configured"}""")
    if (!constantTimeEquals(authorization.orEmpty(), "Bearer $token")) return EndpointResponse(401, """{"error":"unauthorized"}""")
    return null
}

private fun constantTimeEquals(
    a: String,
    b: String,
): Boolean {
    var difference = a.length xor b.length
    for (i in 0 until maxOf(a.length, b.length)) {
        difference = difference or (a.getOrElse(i) { ' ' }.code xor b.getOrElse(i) { ' ' }.code)
    }
    return difference == 0
}

private const val MIN_TOKEN_LENGTH = 32
