package com.atom.chat.settings.contract;

import com.atom.lib.frontend.contract.ContractCodec;
import com.atom.lib.frontend.contract.ContractCompatibilityException;
import com.atom.lib.frontend.contract.ContractRegistry;
import com.atom.lib.frontend.contract.MechanismIdentity;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static com.atom.lib.frontend.contract.ContractDesign.*;
import static org.junit.jupiter.api.Assertions.*;

class SettingsContractResourcesTest {
    @Test
    void incompatibleContractReturnsAnExplicitFallback() throws Exception {
        var result = admit("0".repeat(64));
        assertNull(result.registry());
        assertEquals(ContractCompatibilityException.Reason.MECHANISM_MISMATCH, result.incompatibility().reason());
    }

    @Test
    void compatibleContractUsesThePackagedRegistry() throws Exception {
        var result = admit(MechanismIdentity.compatibilityBinding());
        assertEquals("atomchat-settings", result.registry().namespace());
        assertNull(result.incompatibility());
    }

    @Test
    void invalidManifestDoesNotTakeTheCompatibilityFallback() {
        byte[] design = encode(MechanismIdentity.compatibilityBinding());
        assertThrows(IllegalArgumentException.class,
                () -> SettingsContractResources.admit(new ByteArrayInputStream(design), new ByteArrayInputStream(new byte[0])));
    }

    private static SettingsContractResources.Admission admit(String binding) throws Exception {
        byte[] design = encode(binding);
        return SettingsContractResources.admit(new ByteArrayInputStream(design),
                new ByteArrayInputStream(ContractRegistry.manifest(design, design)));
    }

    @Test
    void wrongDesignIdentityIsRejectedEvenWhenItAlsoHasAnUnknownBinding() {
        byte[] design = encode("wrong-host", "0".repeat(64));
        byte[] manifest = ContractRegistry.manifest(design, design);
        assertThrows(IllegalStateException.class,
                () -> SettingsContractResources.admit(new ByteArrayInputStream(design), new ByteArrayInputStream(manifest)));
    }

    private static byte[] encode(String binding) {
        return encode("atomchat-settings", binding);
    }

    private static byte[] encode(String namespace, String binding) {
        String profile = "atomchat-settings/1";
        var requirements = new EnumMap<Aspect, Requirement>(Aspect.class);
        for (var aspect : Aspect.values()) requirements.put(aspect, Requirement.notApplicable("test fixture"));
        var size = new Ref("geometry", "zero");
        return ContractCodec.encode(new com.atom.lib.frontend.contract.ContractDesign(1, namespace, "3", profile,
                Map.of("root", ""), Set.of(), Map.of(),
                Map.of("geometry", new Family("root", Aspect.LAYOUT, "", Map.of("zero", new NumberValue(0, Unit.U)))),
                Map.of("label", new Role(false, requirements)),
                Map.of("title", new Recipe("root", "label", Map.of(), new Binding("fixture", profile), Map.of(), Set.of("steady"))),
                Map.of("column", new Layout("root", Axis.COLUMN, size, size, size, size)), Map.of(),
                Map.of("history", new Page("root", Set.of("title"), "column", Set.of("steady"))),
                Map.of(MechanismIdentity.BINDING, binding)));
    }
}
