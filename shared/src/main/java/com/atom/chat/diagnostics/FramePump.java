package com.atom.chat.diagnostics;

import com.atom.chat.AtomChat;
import com.atom.chat.config.AtomChatConfig;

/**
 * E1 外部尺子的宿主：把「与当前屏幕无关」的每帧 HUD/界面事件转给
 * {@link Truth3Probe#onPumpFrame(long, boolean, String)}，并负责唯一一次
 * 原样输出探针行（与 frame_probe / prepass_probe 的既有约定一致：
 * 探针类生成完整字符串，宿主只转发，不加日志前缀）。
 *
 * <p>三端各注册<strong>恰好一处</strong>：Fabric {@code HudRenderCallback}、
 * Forge 1.20.1 {@code RenderGuiEvent.Post}、NeoForge 1.21.1
 * {@code RenderGuiEvent.Post}。三者都是「每渲染帧一次、先于 Screen.render」的
 * 同一阶段，因此 G0（无屏幕）/ G1（原版 ChatScreen）/ G2（原版 InventoryScreen）/
 * G3（AC 面板）共用同一个采样点；AC 面板打开时与 {@code frame_probe} 同时存在，
 * 可用于交叉校准，见 {@link Truth3Probe} 类注释。
 *
 * <p>debug=off（产品默认）时这里只有一次配置字段读取与返回：不取时钟、
 * 不采样、不格式化、不输出日志，渲染路径与改动前逐字节等价。
 */
public final class FramePump {

    private FramePump() {
    }

    /** 每帧调用一次；日志行由 {@code Truth3Probe} 生成完整字符串。 */
    public static void onFrame() {
        AtomChatConfig config = AtomChatConfig.get();
        if (!config.debug) {
            return;
        }
        String line = Truth3Probe.get().onPumpFrame(
                System.nanoTime(), true, config.diagnosticRenderMode);
        String work = ClientWorkBudget.get().summaryLine();
        // 仍是唯一一个日志出口：两行按各自窗口节奏合并后一次转发，不新增 outlet。
        if (line != null || work != null) {
            AtomChat.LOGGER.info(line == null ? work : (work == null ? line : line + "\n" + work));
        }
    }
}
