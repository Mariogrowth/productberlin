package net.productberlin.domain.entity

/** A recognisable credential format and how risky it is to expose. */
enum class KeyType(
    val service: String,
    val label: String,
    val risk: KeyRisk,
) {
    StripeSecret("Stripe", "Secret key", KeyRisk.Secret),
    StripeRestricted("Stripe", "Restricted key", KeyRisk.Secret),
    StripePublishable("Stripe", "Publishable key", KeyRisk.Public),
    OpenAi("OpenAI", "API key", KeyRisk.Secret),
    Anthropic("Anthropic", "API key", KeyRisk.Secret),
    AwsAccessKey("AWS", "Access key ID", KeyRisk.Secret),
    GoogleApiKey("Google", "API key", KeyRisk.Depends),
    GitHubToken("GitHub", "Access token", KeyRisk.Secret),
    SlackToken("Slack", "Token", KeyRisk.Secret),
    SendGrid("SendGrid", "API key", KeyRisk.Secret),
    Brevo("Brevo", "API key", KeyRisk.Secret),
    Mailgun("Mailgun", "API key", KeyRisk.Secret),
    HuggingFace("Hugging Face", "Access token", KeyRisk.Secret),
    Groq("Groq", "API key", KeyRisk.Secret),
    Replicate("Replicate", "API token", KeyRisk.Secret),
    SupabaseServiceRole("Supabase", "Service role key", KeyRisk.Secret),
    SupabaseSecret("Supabase", "Secret key", KeyRisk.Secret),
    SupabaseAnon("Supabase", "Anon key", KeyRisk.Public),
    SupabasePublishable("Supabase", "Publishable key", KeyRisk.Public),
    MapboxSecret("Mapbox", "Secret token", KeyRisk.Secret),
    MapboxPublic("Mapbox", "Public token", KeyRisk.Public),
    PrivateKey("PEM", "Private key", KeyRisk.Secret),
}
