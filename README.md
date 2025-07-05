
# Sendlix Group Minecaft Bridge - BungeeCord Plugin

![GitHub Release](https://img.shields.io/github/release/sendlix/group-minecraft-bridge)

A BungeeCord plugin that allows players to subscribe to newsletters and receive email notifications.

## 📋 Overview

The Sendlix Newsletter Plugin seamlessly integrates into your BungeeCord server infrastructure and provides players with the ability to subscribe to newsletters through a simple command. The plugin uses gRPC for communication with the Sendlix backend and offers robust features like rate limiting and email validation.

## ✨ Features

- **Newsletter Subscription**: Players can register for newsletters with `/newsletter <email>`
- **Email Validation**: Automatic verification of email format validity
- **Rate Limiting**: Protection against spam through time-based restrictions
- **Asynchronous Processing**: Non-blocking API calls for optimal server performance
- **gRPC Integration**: Secure and efficient communication with the Sendlix backend
- **User-friendly Messages**: Multilingual success and error messages
- **Plugin Message API**: Inter-server communication for advanced integrations
- **Configurable**: Flexible configuration options via YAML

## 🚀 Installation

### Prerequisites

- BungeeCord Server (Version 1.20+)
- Java 8 or higher

### Setup

1. **Download Plugin**: Download the latest version from the releases
2. **Installation**: Copy the `.jar` file to your BungeeCord server's `plugins/` folder
3. **Restart Server**: Restart your BungeeCord server
4. **Configuration**: Edit the generated `plugins/Sendlix Newsletter/config.yml`

## ⚙️ Configuration

After the first installation, a `config.yml` will be automatically created:

```yaml
# Your Sendlix API Key (replace with your actual API key)
apiKey: "your_api_key_here"

# Your Sendlix Group ID (replace with your actual group ID)
groupId: "your_group_id_here"

# Rate limiting in seconds between API calls (default: 5 seconds)
rateLimitSeconds: 5

# Optional: URL to your privacy policy
privacyPolicyUrl: "https://yourdomain.com/privacy-policy"
```

### Getting API Credentials

1. Visit the Sendlix Dashboard
2. Create a new API key and copy it
3. Create a new group for newsletter subscriptions and copy the id. 
4. Replace the placeholder values in your `config.yml`:
   - `apiKey`: Your generated API key
   - `groupId`: Your target group ID for newsletter subscriptions

IMPORTANT: The api key needs the permission `group.insert` to allow players to subscribe to the newsletter.

## 🎮 Usage

### For Players

**Newsletter Subscription:**
```
/newsletter <email> [--agree-privacy] [--silent]
```

**Command Arguments:**
- `<email>` - **Required**. Your email address (e.g., user@example.com)
- `--agree-privacy` - **Optional**. Agrees to privacy policy (required if privacy policy URL is configured)
- `--silent` - **Optional**. Suppresses messages (useful for backend server integration) the privacy policy confirmation will be sent to the player if the `--agree-privacy` flag is not set.

**Examples:**
```
/newsletter player@gmail.com
/newsletter john.doe@web.de --agree-privacy
/newsletter user@domain.com --silent
/newsletter admin@server.com --silent --agree-privacy
```

To access the newsletter command, players must have the permission `sendlix.newsletter.add`.

## 🔗 Plugin Message API

The plugin provides a comprehensive plugin message API for integration with backend servers.

### Communication Channel
- **Channel Name**: `sendlix:newsletter`
- **Direction**: Bidirectional (BungeeCord ↔ Backend Servers)

### Outgoing Messages (BungeeCord → Backend Server)

When a player's newsletter subscription status changes, BungeeCord automatically sends status updates to the player's current backend server.

**Message Format:**
```
Channel: "sendlix:newsletter"
Data: Status enum byte array
```

**Status Values:**
- `EMAIL_ADDED` - Email successfully added to newsletter
- `EMAIL_NOT_ADDED` - Email could not be added (validation failed, API error, etc.)
- `EMAIL_ALREADY_EXISTS` - Email is already subscribed to newsletter

### Incoming Messages (Backend Server → BungeeCord)

Backend servers can trigger newsletter subscription commands by sending plugin messages.

**Message Format:**
```
Channel: "sendlix:newsletter"
Data: Command arguments as UTF-8 string
```

**Examples:**
```
"user@example.com"
"user@example.com --agree-privacy"
"user@example.com --silent"
"user@example.com --silent --agree-privacy"
```

### Backend Server Integration

#### Bukkit/Spigot Integration

```java
// Register plugin message channels in onEnable()
getServer().getMessenger().registerOutgoingPluginChannel(this, "sendlix:newsletter");
getServer().getMessenger().registerIncomingPluginChannel(this, "sendlix:newsletter", this);

// Trigger newsletter subscription from backend server
public void subscribePlayer(Player player, String email, boolean silent) {
    ByteArrayDataOutput out = ByteStreams.newDataOutput();
    String command = email + " --agree-privacy";
    if (silent) command += " --silent";
    
    out.writeUTF(command);
    plugin.getServer().sendPluginMessage(this, "sendlix:newsletter", out.toByteArray());
}

// Listen for status updates from BungeeCord
@Override
public void onPluginMessageReceived(String channel, Player player, byte[] message) {
    if (channel.equals("sendlix:newsletter")) {
        String status = new String(message, StandardCharsets.UTF_8);
        
        switch (status) {
           case "email_added":
                player.sendMessage("§a✓ Successfully subscribed to newsletter!");
                // Award achievement, update database, etc.
                giveNewsletterReward(player);
                break;
                
            case "email_already_exists":
                player.sendMessage("§e⚠ You're already subscribed!");
                break;
                
            case "email_not_added":
                player.sendMessage("§c✗ Subscription failed. Please try again.");
                logFailedSubscription(player);
                break;
        }
    }
}
```

#### Velocity Integration

```java
// Register plugin message channel
@Subscribe
public void onProxyInitialization(ProxyInitializeEvent event) {
    proxy.getChannelRegistrar().register(MinecraftChannelIdentifier.from("sendlix:newsletter"));
}

// Send newsletter command from backend
public void triggerNewsletter(Player player, String email) {
    ByteArrayDataOutput out = ByteStreams.newDataOutput();
    out.writeUTF(email + " --silent --agree-privacy");
    player.sendPluginMessage(MinecraftChannelIdentifier.from("sendlix:newsletter"), out.toByteArray());
}

// Handle status updates
@Subscribe
public void onPluginMessage(PluginMessageEvent event) {
    if (event.getIdentifier().getId().equals("sendlix:newsletter")) {
        String status = new String(event.getData(), StandardCharsets.UTF_8);
        // Handle status updates...
    }
}
```

## 🔧 Development

### Build Requirements

- Java 8+
- Gradle 7.0+
- Git

### Compiling the Project

```bash
# Clone repository
git clone https://github.com/sendlix/sendlix-bungeecord.git
cd sendlix-bungeecord

# Install dependencies and compile
./gradlew build

# Create Shadow JAR (for deployment)
./gradlew shadowJar
```

The compiled JAR file can be found under `build/libs/`.

### Project Structure

```
src/main/java/com/sendlix/
├── api/                    # API Classes
│   ├── AccessToken.java    # Token Management
│   └── Channel.java        # gRPC Channel Management
├── commands/               # Command Handlers
│   └── NewsletterCommand.java
├── config/                 # Configuration
│   └── SendlixConfig.java
├── core/                   # Plugin Core
│   └── SendlixPlugin.java
└── utils/                  # Utility Functions
    ├── MessageSender.java  # Message Management
    ├── RateLimiter.java    # Rate Limiting
    └── Status.java         # Status Codes
```

## 📊 API Integration

The plugin uses gRPC for communication with the Sendlix backend:

- **Protocol**: gRPC over HTTP/2
- **Authentication**: API Access Token
- **Encryption**: TLS/SSL
- **Data Format**: Protocol Buffers

## 🔒 Security

- **Rate Limiting**: Protection against spam and abuse
- **Email Validation**: Server-side verification of email formats
- **Secure API Communication**: TLS-encrypted connections
- **Token-based Authentication**: Secure API access

## 🐛 Troubleshooting

### Common Issues

**Plugin doesn't load:**
- Check your BungeeCord version (minimum 1.20)
- Ensure all dependencies are present

**API Connection Errors:**
- Verify your Access Token
- Check network connectivity
- Review firewall settings

**Configuration Errors:**
- Validate YAML syntax
- Ensure all required fields are filled

**Plugin Message Issues:**
- Ensure backend servers register the "sendlix:newsletter" channel
- Verify plugin message data format matches expected structure
- Check console logs for plugin message debugging information

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/sendlix/sendlix-bungeecord/issues)
- **Documentation**: [Sendlix Docs](https://docs.sendlix.com)

## 📄 License

This project is licensed under the [AGPL-3.0 License](LICENSE).