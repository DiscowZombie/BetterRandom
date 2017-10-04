package io.github.pr0methean.betterrandom;

import io.github.pr0methean.betterrandom.util.LogPreFormatter;
import io.github.pr0methean.betterrandom.util.LooperThread;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.logging.Level;

public class DeadlockWatchdogThread extends LooperThread {

  public static final DeadlockWatchdogThread INSTANCE = new DeadlockWatchdogThread();
  public static final ThreadMXBean THREAD_MX_BEAN = ManagementFactory.getThreadMXBean();
  private static final LogPreFormatter LOG = new LogPreFormatter(DeadlockWatchdogThread.class);
  private static final int MAX_STACK_DEPTH = 20;
  private static final long serialVersionUID = 9118178318042580320L;

  private DeadlockWatchdogThread() {
    super("DeadlockWatchdogThread");
  }

  public static void ensureStarted() {
    if (INSTANCE.getState() == State.NEW) {
      INSTANCE.setDaemon(true);
      INSTANCE.setPriority(Thread.MAX_PRIORITY);
      INSTANCE.start();
    }
  }

  @Override
  public boolean iterate() throws InterruptedException {
    boolean deadlockFound = false;
    long[] threadsOfInterest;
    Level logLevel;
    threadsOfInterest = THREAD_MX_BEAN.findDeadlockedThreads();
    if (threadsOfInterest.length > 0) {
      LOG.error("DEADLOCKED THREADS FOUND");
      logLevel = Level.SEVERE;
      deadlockFound = true;
    } else {
      logLevel = Level.INFO;
      threadsOfInterest = THREAD_MX_BEAN.getAllThreadIds();
      if (threadsOfInterest.length <= 0) {
        LOG.error("ThreadMxBean didn't return any thread IDs");
        return false;
      }
    }
    for (long id : threadsOfInterest) {
      ThreadInfo threadInfo = THREAD_MX_BEAN.getThreadInfo(id, MAX_STACK_DEPTH);
      LOG.format(logLevel, threadInfo.getThreadName());
      StackTraceElement[] stackTrace = threadInfo.getStackTrace();
      LOG.logStackTrace(logLevel, stackTrace);
    }
    sleep(5_000);
    return !deadlockFound; // Terminate when a deadlock is found
  }
}