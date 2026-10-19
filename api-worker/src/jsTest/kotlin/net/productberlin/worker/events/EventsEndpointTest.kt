package net.productberlin.worker.events

import kotlin.js.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import net.productberlin.domain.entity.EventKind
import net.productberlin.domain.entity.EventSource
import net.productberlin.domain.entity.StartupEvent

class EventsEndpointTest {
    private val token = "t".repeat(40)
    private val auth = "Bearer $token"
    private val now = Date.parse("2026-10-19T08:00:00Z")
    private val feeds =
        listOf(
            EventFeed(EventSource("aicampus", "Merantix AI Campus", EventKind.Tech, berlinBased = true), "https://cal.example/a.ics"),
            EventFeed(EventSource("replit", "Replit", EventKind.Tech, berlinBased = false), "https://cal.example/r.ics"),
        )

    private class Fake(
        var last: String? = null,
    ) : EventStore {
        val replaced = mutableListOf<Map<String, List<StartupEvent>>>()

        override suspend fun lastCollected() = last

        override suspend fun replace(
            bySource: Map<String, List<StartupEvent>>,
            now: String,
        ) {
            replaced += bySource
            last = now
        }
    }

    private fun endpoint(store: Fake = Fake()) = EventsEndpoint(token, store, feeds, now)

    private fun entry(
        uid: String,
        title: String,
        location: String = "",
        start: String = "2026-10-21T16:00:00Z",
        url: String = "https://luma.com/$uid",
    ) = """{"uid":"$uid","title":${JSON.stringify(
        title,
    )},"startsAt":"$start","endsAt":"2026-10-21T18:00:00Z","location":${JSON.stringify(location)},"url":"$url"}"""

    @Test
    fun aRefreshIsDueAtMostEverySixHours() =
        runTest {
            assertEquals(true, JSON.parse<dynamic>(endpoint(Fake(null)).handle("GET", auth, "").body).due)
            assertEquals(false, JSON.parse<dynamic>(endpoint(Fake("2026-10-19T03:00:00.000Z")).handle("GET", auth, "").body).due)
            val due = JSON.parse<dynamic>(endpoint(Fake("2026-10-19T01:59:00.000Z")).handle("GET", auth, "").body)
            assertEquals(true, due.due)
            assertEquals("https://cal.example/a.ics", due.sources[0].feed)
            assertEquals(401, endpoint().handle("GET", "Bearer wrong", "").status)
        }

    @Test
    fun postedEntriesAreValidatedJudgedAndReplaceOnlyThePostedSources() =
        runTest {
            val store = Fake()
            val body =
                """{"sources":[
                {"id":"aicampus","entries":[
                    ${entry(
                    "e1",
                    "UNCONFERENCE: Engineering AI Together",
                    "Merantix AI Campus, Max-Urich-Straße 3, 13355 Berlin, Germany",
                )},
                    ${entry("e2", "Freelance Barcamp Hamburg", "SPACE, Am Sandtorkai 27, 20457 Hamburg, Germany")},
                    ${entry("e3", "Online Workshop: Pitching")},
                    ${entry("e4", "Demo Day #47")},
                    ${entry("e5", "Too late", start = "2026-12-24T16:00:00Z")},
                    ${entry("e6", "Line\nbreak", url = "javascript:alert(1)")}]},
                {"id":"replit","entries":[${entry(
                    "r1",
                    "Replit Friday Showcase",
                )}, ${entry("r2", "Breakfast", url = "https://luma.com/replit-gtc-berlin")}]},
                {"id":"unknown","entries":[${entry("u1", "Ignored")}]}]}"""
            val response = endpoint(store).handle("POST", auth, body)
            assertEquals("""{"sources":2,"events":4}""", response.body)
            val replaced = store.replaced.single()
            assertEquals(setOf("aicampus", "replit"), replaced.keys)
            val campus = replaced.getValue("aicampus")
            assertEquals(listOf("aicampus:e1", "aicampus:e4", "aicampus:e6"), campus.map { it.id })
            assertEquals("Merantix AI Campus", campus.first().venue)
            assertEquals(EventKind.Startup, campus[1].kind, "a demo day is a startup event")
            assertEquals("Line break", campus[2].title)
            assertEquals(null, campus[2].url, "unsafe links are dropped")
            assertEquals(listOf("replit:r2"), replaced.getValue("replit").map { it.id }, "international organisers need Berlin evidence")
        }

    @Test
    fun brokenBodiesAreRejected() =
        runTest {
            assertEquals(400, endpoint().handle("POST", auth, "{").status)
            assertEquals(400, endpoint().handle("POST", auth, "{}").status)
            assertEquals(413, endpoint().handle("POST", auth, " ".repeat(2_000_001)).status)
        }
}
