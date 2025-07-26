import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * TmuxRunner - A Groovy library for managing tmux sessions and processor pipelines
 */
class TmuxRunnerImpl {

    // Constants
    static final String TMUX_COMMAND = "tmux"
    static final int MONITORING_SLEEP = 100
    static final int CLEANUP_SLEEP = 1000
    static final int CAPTURE_LINES = 60

    // Instance variables
    String sessionName
    String command
    boolean isRunning = false
    Thread backgroundThread
    List processors = []
    AtomicBoolean alreadyPrinted = new AtomicBoolean(false)

    /**
     * Constructor
     * @param command - Command to run in tmux session
     * @param sessionPrefix - Prefix for session name (optional)
     */
    TmuxRunnerImpl(String command, String sessionPrefix = "tmux-runner", String sessionId = '') {
        this.command = command
        if (sessionId.isBlank()) {
            this.sessionName = "${sessionPrefix}-${UUID.randomUUID().toString()}"
        } else {
            this.sessionName = sessionId
        }
    }

    /**
     * Add a processor to the pipeline
     * @param processor - Processor instance with processOutput method
     */
    void addProcessor(Object processor) {
        processors << processor
    }

    /**
     * Load processors from files automatically
     * @param directory - Directory to scan for *Processor.groovy files
     */
    @SuppressWarnings(['unused', 'GroovyAssignabilityCheck'])
    void loadProcessors(File directory = new File('.')) {
        def shell = new GroovyShell()
        def processorFiles = []

        // Scan for processor files
        directory.listFiles()?.each { file ->
            if (file.name.endsWith('Processor.groovy')) {
                processorFiles << file.name
            }
        }

        println "Found processors: ${processorFiles.join(', ')}"

        // Load all discovered processors
        processorFiles.each { fileName ->
            def processor = loadProcessor(directory, fileName, shell)
            if (processor != null) {
                addProcessor(processor)
                println "Loaded: ${fileName}"
            }
        }
    }

    protected static Object loadProcessor(File directory, String fileName, GroovyShell shell) {
        def processorFile = new File(directory, fileName)
        if (processorFile.exists()) {
            try {
                def processorClass = shell.parse(processorFile).run()
                return processorClass.newInstance()
            } catch (Exception e) {
                println "Warning: Failed to load processor '${fileName}': ${e.message}"
                return null
            }
        } else {
            println "Warning: Processor file '${fileName}' not found!"
            return null
        }
    }

    /**
     * Start the tmux session and monitoring
     * @param taskLines - Stack of tasks to process
     * @param taskSize - Total number of tasks
     */
    @SuppressWarnings('unused')
    void start(Stack taskLines, int taskSize) {
        // Start tmux session
        if (!createSession()) {
            throw new RuntimeException("Failed to create tmux session")
        }

        isRunning = true

        // Setup cleanup hook
        Runtime.runtime.addShutdownHook(new Thread({
            cleanup()
        }))

        // Start monitoring thread
        def taskNumb = new AtomicInteger(1)
        startMonitoring(taskLines, taskNumb, taskSize)

        println "TmuxRunner started with session: ${sessionName}"
    }

    /**
     * Stop the runner and cleanup
     */

    /**
     * Wait for completion or user input
     */
    @SuppressWarnings('unused')
    void waitForCompletion() {
        def reader = System.console()
        if (reader == null) {
            println "Run in terminal for interactive mode. Press Ctrl+C to stop."
            try {
                backgroundThread?.join()
            } catch (InterruptedException ignored) {
                if (!alreadyPrinted.getAndSet(true)) {
                    println captureTmuxPane()
                }
            }
        } else {
            while (isRunning) {
                def input = reader.readLine('> ')
                if (input == null || input.trim() in ['exit', 'quit']) {
                    println "Exiting..."
                    break
                }
            }
        }
        cleanup()
    }

    // === Private Methods ===

    protected boolean createSession() {
        def checkSession = [TMUX_COMMAND, "has-session", "-t", sessionName].execute()
        if (checkSession.waitFor() != 0) {
            println "Starting new tmux session... PLEASE WAIT !!!"
            def createProcess = [TMUX_COMMAND, "new-session", "-d", "-s", sessionName, command].execute()
            def result = createProcess.waitFor()
            if (result != 0) {
                println "Error creating tmux session. Exit code: $result"
                println "Error output: ${createProcess.errorStream.text}"
                return false
            }
            return true
        }
        return true
    }

    @SuppressWarnings('GroovyAssignabilityCheck')
    protected void startMonitoring(Stack taskLines, AtomicInteger taskNumb, int taskSize) {
        backgroundThread = Thread.start {
            while (isRunning && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(MONITORING_SLEEP)
                    def current = captureTmuxPane()
                    def lastLine = current?.readLines()?.findAll { it?.trim() }?.with { it ? it.last() : null }

                    if (null == lastLine) {
                        continue
                    }

                    // Process through all processors
                    def shouldBreak = false
                    for (processor in processors) {
                        def shouldContinue = processor.processOutput(
                                sessionName,
                                lastLine,
                                taskLines,
                                taskNumb,
                                taskSize,
                                alreadyPrinted,
                                this.&sendTaskToTmux,
                                this.&captureTmuxPane,
                                this.&sendKeysToTmux
                        )

                        if (!shouldContinue) {
                            shouldBreak = true
                            break
                        }
                    }

                    if (shouldBreak) {
                        isRunning = false
                        break
                    }

                } catch (InterruptedException ignored) {
                    if (!alreadyPrinted.getAndSet(true)) {
                        println captureTmuxPane()
                    }
                } catch (Exception e) {
                    if (isRunning) {
                        println ">>> Error in monitoring: ${e.message}"
                    }
                    if (!alreadyPrinted.getAndSet(true)) {
                        println captureTmuxPane()
                    }
                    break
                }
            }
        }
    }

    protected void cleanup() {
        if (!alreadyPrinted.getAndSet(true)) {
            println captureTmuxPane()
        }
        isRunning = false
        if (backgroundThread) {
            backgroundThread.interrupt()
        }

        // Send Ctrl+C to tmux session
        sendCtrlCToTmux()
        Thread.sleep(CLEANUP_SLEEP)

        // Kill the session
        killTmuxSession()
    }

    // === Tmux Operations ===

    protected int sendKeysToTmux(String... keys) {
        def command = [TMUX_COMMAND, "send-keys", "-t", sessionName] + keys.toList()
        return command.execute().waitFor()
    }

    protected int sendCtrlCToTmux() {
        return sendKeysToTmux("C-c")
    }

    protected int killTmuxSession() {
        return [TMUX_COMMAND, "kill-session", "-t", sessionName].execute().waitFor()
    }

    protected String captureTmuxPane() {
        return [TMUX_COMMAND, "capture-pane", "-pS", "-${CAPTURE_LINES}", "-t", sessionName].execute().text
    }

    protected int sendTaskToTmux(String task) {
        return sendKeysToTmux(task, "Enter")
    }

    // === Static Utilities ===

    @SuppressWarnings('unused')
    static String nowStr() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
    }
}

// Return the class for library usage
return TmuxRunnerImpl
