package com.github.mike10004.httprequestecho.gae;

import com.github.mike10004.nativehelper.Program;
import com.github.mike10004.nativehelper.ProgramWithOutput;
import com.github.mike10004.nativehelper.ProgramWithOutputFiles;
import com.github.mike10004.nativehelper.ProgramWithOutputStrings;
import com.github.mike10004.nativehelper.ProgramWithOutputStringsResult;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.novetta.ibg.common.sys.ExposedExecTask;
import com.novetta.ibg.common.sys.OutputStreamEcho;
import org.junit.rules.ExternalResource;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;


@SuppressWarnings("AppEngineForbiddenCode")
public class DevServerRule extends ExternalResource {

    private static final int DEFAULT_PORT = 8080;
    private static final int DEFAULT_ADMIN_PORT = 8000;

    private static final Logger log = Logger.getLogger(DevServerRule.class.getName());
    private static final File cwd = new File(System.getProperty("user.dir"));

    private final ListeningExecutorService executorService;
    private final boolean executorServiceIsMine;
    private volatile ListenableFuture<?> resultFuture;
    private final AtomicReference<ProgState> state;
    private final int port, adminPort;

    protected enum ProgState {
        NOT_STARTED, STARTED, READY, FINISHED;
    }

    public DevServerRule() {
        this(MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()), true, DEFAULT_PORT, DEFAULT_ADMIN_PORT);
    }

    @SuppressWarnings("SameParameterValue")
    public static DevServerRule withPortFromProperty(String systemPropertyName) {
        return withPortsSuppliedBy(portSupplier(systemPropertyName), Suppliers.ofInstance(DEFAULT_ADMIN_PORT));
    }

    private static Supplier<Integer> portSupplier(final String systemPropertyName) {
        return new Supplier<Integer>() {
            @Override
            public @Nullable Integer get() {
                return Integer.parseInt(System.getProperty(systemPropertyName));
            }
        };
    }

    @SuppressWarnings("SameParameterValue")
    public static DevServerRule withPortsFromProperties(String devServerPortSystemPropertyName, String adminServerPortSystemPropertyName) {
        return withPortsSuppliedBy(portSupplier(devServerPortSystemPropertyName), portSupplier(adminServerPortSystemPropertyName));
    }

    public static DevServerRule withPortsSuppliedBy(Supplier<Integer> portSupplier, Supplier<Integer> adminPortSupplier) {
        return new DevServerRule(portSupplier.get(), adminPortSupplier.get());
    }

    public DevServerRule(int port) {
        this(port, DEFAULT_ADMIN_PORT);
    }

    @VisibleForTesting
    DevServerRule(int port, int adminPort) {
        this(MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()), true, port, adminPort);
    }

    @VisibleForTesting
    DevServerRule(ListeningExecutorService executorService) {
        this(executorService, false, DEFAULT_PORT, DEFAULT_ADMIN_PORT);
    }

    private DevServerRule(ListeningExecutorService executorService, boolean executorServiceIsMine, int port, int adminPort) {
        this.executorService = executorService;
        this.executorServiceIsMine = executorServiceIsMine;
        state = new AtomicReference<>(ProgState.NOT_STARTED);
        this.port = checkPort(port);
        this.adminPort = checkPort(adminPort);
        if (adminPort != DEFAULT_ADMIN_PORT) {
            throw new IllegalArgumentException("specifying port for admin_host is not yet supported; you must use " + DEFAULT_ADMIN_PORT);
        }
    }

    public int getPort() {
        return port;
    }

    public int getAdminPort() {
        return adminPort;
    }

    protected class ReadyListener implements DevServerReadinessListener {

        @Override
        public synchronized boolean consumeLine(String line) {
            return checkForReadinessIndication(line);
        }

        protected boolean checkForReadinessIndication(String line) {
            // Starting module "default" running at: http://localhost:8080
            return line.matches(".*Starting module \"[-\\w\\s]+\" running at: http://localhost:" + port + "\\s*$");
        }

    }

    protected File constructStdoutFilePathname() {
        return new File(new File(cwd, "target"), "devserver.log");
    }

    protected File constructStderrFilePathname() {
        return new File(new File(cwd, "target"), "devserver-stderr.txt");
    }

    public interface DevServerReadinessListener {
        boolean consumeLine( String line );
    }

    @Override
    protected synchronized void before() throws NeverBecameReadyException, IOException, InterruptedException {
        final DevServerReadinessListener readinessListener = new ReadyListener();
        MyProgramBuilder builder = new MyProgramBuilder("mvn");
        builder.from(cwd);
        builder.args("gcloud:run", "-DskipTests=true");
        builder.arg(webServerHostArg(port));
        builder.arg(adminHostArg(adminPort));
        File stdoutFile = constructStdoutFilePathname(), stderrFile = constructStderrFilePathname();
        try (final PipedOutputStream devLogOut = new PipedOutputStream();
             final BufferedReader devLogReader = new BufferedReader(new InputStreamReader(new PipedInputStream(devLogOut), outputCharset))) {
            final CopyingOutputStreamEcho outputPiper = new CopyingOutputStreamEcho(devLogOut);
            ProgramWithOutput<?> program = builder.outputToFilesWithEchos(stdoutFile, stderrFile, outputPiper, null);
            state.set(ProgState.STARTED);
            log.log(Level.FINEST, "starting server now; log output copied to {0}", stdoutFile);
            resultFuture = program.executeAsync(executorService);
            Futures.addCallback(resultFuture, new FutureCallback<Object>() {
                @Override
                public void onSuccess(@Nullable Object result) {
                    log.finest("finished successfully");
                    finished();
                }

                @Override
                public void onFailure(Throwable t) {
                    if (!(t instanceof CancellationException)) {
                        log.log(Level.SEVERE, "process future failed not due to cancellation", t);
                    } else {
                        log.finer("mvn gcloud:run was cancelled");
                    }
                    finished();
                }

                private void finished() {
                    state.set(ProgState.FINISHED);
                }
            });
            long waitStart = System.currentTimeMillis();
            ExecutorService outputReadingExecutor = Executors.newSingleThreadExecutor();
            Future<Boolean> future = outputReadingExecutor.submit(new Callable<Boolean>(){
                @Override
                public Boolean call() throws Exception {
                    String line;
                    while ((line = devLogReader.readLine()) != null) {
                        if (readinessListener.consumeLine(line)) {
                            devLogReader.close();
                            outputPiper.disable();
                            return true;
                        }
                    }
                    devLogReader.close();
                    return false;
                }
            });
            boolean becameReady = false;
            try {
                becameReady = future.get(getReadinessWaitingDurationMs(), TimeUnit.MILLISECONDS);
            } catch (ExecutionException e) {
                log.log(Level.SEVERE, "reader thread failed", e);
            } catch (TimeoutException e) {
                future.cancel(true);
            }
            long waitFinished = System.currentTimeMillis();
            if (becameReady) {
                state.compareAndSet(ProgState.STARTED, ProgState.READY);
            }
            log.log(Level.FINER, "waited {0,number,#} milliseconds for server to become ready; current state: {1}", new Object[]{waitFinished - waitStart, state.get()});
            outputReadingExecutor.shutdownNow();
        }
        if (state.get() != ProgState.READY) {
            throw new NeverBecameReadyException();
        }
    }

    private static class NeverBecameReadyException extends Exception {

    }

    protected long getReadinessWaitingDurationMs() {
        return 30 * 1000;
    }

    private static class CopyingOutputStreamEcho implements OutputStreamEcho {

        private final OutputStream duplicate;
        private boolean disabled;

        private CopyingOutputStreamEcho(OutputStream duplicate) {
            this.duplicate = checkNotNull(duplicate);
        }

        @Override
        public void writeEchoed(byte[] b, int off, int len) {
            try {
                if (!disabled) {
                    duplicate.write(b, off, len);
                    duplicate.flush();
                }
            } catch (IOException e) {
                log.log(Level.WARNING, "copying to other stream failed", e);
            }
        }

        public void disable() {
            this.disabled = true;
        }

        public void enable() {
            this.disabled = false;
        }
    }

    private static final Charset outputCharset = Charset.defaultCharset();

    private static class MyProgramBuilder extends Program.Builder {

        /**
         * Constructs a builder instance.
         *
         * @param executable the executable name or pathname of the executable file
         */
        public MyProgramBuilder(String executable) {
            super(executable);
        }

        public ProgramWithOutputFiles outputToFilesWithEchos(File stdoutFile, File stderrFile, final OutputStreamEcho stdoutEcho, final OutputStreamEcho stderrEcho) {
            return new ProgramWithOutputFiles(executable, standardInput, standardInputFile, workingDirectory, environment, arguments, taskFactory, Suppliers.ofInstance(stdoutFile), Suppliers.ofInstance(stderrFile)) {
                @Override
                protected void configureTask(ExposedExecTask task, Map<String, Object> executionContext) {
                    super.configureTask(task, executionContext);
                    task.getRedirector().setStdoutEcho(stdoutEcho);
                    task.getRedirector().setStderrEcho(stderrEcho);
                }
            };
        }
    }

    protected long getLogFlushDelay() {
        return 2 * 1000;
    }

    private static String webServerHostArg(int port) {
        return String.format("-Dgcloud.host=localhost:%d", port);
    }

    private static String adminHostArg(int adminPort) {
        return String.format("-Dgcloud.admin_host=localhost:%d", adminPort);
    }

    private static int checkPort(int port) {
        checkArgument(port > 0 && port <= 65535, "port must be in range 1-65535: %s", port);
        return port;
    }

    @Override
    protected synchronized void after() {
        if (resultFuture != null) {
            boolean clean = stop();
            if (!clean) {
                kill();
            }
            maybeTerminateExecutorService();
            ProgState currentState = state.get();
            if (currentState != ProgState.NOT_STARTED && currentState != ProgState.FINISHED) {
                throw new IllegalStateException("executor service awaited termination, but the result future has not resolved");
            }
        }
    }

    protected boolean stop() {
        ProgramWithOutputStrings program = Program.running("mvn")
                .from(cwd)
                .args("gcloud:run_stop")
                .args(webServerHostArg(port))
                .args(adminHostArg(adminPort))
                .outputToStrings();
        ProgramWithOutputStringsResult result = program.execute();
        if (result.getExitCode() != 0) {
            log.log(Level.WARNING, "gcloud:run_stop goal exited with code {0}", result.getExitCode());
            System.out.print(result.getStdoutString());
            System.err.print(result.getStderrString());
            return false;
        } else {
            try {
                log.log(Level.FINE, "gcloud:run_stop exited clean; isDone? {0}; waiting 5 seconds for its future to return", resultFuture.isDone());
                Stopwatch stopwatch = Stopwatch.createStarted();
                resultFuture.get(5, TimeUnit.SECONDS);
                log.log(Level.FINER, "future get() returned in {0} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                log.log(Level.SEVERE, "failed while waiting for result future: %s%n", e);
            }
            if (!resultFuture.isDone()) {
                System.out.print(result.getStdoutString());
                System.err.print(result.getStderrString());
                log.warning("result future not done even though gcloud:run_stop goal was executed");
                return false;
            } else {
                return true;
            }
        }
    }

    private static void maybeSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            log.log(Level.INFO, "interrupted while sleeping to allow devserver.log to accumulate messages", e);
        }

    }

    protected void kill() {
        if (!resultFuture.isDone()) {
            log.finer("cancelling invoker result future");
            maybeSleep(getLogFlushDelay());
            boolean cancelled = resultFuture.cancel(true);
            log.log(Level.FINER, "cancelled: {0}", cancelled);
        } else {
            log.info("kill() requested even though result future already finished");
        }
    }

    protected void maybeTerminateExecutorService() {
        if (executorServiceIsMine) {
            List<Runnable> notYetExecuted = executorService.shutdownNow();
            if (!notYetExecuted.isEmpty()) {
                log.log(Level.WARNING, "tasks not yet executed: {0}", notYetExecuted);
            }
        }
    }
}
