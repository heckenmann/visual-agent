# Setup Guide

## Prerequisites

| Software | Version | Install |
|----------|---------|---------|
| Java JDK | 21+ | [Amazon Corretto](https://aws.amazon.com/corretto/) |
| Gradle | 8.x+ | `brew install gradle` (macOS) |
| Ollama | Latest | [ollama.com/download](https://ollama.com/download) |

## Installation

```bash
git clone <repository-url>
cd visual-agent
gradle build
```

## Configure Ollama

```bash
# Start Ollama server
ollama serve

# Download a model
ollama pull llama3.2

# Verify it's running
curl http://localhost:11434/api/tags
```

## Run Application

**Important:** JavaFX requires the module path to be set because Java 21 does not bundle JavaFX.

```bash
# Option 1: Gradle (sets module path automatically via build.gradle.kts)
gradle run
```

```bash
# Option 2: Direct Java command (after `gradle copyAllDependencies`)
java \
  --module-path lib \
  --add-modules javafx.controls,javafx.fxml,javafx.web,javafx.graphics,javafx.media,javafx.swing,javafx.base \
  -cp "build/classes/kotlin/main:lib/*" \
  de.heckenmann.visualagent.Main
```

**Note:** `gradle copyAllDependencies` must be run once to populate the `lib/` directory with JavaFX platform JARs before using Option 2.

## Configuration

Configuration is loaded from `src/main/resources/config/app.properties`:

```properties
# Ollama Configuration
ollama.local.url=http://localhost:11434
ollama.model=llama3.2

# Database
database.path=./data/visual-agent.db

# UI
ui.theme=dark
ui.font.size=14

# Browser
browser.default=firefox
```

## Troubleshooting

### "JavaFX Runtime components missing"

This means the JavaFX module path is not set. Use `gradle run` which configures this automatically, or use the direct Java command with `--module-path lib` shown above.

### "Module javafx.base not found"

The `lib/` directory is missing JavaFX platform JARs. Run:

```bash
gradle copyAllDependencies
```

### "Two versions of module X found in lib"

Duplicate JARs in `lib/`. Only the platform-specific JARs (e.g., `*-mac-aarch64.jar`) should remain for the module path. Remove the empty "meta" JARs (e.g., `javafx-base-21.0.2.jar` without platform suffix).

### Ollama Connection Fails

```bash
ollama list    # Check if Ollama is running
ollama serve   # Start Ollama server
```

### SQLite Errors

```bash
mkdir -p data  # Create data directory
```