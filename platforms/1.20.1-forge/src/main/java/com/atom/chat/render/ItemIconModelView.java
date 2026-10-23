package com.atom.chat.render;

import com.mojang.blaze3d.systems.RenderSystem;

/** 1.20 uses PoseStack; 1.21 uses JOML Matrix4fStack. */
final class ItemIconModelView {
    private ItemIconModelView() {}

    static void pushIdentity() {
        var stack = RenderSystem.getModelViewStack();
        stack.pushPose();
        stack.setIdentity();
        RenderSystem.applyModelViewMatrix();
    }

    static void pop() {
        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
    }
}
