package net.productberlin.worker.hiring

/** Applicant-tracking systems whose public job feeds the GitHub collector can read. */
internal enum class JobProvider(
    val code: String,
) {
    Personio("personio"),
    Ashby("ashby"),
    Greenhouse("greenhouse"),
    Lever("lever"),
    Workable("workable"),
    Recruitee("recruitee"),
    SmartRecruiters("smartrecruiters"),
    ;

    companion object {
        fun of(code: String): JobProvider? = entries.firstOrNull { it.code == code }
    }
}
