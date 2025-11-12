# Sendlix Group Minecraft Bridge – BungeeCord Plugin

![GitHub Release](https://img.shields.io/github/release/sendlix/group-minecraft-bridge)

A BungeeCord plugin that lets players subscribe their email to a Sendlix group. Optionally, the plugin can send a one-time verification email if enabled. It does not send newsletters or general notifications by itself.

## Overview

This plugin integrates with the Sendlix API via gRPC. Players can register their email using a command. The plugin validates email format, applies rate limiting, and can require a privacy-policy confirmation. If email verification is enabled, a verification code is emailed and must be confirmed in-game before the email is submitted to the group.

## Features

- Newsletter subscription command: `/newsletter <email>`
- Optional email verification flow (sends a single verification email with a code)
- Email format validation
- Rate limiting between attempts (default 5s)
- Asynchronous gRPC calls to Sendlix (non-blocking)
- Rich, clickable messages (English only)
- Plugin Message API for backend integration (bidirectional on `sendlix:newsletter`)
- YAML configuration with sensible defaults
- Automatic player substitution: `{{mc_username}}`

What it does not do:
- It does not send newsletters or general email notifications by itself; it only subscribes an email to a Sendlix group and can send a verification email if configured.

## Installation

Prerequisites
- BungeeCord server (built against API 1.21, works on recent 1.20+)
- Java 11+

Setup
1. Download the latest release JAR.
2. Copy it to your BungeeCord server’s `plugins/` folder.
3. Restart the server.
4. Edit the generated `plugins/Sendlix Group Minecraft Bridge/config.yml`.

## Configuration

A `config.yml` will be created on first run.

```yaml
# Your Sendlix API Key in format: secret.keyId
apiKey: "your_api_key_here"

# Your Sendlix Group ID
groupId: "your_group_id_here"

# Cooldown between attempts in seconds
rateLimitSeconds: 5

# Optional: URL to your privacy policy
privacyPolicyUrl: "https://yourdomain.com/privacy-policy"

# Optional: enable email verification (sends a code that must be confirmed)
emailValidation: false

# Required when emailValidation = true: sender email used to send the verification email
emailFrom: "noreply@yourdomain.com"
```

Notes
- The API key must include the scope `group.insert`.
- If `emailValidation` is enabled, the API key must also include the `sender` scope to send emails. Configure `emailFrom` to a valid/verified sender.
- When `emailValidation` is enabled, an `emails/` folder is created under the plugin’s data directory containing editable templates: `verification.html` and `verification.txt`.

Template variables available in verification emails:
- `{{code}}` – the verification code
- `{{username}}` – the player’s in-game name

## Usage

Command
- Basic: `/newsletter <email> [--agree-privacy] [--silent]`
- Verify code (only when email verification is enabled): `/newsletter -c <code>`

Arguments
- `<email>` – required for subscription, e.g. `user@example.com`
- `--agree-privacy` – required if `privacyPolicyUrl` is set; otherwise the plugin shows a clickable prompt to agree
- `--silent` – suppresses normal chat output; errors may still be shown; the privacy-policy prompt can still appear if consent is missing. Primarily intended for integrations or server-side plugins that send their own user-facing messages (for example via the Plugin Message API), rather than using the plugin’s default messages. Regular players do not gain a benefit from this flag.
- `-c <code>` – confirms the email using the received verification code

Permission
- `sendlix.newsletter.add` is required to use the command.

Examples
- `/newsletter player@gmail.com`
- `/newsletter john.doe@web.de --agree-privacy`
- `/newsletter user@domain.com --silent`
- `/newsletter admin@server.com --silent --agree-privacy`
- `/newsletter -c 12345`

## Plugin Message API

Channel
- Name: `sendlix:newsletter`
- Direction: BungeeCord ↔ Backend servers (bidirectional)

Outgoing (Bungee → Backend)
- Sent when a player’s subscription flow changes state
- Payload: UTF-8 string of a status code (raw bytes)

Status values
- `email_added`
- `email_not_added`
- `email_already_exists`
- `email_verification_sent`
- `email_verification_failed`

Incoming (Backend → Bungee)
- Payload: command arguments as a single UTF-8 string (raw bytes), exactly the same as a player would type
- Examples:
  - `"user@example.com"`
  - `"user@example.com --agree-privacy"`
  - `"user@example.com --silent"`
  - `"user@example.com --silent --agree-privacy"`
  - `"-c 12345"` (verification)

Important encoding note
- The payload is a raw UTF-8 string without a length prefix. Ensure both sides use the same encoding. The examples below use `StandardCharsets.UTF_8`.

### Bukkit/Spigot integration example

```java
// Register plugin message channels in onEnable()
getServer().getMessenger().registerOutgoingPluginChannel(this, "sendlix:newsletter");
getServer().getMessenger().registerIncomingPluginChannel(this, "sendlix:newsletter", this);

// Trigger newsletter subscription from backend server
public void subscribePlayer(Player player, String email, boolean silent) {
    String command = email + " --agree-privacy" + (silent ? " --silent" : "");
    byte[] payload = command.getBytes(StandardCharsets.UTF_8);
    player.sendPluginMessage(this, "sendlix:newsletter", payload);
}

// Listen for status updates from BungeeCord
@Override
public void onPluginMessageReceived(String channel, Player player, byte[] message) {
    if (!channel.equals("sendlix:newsletter")) return;
    String status = new String(message, StandardCharsets.UTF_8);
    switch (status) {
        case "email_added":
            player.sendMessage("§a✓ Successfully subscribed to newsletter!");
            break;
        case "email_already_exists":
            player.sendMessage("§e⚠ You're already subscribed!");
            break;
        case "email_not_added":
            player.sendMessage("§c✗ Subscription failed. Please try again.");
            break;
        case "email_verification_sent":
            player.sendMessage("§b✉ Verification email sent. Check your inbox.");
            break;
        case "email_verification_failed":
            player.sendMessage("§c✗ Invalid or expired verification code.");
            break;
    }
}
```

## Development

Build requirements
- Java 11+
- Gradle (wrapper included)
- Git

Compile
```powershell
# Windows PowerShell
./gradlew.bat build
./gradlew.bat shadowJar
```
```bash
# macOS/Linux
./gradlew build
./gradlew shadowJar
```
The shaded JAR will be in `build/libs/`.

Project layout
```
src/main/java/com/sendlix/group/mc/
├── api/                    # API clients and gRPC channel
│   ├── AccessToken.java
│   ├── Channel.java
│   ├── EmailService.java
│   └── GroupService.java
├── commands/
│   └── NewsletterCommand.java
├── config/
│   ├── EmailTemplateRepository.java
│   ├── PluginProperties.java
│   └── SendlixConfig.java
├── core/
│   └── SendlixPlugin.java
└── utils/
    ├── MessageSender.java
    ├── RateLimiter.java
    └── Status.java
```

## API details

- Protocol: gRPC over HTTP/2 with TLS
- Auth: JWT via API key exchange; header injected by client interceptor
- Required scopes: `group.insert` and, when `emailValidation` is enabled, also `sender`
- Substitutions: `{{mc_username}}` is attached to group entries

## Security

- Rate limiting to prevent spam
- Email format validation
- TLS-encrypted API channel
- Token-based authentication; rejects API keys without `group.insert`

## Limitations and notes

- The plugin only subscribes emails to a Sendlix group and may send a one-time verification email; it does not deliver newsletters.
- Messages are English-only (no built-in localization).
- The command can only be used by players (not the console).
- Verification code is 5 digits and expires after 60 minutes.
- If `privacyPolicyUrl` is set and `--agree-privacy` isn’t provided, the plugin shows a clickable consent prompt (this may appear even in `--silent` mode).
- Plugin Message API expects raw UTF-8 strings without a length prefix; ensure consistent encoding on both sides.

## Troubleshooting

Plugin doesn’t load
- Check BungeeCord version (built against API 1.21) and Java 11+
- Ensure the plugin JAR is in `plugins/`

API connection errors
- Verify your API key format `secret.keyId`
- Ensure required scopes (`group.insert`; and `sender` when verification emails are enabled)
- Check network/firewall connectivity to `api.sendlix.com:443`

Configuration problems
- Verify YAML formatting and required fields `apiKey` and `groupId`
- If verification is enabled, set a valid `emailFrom`

Plugin message issues
- Make sure both sides register `sendlix:newsletter`
- Ensure payloads are raw UTF-8 strings (no length prefix)
- Log exact bytes if decoding fails

## Support

- Issues: https://github.com/sendlix/sendlix-bungeecord/issues
- Documentation: https://docs.sendlix.com

## License

AGPL-3.0 – see LICENSE.
