package com.atom.chat.render;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 预热接线的门与线程边界：字体只一次、GL 只一次、冷活不在调用线程上。
 * 不调用真实 Skia 预热（那需要原生库），只验证调度语义。
 */
class FirstOpenWarmupTest {

    @BeforeEach void reset() {
        FirstOpenWarmup.resetForTest();
    }

    @Test void fontPrewarmIsClaimedExactlyOnce() {
        assertTrue(FirstOpenWarmup.claimFontPrewarm());
        assertFalse(FirstOpenWarmup.claimFontPrewarm());
    }

    @Test void glWarmupRunsOnceEvenIfRequestedTwice() {
        FirstOpenWarmup.requestGlWarmup();
        FirstOpenWarmup.requestGlWarmup();
        assertTrue(FirstOpenWarmup.consumeGlWarmup());
        assertFalse(FirstOpenWarmup.consumeGlWarmup());
        FirstOpenWarmup.requestGlWarmup();
        assertFalse(FirstOpenWarmup.consumeGlWarmup(), "完成过的 GL 预热不得再跑第二次");
    }

    @Test void disconnectReopensOnlyTheJoinScopedWarmups() {
        assertTrue(FirstOpenWarmup.claimEmojiPrewarm());
        FirstOpenWarmup.requestGlWarmup();
        assertTrue(FirstOpenWarmup.consumeGlWarmup(), "首次请求应真的跑一次");
        FirstOpenWarmup.onDisconnect();
        assertTrue(FirstOpenWarmup.claimEmojiPrewarm(), "新世界可以按自己的 backlog 重新预热 emoji");
        // GL 资源属于进程；onDisconnect 只丢弃待办，不重开已完成的那一次。
        FirstOpenWarmup.requestGlWarmup();
        assertFalse(FirstOpenWarmup.consumeGlWarmup(), "完成过的 GL 预热不得再跑第二次");
    }

    @Test void coldWorkRunsOnADaemonThreadNotTheCaller() throws Exception {
        Thread caller = Thread.currentThread();
        AtomicReference<Thread> ranOn = new AtomicReference<>();
        Thread worker = FirstOpenWarmup.startColdThread("AtomChat-TestPrewarm",
                () -> ranOn.set(Thread.currentThread()));
        worker.join(5_000L);
        assertNotNull(ranOn.get(), "冷活没有执行");
        assertNotSame(caller, ranOn.get(), "字体/emoji 预热不得在调用（渲染）线程上执行");
        assertTrue(worker.isDaemon(), "预热线程必须是守护线程");
        assertFalse(worker.isAlive());
    }
}
