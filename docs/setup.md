# Setup Guide

## Prerequisites

### Required Software

| Software | Version | Download |
|----------|---------|----------|
| Java JDK | 21+ | [Amazon Corretto](https://aws.amazon.com/corretto/) |
| Gradle | 8.x | [Gradle](https://gradle.org/install/) |
| Ollama | Latest | [Ollama](https://ollama.com/download) |

### Optional Software

| Software | Purpose |
|----------|---------|
| Firefox | Browser integration |
| Git | Version control |

## Installation

### 1. Clone Repository

```bash
git clone <repository-url>
cd visual-agent
```

### 2. Gradle Setup

If Gradle is not installed:

**macOS:**
```bash
brew install gradle
```

**Windows (Chocolatey):**
```powershell
choco install gradle
```

**Linux:**
```bash
sudo apt install gradle  # Debian/Ubuntu
sudo dnf install gradle  # Fedora
```

### 3. Install Dependencies

```bash
gradle build
```

### 4. Configure Ollama

**Start local server:**
```bash
ollama serve
```

**Download model:**
```bash
ollama pull llama3.2
```

**Check availability:**
```bash
curl http://localhost:11434/api/tags
```

## Initialize Project Structure

### Create build.gradle.kts

```kotlin
plugins {
    kotlin("jvm") version "1.9.21"
    application
}

group = "com.visualagent"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin
    implementation(kotlin("stdlib"))
    
    // JavaFX
    implementation("org.openjfx:javafx-controls:21")
    implementation("org.openjfx:javafx-fxml:21")
    implementation("org.openjfx:javafx-webview:21")
    
    // SQLite
    implementation("org.xerial:sqlite-jdbc:3.45.0.0")
    
    // HTTP Client
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-cio:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:1.7.3")
    
    // JSON
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
    
    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.14")
}

application {
    mainClass.set("MainKt")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "21"
    }
}
```

### Create settings.gradle.kts

```kotlin
rootProject.name = "visual-agent"
```

### Create Main Class

`src/main/kotlin/Main.kt`:

```kotlin
import javafx.application.Application
import javafx.stage.Stage

class Main : Application() {
    override fun start(primaryStage: Stage) {
        // TODO: Initialize UI
        primaryStage.title = "Visual Agent"
        primaryStage.show()
    }
    
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Application.launch(Main::class.java, *args)
        }
    }
}
```

## Configuration

### Create Directory Structure

```bash
mkdir -p src/main/resources/config
mkdir -p src/main/resources/styles
mkdir -p src/main/resources/fxml
mkdir -p src/main/resources/images
```

### Create app.properties

`src/main/resources/config/app.properties`:

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

## Build & Run

### Build Application

```bash
gradle clean build
```

### Run Application

```bash
gradle run
```

### Create JAR

```bash
gradle jar
```

## Troubleshooting

### Ollama Connection Fails

```bash
# Check Ollama status
ollama list

# Restart server
ollama serve
```

### JavaFX Modules Not Found

Add JVM args:

```kotlin
application {
    applicationDefaultJvmArgs = listOf(
        "--add-modules", "javafx.controls,javafx.fxml,javafx.web",
        "--add-opens", "javafx.graphics/com.sun.javafx.application=ALL-UNNAMED"
    )
}
```

### SQLite Errors

```bash
# Create data directory
mkdir -p data

# Check permissions
chmod 755 data
```

## Next Steps

1. [Read Architecture](architecture.md)
2. [Create First UI Component](development.md)
3. [Implement Ollama Client](api.md)
