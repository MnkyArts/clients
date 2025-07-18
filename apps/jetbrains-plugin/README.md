# Bitwarden JetBrains Plugin

The official Bitwarden JetBrains plugin provides secure password management and autofill capabilities directly in your IDE.

## Features

- **Secure Autofill**: Automatically fill login credentials in forms and dialogs
- **Vault Management**: Access and search your Bitwarden vault from within the IDE
- **Auto-sync**: Automatically synchronize your vault in the background
- **Quick Access**: Use `Ctrl+Alt+B` for quick access to your vault items
- **Multi-IDE Support**: Works with all JetBrains IDEs (IntelliJ IDEA, PyCharm, WebStorm, etc.)

## Installation

### From JetBrains Marketplace (Coming Soon)

1. Open your JetBrains IDE
2. Go to `File > Settings > Plugins`
3. Search for "Bitwarden"
4. Click "Install"

### Manual Installation

1. Download the latest plugin zip file from the [releases page](../../releases)
2. In your IDE, go to `File > Settings > Plugins`
3. Click the gear icon and select "Install Plugin from Disk..."
4. Select the downloaded zip file

## Usage

### Getting Started

1. After installation, go to `Bitwarden > Login to Bitwarden` in the main menu
2. Enter your Bitwarden email and master password
3. The plugin will automatically sync your vault

### Using Autofill

- Press `Ctrl+Alt+B` for quick access to your vault
- The plugin will suggest relevant items based on the current context
- Select an item to copy credentials or autofill forms

### Menu Actions

- **Login to Bitwarden**: Authenticate with your Bitwarden account
- **Sync Vault**: Manually synchronize your vault
- **Logout**: Sign out and clear local vault cache
- **Settings**: Configure plugin preferences

## Building from Source

This plugin is part of the Bitwarden clients repository and uses Gradle for building.

### Prerequisites

- JDK 17 or higher
- Gradle 8.4+ (or use the included wrapper)

### Build Commands

```bash
# Build the plugin
./gradlew buildPlugin

# Run in development IDE
./gradlew runIde

# Run tests
./gradlew test
```

The built plugin will be available in `build/distributions/`.

## Development

### Project Structure

```
src/main/kotlin/com/bitwarden/jetbrains/
├── actions/          # IDE action classes
├── services/         # Core plugin services
├── startup/          # Plugin initialization
└── ui/              # User interface components
```

### Key Services

- **BitwardenAuthService**: Handles authentication and token management
- **BitwardenVaultService**: Manages vault data and searching
- **BitwardenSyncService**: Handles automatic vault synchronization
- **BitwardenAutofillService**: Provides autofill functionality

## Contributing

This plugin is part of the larger Bitwarden clients project. See the main [contributing guidelines](../../CONTRIBUTING.md) for information on how to contribute.

## Security

For security issues, please follow the [security policy](../../SECURITY.md).

## License

This project is licensed under the GPL-3.0 License - see the [LICENSE file](../../LICENSE.txt) for details.
