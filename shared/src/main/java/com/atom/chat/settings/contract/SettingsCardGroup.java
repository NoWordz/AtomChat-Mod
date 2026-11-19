package com.atom.chat.settings.contract;

import com.atom.chat.settings.SettingsGroupFrontend;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.frontend.StrictPageSession;
import com.atom.lib.frontend.contract.ContractDesign.TextValue;
import com.atom.lib.frontend.contract.ContractDesign.Value;
import com.atom.lib.layout.NodeId;
import com.atom.lib.layout.PresentationFrame;
import io.github.humbleui.skija.Canvas;
import java.util.List;

/** The original merged settings card surface and its row dividers. */
public final class SettingsCardGroup implements StrictPageSession.GroupImplementation {
    @Override
    public void paintGroup(Canvas canvas, PresentationFrame frame, NodeId group, List<NodeId> children,
                           Value material, StrictPageSession.FrameBindings bindings) {
        if (!new TextValue("merged-settings-card/1").equals(material)) {
            throw new IllegalArgumentException("UNKNOWN_SETTINGS_GROUP_MATERIAL " + material);
        }
        var bounds = frame.box(group).border();
        var card = new UiLayout.Rect(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        SettingsGroupFrontend.drawCardSurface(canvas, card);
        SettingsGroupFrontend.clipCard(canvas, card);
        for (int i = 1; i < children.size(); i++) {
            SettingsGroupFrontend.divider(canvas, card, frame.box(children.get(i)).border().y());
        }
    }
}
