# Bitwarden JetBrains Plugin - Development Guide

## Overview

This document provides detailed information about the Bitwarden JetBrains Plugin implementation, including architecture, features, and development instructions.

## Architecture

### Core Services

The plugin is built around several core services:

#### 1. BitwardenAuthService

- Handles user authentication with Bitwarden servers
- Manages access tokens and refresh tokens
- Supports standard email/password authentication
- Stores tokens securely using JetBrains PasswordSafe

#### 2. BitwardenVaultService

- Manages vault data and item storage
- Provides search and filtering capabilities
- Supports all vault item types (Login, Secure Note, Card, Identity)
- Includes intelligent URI matching for autofill suggestions

#### 3. BitwardenSyncService

- Handles automatic vault synchronization
- Configurable sync intervals (default: 15 minutes)
- Background sync with proper error handling
- Manual sync capabilities

#### 4. BitwardenAutofillService

- Provides autofill functionality using Java Robot API
- Context-aware suggestions based on current URI
- Support for username, password, and combined autofill
- Clipboard integration for copy operations

### User Interface

#### Tool Window

- Integrated Bitwarden tool window in IDE sidebar
- Login interface for unauthenticated users
- Vault browser with item list and details
- Quick action buttons for sync, logout, and quick access

#### Actions and Shortcuts

- **Ctrl+Alt+B**: Quick access popup for vault items
- Menu actions: Login, Sync, Logout, Settings
- Context-sensitive action availability

### Plugin Configuration

The plugin is configured through:

- `plugin.xml`: Core plugin descriptor
- `build.gradle.kts`: Build configuration and dependencies
- Services registered as IntelliJ application services

## Features

### ✅ Implemented Features

1. **Authentication**

   - Email/password login
   - Secure token storage
   - Session management
   - Logout with cache clearing

2. **Vault Management**

   - Fetch and cache vault items
   - Search and filter functionality
   - Support for all item types
   - URI-based item matching

3. **Automatic Synchronization**

   - Background sync every 15 minutes (configurable)
   - Manual sync on demand
   - Sync status indicators
   - Error handling and retry logic

4. **Autofill Capabilities**

   - Quick access popup (Ctrl+Alt+B)
   - Username/password autofill
   - Clipboard operations
   - Context-aware suggestions

5. **User Interface**
   - Integrated tool window
   - Login/logout flows
   - Vault item browsing
   - Quick action shortcuts

### 🚧 Planned Enhancements

1. **Advanced Autofill**

   - Form field detection
   - Web browser integration
   - Custom field support
   - TOTP code generation

2. **Settings and Preferences**

   - Custom sync intervals
   - Server configuration
   - Autofill preferences
   - Security settings

3. **Security Features**
   - Biometric unlock (where supported)
   - Auto-lock functionality
   - Session timeout
   - Secure clipboard clearing

## Installation and Testing

### Prerequisites

- JetBrains IDE (IntelliJ IDEA, PyCharm, WebStorm, etc.)
- Java 17 or higher
- Gradle 8.4+

### Building the Plugin

```bash
# Clone the repository
git clone <repository-url>
cd clients/apps/jetbrains-plugin

# Build the plugin
./gradlew buildPlugin

# Run tests
./gradlew test

# Run in development IDE
./gradlew runIde
```

### Installing from Build

1. Build the plugin using the commands above
2. The plugin zip will be in `build/distributions/`
3. In your IDE: `File > Settings > Plugins > Install Plugin from Disk`
4. Select the built zip file
5. Restart IDE when prompted

### Testing the Plugin

1. **Initial Setup**

   - Open the Bitwarden tool window (usually in right sidebar)
   - Click "Login" and enter your Bitwarden credentials
   - Wait for initial vault sync

2. **Testing Autofill**

   - Press `Ctrl+Alt+B` for quick access
   - Select a login item and choose autofill option
   - Test in various contexts (forms, dialogs, etc.)

3. **Testing Sync**
   - Use "Sync Vault" from menu or tool window
   - Verify items are updated
   - Check sync timestamps

## Development

### Project Structure

```
src/main/kotlin/com/bitwarden/jetbrains/
├── actions/          # IDE action implementations
├── autofill/         # Autofill functionality
├── listeners/        # Event listeners
├── services/         # Core business logic
├── startup/          # Plugin initialization
└── ui/              # User interface components

src/main/resources/
└── META-INF/
    └── plugin.xml   # Plugin descriptor

src/test/kotlin/     # Unit tests
```

### Key Dependencies

- **IntelliJ Platform SDK**: Core IDE integration
- **OkHttp**: HTTP client for API communication
- **Jackson**: JSON parsing and serialization
- **Kotlin Standard Library**: Primary development language

### Extension Points

The plugin extends several IntelliJ Platform extension points:

- `applicationService`: Core services
- `toolWindow`: Bitwarden sidebar panel
- `passwordSafe.configuration`: Secure storage
- `webBrowser.provider`: Browser integration
- `backgroundPostStartupActivity`: Initialization

### Configuration Management

Settings are managed through:

- IntelliJ PasswordSafe for credentials
- Application-level preferences for configuration
- Secure storage for sensitive data

## Security Considerations

### Data Protection

- All sensitive data encrypted using IntelliJ PasswordSafe
- No plaintext password storage
- Secure communication with Bitwarden servers
- Automatic token refresh handling

### Access Control

- User authentication required for all operations
- Session timeout and automatic logout
- Secure clipboard operations with auto-clear

### Network Security

- HTTPS-only communication
- Certificate validation
- Proper error handling for network issues

## CI/CD Pipeline

The plugin includes a comprehensive CI/CD workflow:

### Build Pipeline

- Multi-platform testing (Linux, Windows, macOS)
- Gradle dependency caching
- Automated testing and verification
- Plugin packaging and artifact upload

### Release Process

- Automated version management
- Channel-based releases (alpha, beta, stable)
- JetBrains Marketplace publishing
- Release artifact distribution

### Quality Assurance

- Unit test execution
- Plugin verification
- Code style checking
- Security scanning

## Contributing

### Getting Started

1. Fork the repository
2. Create a feature branch
3. Implement changes with tests
4. Submit a pull request

### Development Guidelines

- Follow Kotlin coding conventions
- Add tests for new functionality
- Update documentation for user-facing changes
- Use proper logging levels

### Testing Requirements

- Unit tests for service logic
- Integration tests for UI components
- Manual testing in target IDEs
- Performance testing for large vaults

## Troubleshooting

### Common Issues

1. **Build Failures**

   - Ensure Java 17+ is installed
   - Check Gradle wrapper permissions
   - Clear Gradle cache if needed

2. **Plugin Installation**

   - Verify IDE version compatibility
   - Check plugin file integrity
   - Restart IDE after installation

3. **Authentication Issues**

   - Verify network connectivity
   - Check Bitwarden server status
   - Clear stored credentials if needed

4. **Autofill Problems**
   - Check accessibility permissions
   - Verify Java Robot API availability
   - Test in different contexts

### Debug Mode

Enable debug logging by adding to IDE VM options:

```
-Dlog4j.logger.com.bitwarden.jetbrains=DEBUG
```

### Support

For issues and support:

- Check the [GitHub Issues](../../issues)
- Review the [Security Policy](../../SECURITY.md)
- Follow the [Contributing Guidelines](../../CONTRIBUTING.md)
