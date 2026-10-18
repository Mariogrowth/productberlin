package net.productberlin.presentation

import kotlinx.coroutines.CancellationException
import net.productberlin.domain.entity.WeeklyNews
import net.productberlin.domain.usecase.GetWeeklyNews
import react.useEffect
import react.useState

/** The week's news, or null while loading or when it cannot be loaded: the section is extra, so it simply stays hidden. */
internal fun useWeeklyNews(getNews: GetWeeklyNews): WeeklyNews? {
    var news by useState<WeeklyNews?>(null)
    useEffect(getNews) {
        news =
            try {
                getNews()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
    }
    return news
}
