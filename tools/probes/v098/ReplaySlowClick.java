package atomchatprobe;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/** Exercises the production input route with a deliberately slow diagnostic sink. */
final class ReplaySlowClick extends AbstractAppender {
    private static final boolean ENABLED = Boolean.getBoolean("atomchat.replay.slowClickLog");
    private static final long DELAY = 180_000_000L;
    private static final long TIMEOUT = 3_000_000_000L;
    private static ReplaySlowClick test;

    private final Thread inputThread = Thread.currentThread();
    private final long started = System.nanoTime();
    private final String clickCoordinates;
    private final AtomicBoolean pending = new AtomicBoolean(true);
    private volatile Thread writer;
    private volatile Thread feedbackWriter;

    private ReplaySlowClick(int guiX, int guiY) {
        super("isolated AtomChat click regression", null, null, true, Property.EMPTY_ARRAY);
        clickCoordinates = "click: gui=(" + guiX + ", " + guiY + ") virtual=";
    }

    static void arm(int guiX, int guiY) {
        if (!ENABLED || test != null) return;
        test = new ReplaySlowClick(guiX, guiY);
        test.start();
        ((Logger) LogManager.getLogger("atomchat")).addAppender(test);
    }

    static boolean verify() {
        if (!ENABLED || !test.isStarted()) return true;
        boolean complete = test.writer != null && test.feedbackWriter != null;
        if (!complete && System.nanoTime() - test.started < TIMEOUT) return false;
        var logger = (Logger) LogManager.getLogger("atomchat");
        logger.removeAppender(test);
        test.stop();
        if (!complete) throw new AssertionError("click or feedback diagnostic never reached the appender");
        if (test.writer == test.inputThread) throw new AssertionError("click diagnostic blocked the render thread");
        if (test.feedbackWriter == test.inputThread) throw new AssertionError("feedback diagnostic ran on the render thread");
        System.out.println("ATOMCHAT_ASYNC_CLICK_VERIFIED");
        return true;
    }

    @Override
    public void append(LogEvent event) {
        String message = event.getMessage().getFormattedMessage();
        if (!pending.get() && message.startsWith("[AtomChat][feedback] play cue=BUTTON ")) {
            feedbackWriter = Thread.currentThread();
        }
        if (!message.startsWith("[AtomChat][network] " + clickCoordinates)
                || !message.endsWith(" button=0") || !pending.compareAndSet(true, false)) return;
        long until = System.nanoTime() + DELAY;
        for (long remaining; (remaining = until - System.nanoTime()) > 0;) {
            LockSupport.parkNanos(remaining);
        }
        writer = Thread.currentThread();
    }
}
