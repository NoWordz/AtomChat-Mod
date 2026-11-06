package com.atom.chat.diagnostics;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 首开预算探针的值语义与门控。全部离屏：不启动游戏、不需要 GL/Minecraft。
 * 数值由测试喂入，只验证口径（total − 四段 = other）与 debug=off 的静默。
 */
class FirstOpenBudgetTest {

    @BeforeEach void reset() {
        FirstOpenBudget.resetForTest();
    }

    @Test void debugOffIsCompletelyInert() {
        assertFalse(FirstOpenBudget.beginFirstOpen(false));
        assertFalse(FirstOpenBudget.active());
        FirstOpenBudget.addFont(1_000_000L);
        assertNull(FirstOpenBudget.endFrame(), "debug=off 不得输出任何行");
    }

    @Test void lineIsParseableAndPartsStayInsideTheTotal() throws Exception {
        assertTrue(FirstOpenBudget.beginFirstOpen(true));
        FirstOpenBudget.addFont(2_000_000L);
        FirstOpenBudget.addEmoji(3_000_000L);
        FirstOpenBudget.addBlurInit(4_000_000L);
        FirstOpenBudget.addIcons(5_000_000L);
        FirstOpenBudget.setPrewarmFont(125_000_000L);
        FirstOpenBudget.setPrewarmEmoji(840_000_000L);
        FirstOpenBudget.setPrewarmGl(310_000_000L);
        Thread.sleep(20L);

        String line = FirstOpenBudget.endFrame();
        assertNotNull(line);
        Matcher m = Pattern.compile(FirstOpenBudget.LINE_RE).matcher(line);
        assertTrue(m.matches(), "不可解析: " + line);
        double total = Double.parseDouble(m.group(1));
        double font = Double.parseDouble(m.group(2));
        double emoji = Double.parseDouble(m.group(3));
        double blur = Double.parseDouble(m.group(4));
        double icons = Double.parseDouble(m.group(5));
        double other = Double.parseDouble(m.group(6));
        assertTrue(total >= font + emoji + blur + icons, line);
        assertTrue(other >= 0.0, line);
        assertEquals(125.000, Double.parseDouble(m.group(7)), 1e-9);
        assertEquals(840.000, Double.parseDouble(m.group(8)), 1e-9);
        assertEquals(310.000, Double.parseDouble(m.group(9)), 1e-9);
    }

    @Test void onlyTheFirstConstructionIsMeasured() {
        assertTrue(FirstOpenBudget.beginFirstOpen(true));
        assertNotNull(FirstOpenBudget.endFrame());
        assertFalse(FirstOpenBudget.beginFirstOpen(true), "第二次构造不得重新开启窗口");
        assertFalse(FirstOpenBudget.active());
        assertNull(FirstOpenBudget.endFrame());
    }

    @Test void unwrittenPrewarmFieldsStayAtMinusOne() {
        assertTrue(FirstOpenBudget.beginFirstOpen(true));
        String line = FirstOpenBudget.endFrame();
        assertNotNull(line);
        Matcher m = Pattern.compile(FirstOpenBudget.LINE_RE).matcher(line);
        assertTrue(m.matches(), line);
        assertEquals("-1.000", m.group(7));
        assertEquals("-1.000", m.group(8));
        assertEquals("-1.000", m.group(9));
    }
}
