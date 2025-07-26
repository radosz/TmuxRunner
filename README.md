## JitPack Build Status

[![](https://jitpack.io/v/radosz/TmuxRunner.svg)](https://jitpack.io/#radosz/TmuxRunner)

Visit [JitPack page](https://jitpack.io/#radosz/TmuxRunner) to see build logs and available versions.

# TmuxRunner

A Groovy library for managing tmux sessions and processor pipelines, designed for automated task execution with real-time monitoring and processing capabilities.

## Features

- 🚀 **Automated tmux session management** - Create, monitor, and cleanup tmux sessions
- 🔄 **Processor pipeline system** - Extensible processor architecture for handling command output
- 📦 **JitPack integration** - Easy dependency management via JitPack.io
- 🔍 **Real-time monitoring** - Monitor tmux session output with configurable processors
- 🛠️ **Auto-discovery** - Automatically loads processor files from specified directories
- 🧹 **Cleanup handling** - Proper session cleanup with shutdown hooks

## Installation

### Using JitPack with Groovy Grape

Add the following annotations to your Groovy script:

```groovy
@GrabResolver(name='jitpack', root='https://jitpack.io')
@Grab('com.github.radosz:TmuxRunner:main-SNAPSHOT')
```

### Using JitPack with Gradle

Add to your `build.gradle`:

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.radosz:TmuxRunner:main-SNAPSHOT'
}
```

### Using JitPack with Maven

Add to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.radosz</groupId>
    <artifactId>TmuxRunner</artifactId>
    <version>main-SNAPSHOT</version>
</dependency>
```

## Quick Start

### Basic Usage

```groovy
@GrabResolver(name='jitpack', root='https://jitpack.io')
@Grab('com.github.radosz:TmuxRunner:main-SNAPSHOT')

// Create a TmuxRunner instance
def runner = new TmuxRunnerImpl('your-command-here', 'session-prefix')

// Load processors from current directory
runner.loadProcessors(new File('.'))

// Create a task stack
def tasks = new Stack()
tasks.push("echo 'Task 1'")
tasks.push("echo 'Task 2'")

// Start the runner
runner.start(tasks, tasks.size())

// Wait for completion
runner.waitForCompletion()
```

### Advanced Example with Custom Processors

```groovy
@GrabResolver(name='jitpack', root='https://jitpack.io')
@Grab('com.github.radosz:TmuxRunner:main-SNAPSHOT')

// Create runner
def runner = new TmuxRunnerImpl('npx some-cli-tool', 'my-session')

// Add a custom processor
runner.addProcessor(new MyCustomProcessor())

// Or load all processors from a directory
runner.loadProcessors(new File('/path/to/processors'))

// Start with tasks
def tasks = new Stack()
tasks.push("task 1 content")
tasks.push("task 2 content")

runner.start(tasks, tasks.size())
runner.waitForCompletion()
```

## Processor System

### Creating Custom Processors

Processors must implement a `processOutput` method with the following signature:

```groovy
class MyCustomProcessor {
    boolean processOutput(
        String sessionName,
        String lastLine, 
        Stack taskLines,
        AtomicInteger taskNumber,
        int taskSize,
        AtomicBoolean alreadyPrinted,
        Closure sendTaskToTmux,
        Closure captureTmuxPane,
        Closure sendKeysToTmux
    ) {
        // Process the output line
        if (lastLine.contains("some-pattern")) {
            // Do something
            return false // Stop processing pipeline
        }
        return true // Continue to next processor
    }
}
```

### Processor Auto-Discovery

TmuxRunner automatically discovers and loads processor files:

1. Place processor files in a directory (e.g., `MyTaskProcessor.groovy`)
2. Files must end with `Processor.groovy`
3. Each file should define a class that can be instantiated
4. Call `runner.loadProcessors(new File('/path/to/processors'))`

Example processor file structure:
```
processors/
├── TaskCompletionProcessor.groovy
├── ErrorHandlingProcessor.groovy
└── LoggingProcessor.groovy
```

## API Reference

### TmuxRunnerImpl Constructor

```groovy
TmuxRunnerImpl(String command, String sessionPrefix = "tmux-runner")
```

- `command`: The command to run in the tmux session
- `sessionPrefix`: Optional prefix for the session name (default: "tmux-runner")

### Methods

#### `addProcessor(Object processor)`
Add a single processor to the pipeline.

#### `loadProcessors(File directory)`
Auto-discover and load all `*Processor.groovy` files from the specified directory.

#### `start(Stack taskLines, int taskSize)`
Start the tmux session and begin monitoring with the provided tasks.

#### `waitForCompletion()`
Wait for task completion or user interruption.

### Processor Method Parameters

- `sessionName`: Current tmux session name
- `lastLine`: Latest line captured from tmux session
- `taskLines`: Stack of remaining tasks
- `taskNumber`: Current task number (AtomicInteger)
- `taskSize`: Total number of tasks
- `alreadyPrinted`: Flag to prevent duplicate printing (AtomicBoolean)
- `sendTaskToTmux`: Closure to send a task to tmux session
- `captureTmuxPane`: Closure to capture current tmux pane content
- `sendKeysToTmux`: Closure to send keys to tmux session

## Configuration

### Environment Variables

The library uses these constants that can be customized:

- `TMUX_COMMAND`: Command to run tmux (default: "tmux")
- `MONITORING_SLEEP`: Sleep interval for monitoring loop in ms (default: 100)
- `CLEANUP_SLEEP`: Sleep interval during cleanup in ms (default: 1000)
- `CAPTURE_LINES`: Number of lines to capture from tmux pane (default: 60)

## Examples

### Example 1: Simple Task Runner

```groovy
@GrabResolver(name='jitpack', root='https://jitpack.io')
@Grab('com.github.radosz:TmuxRunner:main-SNAPSHOT')

def runner = new TmuxRunnerImpl('bash', 'simple-tasks')

def tasks = new Stack()
tasks.push('echo "Starting task 1"')
tasks.push('sleep 2')
tasks.push('echo "Task 1 complete"')

runner.start(tasks, tasks.size())
runner.waitForCompletion()
```

### Example 2: With Custom Processor

```groovy
@GrabResolver(name='jitpack', root='https://jitpack.io')
@Grab('com.github.radosz:TmuxRunner:main-SNAPSHOT')

class CompletionProcessor {
    boolean processOutput(sessionName, lastLine, taskLines, taskNumber, taskSize, 
                         alreadyPrinted, sendTaskToTmux, captureTmuxPane, sendKeysToTmux) {
        if (lastLine.contains("DONE")) {
            println "Task completed successfully!"
            if (!taskLines.empty()) {
                def nextTask = taskLines.pop()
                sendTaskToTmux(nextTask)
                taskNumber.incrementAndGet()
            }
        }
        return true
    }
}

def runner = new TmuxRunnerImpl('my-cli-tool', 'custom-session')
runner.addProcessor(new CompletionProcessor())

def tasks = new Stack()
tasks.push('command-that-outputs-DONE-when-finished')

runner.start(tasks, tasks.size())
runner.waitForCompletion()
```

## Requirements

- **Groovy**: 4.0+ (uses `localGroovy()` for compatibility)
- **tmux**: Must be installed and available in PATH
- **Java**: Java 11+ (as specified in build configuration)

## Troubleshooting

### JitPack Build Issues

If you encounter dependency resolution issues:

1. Visit https://jitpack.io/#radosz/TmuxRunner to trigger a fresh build
2. Try using a specific commit hash instead of `main-SNAPSHOT`
3. Check the build logs on JitPack for detailed error information

### tmux Session Issues

- Ensure tmux is installed: `brew install tmux` (macOS) or `apt-get install tmux` (Ubuntu)
- Check if tmux sessions are being created: `tmux list-sessions`
- Verify the command you're passing to TmuxRunner works in a regular tmux session

### Processor Loading Issues

- Ensure processor files end with `Processor.groovy`
- Check that processor classes can be instantiated (have a no-arg constructor)
- Verify the directory path is correct when calling `loadProcessors()`
