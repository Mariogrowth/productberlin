package net.productberlin.presentation.designsystem.layouts.forms

/** Submission state supplied by the caller. The layout neither validates nor sends addresses. */
enum class EmailSignupStatus { Idle, Submitting, Invalid, Failed, Succeeded }
