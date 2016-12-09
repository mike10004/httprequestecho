package com.github.mike10004.httprequestecho.gae;

import com.github.mike10004.nativehelper.Program;
import com.github.mike10004.nativehelper.ProgramWithOutputStrings;
import com.github.mike10004.nativehelper.ProgramWithOutputStringsResult;
import com.google.common.base.Splitter;
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
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;


@SuppressWarnings("AppEngineForbiddenCode")
public class DevServerRule extends ExternalResource {

    private static final Logger log = Logger.getLogger(DevServerRule.class.getName());
    private static final File cwd = new File(System.getProperty("user.dir"));

    private final ListeningExecutorService executorService;
    private final boolean executorServiceIsMine;
    private volatile ListenableFuture<?> resultFuture;
    private final AtomicBoolean readyFlag, finishedFlag;
    private final int port, adminPort;
    private File devLogFile;

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

    private static final int DEFAULT_PORT = 8080;
    private static final int DEFAULT_ADMIN_PORT = 8000;

    public int getPort() {
        return port;
    }

    public DevServerRule(int port) {
        this(port, DEFAULT_ADMIN_PORT);
    }

    DevServerRule(int port, int adminPort) {
        this(MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()), true, port, adminPort);
    }

    public DevServerRule(ListeningExecutorService executorService) {
        this(executorService, false, DEFAULT_PORT, DEFAULT_ADMIN_PORT);
    }

    private DevServerRule(ListeningExecutorService executorService, boolean executorServiceIsMine, int port, int adminPort) {
        this.executorService = executorService;
        this.executorServiceIsMine = executorServiceIsMine;
        readyFlag = new AtomicBoolean(false);
        finishedFlag = new AtomicBoolean(false);
        this.port = checkPort(port);
        this.adminPort = checkPort(adminPort);
        if (adminPort != DEFAULT_ADMIN_PORT) {
            throw new IllegalArgumentException("specifying port for admin_host is not yet supported; you must use " + DEFAULT_ADMIN_PORT);
        }
    }

    protected abstract class ReadyListener implements DevServerOutputHandler {

        private final DevServerOutputHandler preDelegate;

        ReadyListener(DevServerOutputHandler preDelegate) {
            this.preDelegate = preDelegate;
        }

        @Override
        public synchronized void consumeLine(String line) {
            if (preDelegate != null) {
                preDelegate.consumeLine(line);
            }
            boolean newReadiness;
            if (!readyFlag.get()) {
                boolean oldReadiness = readyFlag.getAndSet(newReadiness = checkForReadinessIndication(line));
                if (oldReadiness != newReadiness) {
                    readinessChanged(newReadiness);
                }
            }
        }

        protected boolean checkForReadinessIndication(String line) {
            // Starting module "default" running at: http://localhost:8080
            return line.matches(".*Starting module \"[-\\w\\s]+\" running at: http://localhost:" + port + "\\s*$");
        }

        protected abstract void readinessChanged(boolean ready);
    }

    protected OutputStream openDevLogOutputStream() throws IOException {
        devLogFile = cwd.toPath().resolve("target").resolve("devserver.log").toFile();
        final OutputStream devLogOut = new java.io.FileOutputStream(devLogFile);
        return devLogOut;
    }

    protected @Nullable DevServerOutputHandler createPreReadinessCheckDelegate() {
        return null;
    }

    public interface DevServerOutputHandler {
        void consumeLine( String line );
    }

    @Override
    protected synchronized void before() throws Throwable {
        DevServerOutputHandler preReadinessDelegate = createPreReadinessCheckDelegate();
        final Object blocker = new Object();
        DevServerOutputHandler readinessListener = new ReadyListener(preReadinessDelegate) {
            @Override
            protected void readinessChanged(boolean ready) {
                log.log(ready ? Level.FINER : Level.INFO, "readinessChanged: {0} -> {1}", new Object[]{!ready, ready});
                if (ready) {
                    synchronized (blocker) {
                        blocker.notify();
                    }
                }
            }
        };
        MyProgramBuilder builder = new MyProgramBuilder("mvn");
        builder.from(cwd);
        builder.args("gcloud:run", "-DskipTests=true");
        builder.arg(webServerHostArg(port));
        builder.arg(adminHostArg(adminPort));
        final OutputStream devLogOut = openDevLogOutputStream();
        ProgramWithOutputStrings program = builder.outputToStrings(readinessListener, devLogOut, createDevServerStderrEcho());
        resultFuture = program.executeAsync(executorService);
        Futures.addCallback(resultFuture, new FutureCallback<Object>() {
            @Override
            public void onSuccess(@Nullable Object result) {
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
                finishedFlag.compareAndSet(false, true);
                if (devLogFile != null) {
                    log.log(Level.FINER, "closing log file {0}", devLogFile);
                }
                try {
                    devLogOut.close();
                } catch (IOException e) {
                    log.log(Level.SEVERE, "failed to close dev log file", e);
                }
            }
        });
        long waitStart = System.currentTimeMillis();
        synchronized (blocker) {
            log.finer("waiting for readiness flag to be set");
            blocker.wait(getReadinessWaitingDuration());
        }
        long waitFinished = System.currentTimeMillis();
        log.log(Level.FINER, "waited {0} milliseconds for readiness flag", waitFinished - waitStart);
        if (!readyFlag.get()) {
            throw new IllegalStateException("blocker wait() finished before readiness was achieved");
        }
    }

    protected long getReadinessWaitingDuration() {
        return 30 * 1000;
    }

    private static class MyProgramBuilder extends Program.Builder {

        /**
         * Constructs a builder instance.
         *
         * @param executable the executable name or pathname of the executable file
         */
        public MyProgramBuilder(String executable) {
            super(executable);
        }

        private static final Charset outputCharset = Charset.defaultCharset();

        public ProgramWithOutputStrings outputToStrings(final DevServerOutputHandler handler, final OutputStream out, final OutputStreamEcho devServerStderrEcho) {
            return new ProgramWithOutputStrings(executable, standardInput, standardInputFile, workingDirectory, environment, arguments, taskFactory, DEFAULT_STRING_OUTPUT_CHARSET) {
                @Override
                protected void configureTask(ExposedExecTask task, Map<String, Object> executionContext) {
                    super.configureTask(task, executionContext);
                    OutputStreamEcho stdoutEcho = new OutputStreamEcho(){

                        private volatile String partial;
                        private final String delimiter = System.getProperty("line.separator");
                        private final Splitter splitter = Splitter.on(delimiter);

                        @Override
                        public synchronized void writeEchoed(byte[] b, int off, int len) {
                            try {
                                out.write(b, off, len);
                                out.flush();
                            } catch (IOException e) {
                                throw new IllegalStateException("writing to stream failed", e);
                            }
                            try {
                                String s = new String(b, off, len, outputCharset);
                                List<String> parts = splitter.splitToList(s);
                                if (!parts.isEmpty()) {
                                    List<String> buffer = new ArrayList<>(parts.size() + 2);
                                    String first = parts.get(0);
                                    if (partial != null) {
                                        first = partial + first;
                                        partial = null;
                                    }
                                    buffer.add(first);
                                    if (parts.size() > 1) {
                                        if (s.endsWith(delimiter)) {
                                            buffer.addAll(parts.subList(1, parts.size()));
                                        } else {
                                            partial = parts.get(parts.size() - 1);
                                            buffer.addAll(parts.subList(1, parts.size() - 1));
                                        }
                                        for (String line : buffer) {
                                            handler.consumeLine(line);
                                        }
                                    }
                                }
                            } catch (RuntimeException e) {
                                log.log(Level.SEVERE, "failed to echo line", e);
                            }
                        }
                    };
                    task.getRedirector().setStdoutEcho(stdoutEcho);
                    task.getRedirector().setStderrEcho(devServerStderrEcho);
                }
            };
        }
    }

    protected OutputStreamEcho createDevServerStderrEcho() {
        return new OutputStreamEcho() {
            @Override
            public void writeEchoed(byte[] b, int off, int len) {
                System.err.write(b, off, len);
            }
        };
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
            if (!finishedFlag.get()) {
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
                Object futureReturnValue = resultFuture.get(5, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                System.err.format("failed while waiting for result future: %s%n", e);
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

    protected void kill() {
        if (!resultFuture.isDone()) {
            log.finer("cancelling invoker result future");
            try {
                Thread.sleep(getLogFlushDelay());
            } catch (InterruptedException e) {
                log.log(Level.INFO, "interrupted while sleeping to allow devserver.log to accumulate messages", e);
            }
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
