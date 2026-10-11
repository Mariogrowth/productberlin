package net.productberlin.presentation.keycheck

import net.productberlin.domain.entity.KeyFinding
import net.productberlin.domain.entity.KeyRisk
import net.productberlin.domain.entity.KeyType

/** Plain-language explanation of a finding: what it means and what to do. */
internal data class KeyAdvice(
    val verdict: String,
    val meaning: String,
    val fix: String?,
)

internal fun KeyFinding.advice(): KeyAdvice =
    when (type.risk) {
        KeyRisk.Secret -> {
            KeyAdvice(
                verdict = "Urgent: secret key",
                meaning =
                    "Anyone visiting your site can copy it and use ${type.service} as you, " +
                        "spending your credit or reaching your data.",
                fix =
                    "Revoke it now in ${REVOKE.getValue(type)}, check your usage for anything you don't recognise, " +
                        "then move the code that needs it to a server and keep the new key in your platform's secret settings.",
            )
        }

        KeyRisk.Depends -> {
            KeyAdvice(
                verdict = "Check its settings",
                meaning = "Fine in a website only if it is restricted, for example to your domain and the APIs you use.",
                fix = "Open Google Cloud → APIs & Services → Credentials and add website and API restrictions.",
            )
        }

        KeyRisk.Public -> {
            KeyAdvice(
                verdict = "Fine to be public",
                meaning =
                    if (type == KeyType.SupabaseAnon || type == KeyType.SupabasePublishable) {
                        "Designed for browsers, as long as Row Level Security is on for every table."
                    } else {
                        "Designed to be used in browsers. Nothing to fix."
                    },
                fix = null,
            )
        }
    }

/** "OpenAI API key", "Stripe secret key": acronyms keep their capitals. */
internal fun KeyType.title(): String = "$service ${if (label.startsWith("API")) label else label.replaceFirstChar { it.lowercaseChar() }}"

/** A request people can paste into Claude to fix the leak in their own project. */
internal fun KeyFinding.claudePrompt(domain: String): String =
    "My website $domain exposes a ${type.title()} ($masked) in $location. " +
        "Please help me: 1) remove it from the front-end code, 2) move the code that needs it to a server-side " +
        "function and read the key from a secret environment variable, 3) remind me to revoke the old key in " +
        "${REVOKE.getValue(type)} and to set a spending limit."

private val REVOKE =
    mapOf(
        KeyType.StripeSecret to "the Stripe Dashboard → Developers → API keys",
        KeyType.StripeRestricted to "the Stripe Dashboard → Developers → API keys",
        KeyType.StripePublishable to "the Stripe Dashboard → Developers → API keys",
        KeyType.OpenAi to "platform.openai.com → API keys",
        KeyType.Anthropic to "console.anthropic.com → API keys",
        KeyType.AwsAccessKey to "AWS IAM → Users → Security credentials",
        KeyType.GoogleApiKey to "Google Cloud → APIs & Services → Credentials",
        KeyType.GitHubToken to "GitHub → Settings → Developer settings → Personal access tokens",
        KeyType.SlackToken to "api.slack.com → Your apps",
        KeyType.SendGrid to "SendGrid → Settings → API Keys",
        KeyType.Brevo to "Brevo → SMTP & API → API keys",
        KeyType.Mailgun to "Mailgun → API Security",
        KeyType.HuggingFace to "huggingface.co → Settings → Access Tokens",
        KeyType.Groq to "console.groq.com → API Keys",
        KeyType.Replicate to "replicate.com → Account → API tokens",
        KeyType.SupabaseServiceRole to "Supabase → Project Settings → API",
        KeyType.SupabaseSecret to "Supabase → Project Settings → API Keys",
        KeyType.SupabaseAnon to "Supabase → Project Settings → API",
        KeyType.SupabasePublishable to "Supabase → Project Settings → API Keys",
        KeyType.MapboxSecret to "Mapbox → Account → Tokens",
        KeyType.MapboxPublic to "Mapbox → Account → Tokens",
        KeyType.PrivateKey to "the service that uses this key pair (create a new pair)",
    )
