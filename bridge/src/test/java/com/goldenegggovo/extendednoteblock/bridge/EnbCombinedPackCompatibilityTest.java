package com.goldenegggovo.extendednoteblock.bridge;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import net.momirealms.craftengine.core.util.YamlUtils;
import net.momirealms.craftengine.libraries.yaml.SparrowYaml;

class EnbCombinedPackCompatibilityTest {
    private EnbCombinedPack.PackSettings settings(String source, Set<String> loaded, boolean sendOnJoin) throws Exception {
        return EnbCombinedPack.packSettings(Path.of("plugins/CraftEngine"),
                YamlUtils.reader(SparrowYaml.create().load(source)), loaded, sendOnJoin);
    }

    private String workflow(String trigger, String sendStep) {
        return """
                resource-pack:
                  workflows:
                    default:
                      trigger: %s
                      steps:
                        - type: generate
                        - type: zip
                          path: ./generated/resource_pack.zip
                        - type: upload
                          pack: default
                          path: ./generated/resource_pack.zip
                %s
                """.formatted(trigger, sendStep);
    }

    @Test void officialDefaultUsesWorkflowZipAndBlocksAutomaticResend() throws Exception {
        var config = settings(workflow("reload_pack", "        - type: send_pack\n          pack: default"), Set.of("default"), false);
        assertEquals(Path.of("plugins/CraftEngine/generated/resource_pack.zip").toAbsolutePath().normalize(), config.path());
        assertTrue(config.automaticDelivery());
    }

    @Test void explicitDisabledDeliveryAndRemovedSendStepAllowEnb() throws Exception {
        var config = settings(workflow("reload_pack", ""), Set.of("default"), false);
        assertNotNull(config.path());
        assertFalse(config.automaticDelivery());
        assertTrue(settings(workflow("reload_pack", ""), Set.of("default"), true).automaticDelivery());
        assertTrue(settings("resource-pack:\n  delivery:\n    resend-on-upload: true\n", Set.of(), false).automaticDelivery());
        assertFalse(settings("resource-pack:\n  delivery:\n    resend-on-upload: false\n", Set.of(), false).automaticDelivery());
    }

    @Test void manualDisabledOrUnloadedWorkflowCannotBlockAudio() throws Exception {
        String send = "        - type: send_pack\n          pack: default";
        assertFalse(settings(workflow("[]", send), Set.of("default"), false).automaticDelivery());
        assertFalse(settings(workflow("manual_only", send), Set.of("default"), false).automaticDelivery());
        assertFalse(settings(workflow("reload_pack", send), Set.of(), false).automaticDelivery());
        assertNull(settings(workflow("reload_pack", send), Set.of(), false).path());
    }

    @Test void listTriggerAndNamespacedWorkflowTypesMatchCraftEngine() throws Exception {
        String yaml = workflow("[manual_only, reload_pack]", "        - type: craftengine:send_pack\n          pack: default")
                .replace("type: zip", "type: craftengine:zip");
        var config = settings(yaml, Set.of("default"), false);
        assertNotNull(config.path());
        assertTrue(config.automaticDelivery());
    }
}
