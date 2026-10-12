package com.atom.chat.profile;
/** Null means keep the field; empty means explicitly clear it. */
public record ProfilePatch(String signature, String avatar, String banner) {}
