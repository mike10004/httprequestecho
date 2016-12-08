package com.github.mike10004.httprequestecho;

import com.github.mike10004.nativehelper.Program;
import com.github.mike10004.nativehelper.ProgramWithOutputStrings;
import com.google.common.base.Splitter;
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkArgument;


public class DevServerRule extends ExternalResource {

    private static final Logger log = Logger.getLogger(DevServerRule.class.getName());
    private static final File cwd = new File(System.getProperty("user.dir"));

    private final ListeningExecutorService executorService;
    private final boolean executorServiceIsMine;
    private volatile ListenableFuture<?> resultFuture;
    private final AtomicBoolean readyFlag, finishedFlag;
    private final int port;

    public DevServerRule() {
        this(MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()), true, DEFAULT_PORT);
    }

    public static DevServerRule withPortFromProperty(String systemPropertyName) {
        String val = System.getProperty("dev.server.port");
        int port = Integer.parseInt(val);
        checkArgument(port > 0, "port invalid: %s", port);
        return new DevServerRule(port);
    }

    private static int DEFAULT_PORT = 8080;

    public int getPort() {
        return port;
    }

    public DevServerRule(int port) {
        this(MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()), true, port);
    }

    public DevServerRule(ListeningExecutorService executorService) {
        this(executorService, false, DEFAULT_PORT);
    }

    private DevServerRule(ListeningExecutorService executorService, boolean executorServiceIsMine, int port) {
        this.executorService = executorService;
        this.executorServiceIsMine = executorServiceIsMine;
        readyFlag = new AtomicBoolean(false);
        finishedFlag = new AtomicBoolean(false);
        this.port = port;
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

    protected OutputStream createDevLogOutputStream() throws IOException {
        devLogFile = cwd.toPath().resolve("target").resolve("devserver.log").toFile();
        final OutputStream devLogOut = new FileOutputStream(devLogFile);
        return devLogOut;
    }

    private File devLogFile;

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
        builder.args("-Dgcloud.host=localhost:" + port);
        final OutputStream devLogOut = createDevLogOutputStream();
        ProgramWithOutputStrings program = builder.outputToStrings(readinessListener, devLogOut, createDevServerStderrEcho());
        resultFuture = program.executeAsync(executorService);
        Futures.addCallback(resultFuture, new FutureCallback<Object>() {
            @Override
            public void onSuccess(@Nullable Object result) {
                log.log(Level.INFO, "process future succeeded (which is unexpected): {0}", result);
                finished();
            }

            @Override
            public void onFailure(Throwable t) {
                if (!(t instanceof CancellationException)) {
                    log.log(Level.SEVERE, "process future failed not due to cancellation", t);
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

//                        private final List<String> buffer = new ArrayList<String>(100);
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

    @Override
    protected synchronized void after() {
        log.finer("cancelling invoker result future");
        if (resultFuture != null) {
            try {
                Thread.sleep(getLogFlushDelay());
            } catch (InterruptedException e) {
                log.log(Level.INFO, "interrupted while sleeping to allow devserver.log to accumulate messages", e);
            }
            boolean cancelled = resultFuture.cancel(true);
            log.log(Level.FINER, "cancelled: {0}", cancelled);
            if (executorServiceIsMine) {
                List<Runnable> notYetExecuted = executorService.shutdownNow();
                if (!notYetExecuted.isEmpty()) {
                    log.log(Level.WARNING, "tasks not yet executed: {0}", notYetExecuted);
                }
            }
            if (!finishedFlag.get()) {
                throw new IllegalStateException("executor service awaited termination, but the result future has not resolved");
            }
        }
    }
}
