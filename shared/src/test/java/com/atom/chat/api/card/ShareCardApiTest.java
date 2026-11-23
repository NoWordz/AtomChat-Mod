package com.atom.chat.api.card;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ShareCardApiTest {

    @Test
    void parsesXaeroWaypointFormatsAndDimensions() {
        String raw = "<Steve> xaero-waypoint:绯红遗迹:绯:1284:72:-416:10:false:0:Internal-overworld-waypoints";
        ShareCard card = BuiltInCardParsers.parseXaeroWaypoint(raw, "/xaero_waypoint_add:test");
        assertNotNull(card);
        assertEquals(ShareCard.Template.LOCATION, card.template());
        assertNotNull(card.location());
        assertEquals("绯红遗迹", card.location().name());
        assertEquals(1284, card.location().blockX());
        assertEquals(72, card.location().blockY());
        assertEquals(-416, card.location().blockZ());
        assertEquals("minecraft:overworld", card.location().dimension());
        assertEquals("Xaero 地图", card.location().mapSource());
        assertEquals("/xaero_waypoint_add:test", card.location().nativeAddCommand());

        String customDim = "xaero_waypoint:BossRoom:B:-50:~:300:4:false:0:Internal-dim%twilightforest$twilight_forest-waypoints";
        ShareCard custom = BuiltInCardParsers.parseXaeroWaypoint(customDim, "");
        assertNotNull(custom);
        assertEquals("twilightforest:twilight_forest", custom.location().dimension());
        assertEquals(64, custom.location().blockY());

        // Xaero client-rewritten localized chat message: raw text has no coordinates; payload lives in ClickEvent.
        String rewritten = "ChatMiaoPT_Meow 从 overworld 分享了一个名为「Shared Location」的路径点！ [添加]";
        String clickCmd = "/xaero_waypoint_add:Shared^col^Location:S:256:72:-128:10:false:0:Internal-overworld-waypoints";
        ShareCard fromClick = BuiltInCardParsers.parseXaeroWaypoint(rewritten, clickCmd, "minecraft:the_nether");
        assertNotNull(fromClick);
        assertEquals("Shared:Location", fromClick.location().name());
        assertEquals(256, fromClick.location().blockX());
        assertEquals(72, fromClick.location().blockY());
        assertEquals(-128, fromClick.location().blockZ());
        assertEquals("minecraft:overworld", fromClick.location().dimension());
        assertEquals(clickCmd, fromClick.location().nativeAddCommand());

        // Internal-waypoints / External falls back to viewer's current dimension instead of bogus minecraft:internal-waypoints.
        String genericInternal = "/xaero_waypoint_add:Base:B:10:64:20:5:false:0:Internal-waypoints";
        ShareCard fallbackDim = BuiltInCardParsers.parseXaeroWaypoint(rewritten, genericInternal, "minecraft:the_nether");
        assertNotNull(fallbackDim);
        assertEquals("minecraft:the_nether", fallbackDim.location().dimension());
    }

    @Test
    void buildsFallbackRedPacketCardAndBlocksRedPacketDetailReceipts() {
        ShareCard rp = BuiltInCardParsers.buildRedPacketCard(
                "abc123", "恭喜发财，大吉大利", true, false, "", 5, "星币", "/redpacket claim abc123", false);
        assertNotNull(rp);
        assertEquals("rp:abc123", rp.cardId());
        assertEquals(ShareCard.Template.RED_PACKET, rp.template());
        assertEquals("共 5 个", rp.badgeText());
        assertEquals("/redpacket claim abc123", rp.bodyAction().value());

        // Red-packet follow-up "[详情]" receipts must NOT become redundant white single-action cards
        var claimReceipt = new UniversalCardMatcher.MessageSignals(
                "[红包] 你领取了 ChatMiaoPT_Meow 的红包，获得 1 星币  [ 详情 ]",
                "", true, java.util.List.of("msg.module_shop_redpacket.tag", "msg.module_shop_redpacket.claimed"),
                java.util.List.of(
                        new UniversalCardMatcher.ClickActionSpec("[ 详情 ]", ShareCard.ActionKind.RUN_COMMAND, "/redpacket info abc123")
                ), false, "minecraft:overworld");
        assertNull(UniversalCardMatcher.matchSingleAction(claimReceipt));

        var finishedReceipt = new UniversalCardMatcher.MessageSignals(
                "[红包] ChatMiaoPT_Meow 的红包已被领完（1 个，用时 2 分 56 秒）  [ 详情 ]",
                "", true, java.util.List.of("msg.module_shop_redpacket.tag", "msg.module_shop_redpacket.finished"),
                java.util.List.of(
                        new UniversalCardMatcher.ClickActionSpec("[ 详情 ]", ShareCard.ActionKind.RUN_COMMAND, "/redpacket info abc123")
                ), false, "minecraft:overworld");
        assertNull(UniversalCardMatcher.matchSingleAction(finishedReceipt));

        // Passive "[详情]" / "[details]" notices from other mods must also stay compact text lines
        var genericDetail = new UniversalCardMatcher.MessageSignals(
                "[拍卖行] 你的商品已售出，获得 500 星币 [详情]",
                "", true, java.util.List.of(),
                java.util.List.of(
                        new UniversalCardMatcher.ClickActionSpec("[详情]", ShareCard.ActionKind.RUN_COMMAND, "/ah history 12")
                ), false, "minecraft:overworld");
        assertNull(UniversalCardMatcher.matchSingleAction(genericDetail));
    }

    @Test
    void parsesJourneyMapAndVoxelMapBracketWaypoints() {
        String jm = "<Alex> [name:\"主城基地\", x:100, y:65, z:-250, dim:minecraft:the_nether]";
        ShareCard card = BuiltInCardParsers.parseBracketWaypoint(jm, "", "JourneyMap");
        assertNotNull(card);
        assertEquals(ShareCard.Template.LOCATION, card.template());
        assertEquals("主城基地", card.location().name());
        assertEquals(100, card.location().blockX());
        assertEquals(65, card.location().blockY());
        assertEquals(-250, card.location().blockZ());
        assertEquals("minecraft:the_nether", card.location().dimension());
        assertEquals("JourneyMap", card.location().mapSource());
    }

    @Test
    void parsesFtbChunksCoordinateShare() {
        ShareCard card = BuiltInCardParsers.parseFtbCoordinate("FTB Chunks: [12, 80, -34]", "minecraft:the_end", "");
        assertNotNull(card);
        assertEquals(12, card.location().blockX());
        assertEquals(80, card.location().blockY());
        assertEquals(-34, card.location().blockZ());
        assertEquals("minecraft:the_end", card.location().dimension());
    }

    @Test
    void buildsTpaCardAndDimsViaStateStore() {
        CardStateStore.clear();
        ShareCard tpa1 = BuiltInCardParsers.buildTpaRequestCard("Steve", false, "/tpyes", "/tpno", "繁星传送 · 传送请求");
        ShareCard tpa2 = BuiltInCardParsers.buildTpaRequestCard("Steve", false, "/tpyes", "/tpno", "繁星传送 · 传送请求");
        assertTrue(tpa1.cardId().startsWith("tpa:steve:"));
        assertNotEquals(tpa1.cardId(), tpa2.cardId(), "repeated TPA requests must not collide on cardId");
        assertEquals(2, tpa1.actions().size());
        assertEquals("/tpyes", tpa1.actions().get(0).value());
        assertEquals("/tpno", tpa1.actions().get(1).value());

        long revBefore = CardStateStore.revision();
        CardStateStore.update(CardState.dimmed(tpa1.cardId(), "已接受传送请求")
                .badgeText("已接受").disableActions(true).build());
        assertTrue(CardStateStore.revision() > revBefore);
        CardState stored = CardStateStore.get(tpa1.cardId());
        assertNotNull(stored);
        assertTrue(stored.dimmed());
        assertTrue(stored.disableActions());
        assertEquals("已接受", stored.badgeText());
        assertNull(CardStateStore.get(tpa2.cardId()), "new TPA card must not inherit old dimmed state");
    }

    @Test
    void blocksDimensionCommandInjection() {
        String evil = "[name:trap, x:0, y:-300, z:0, dim:minecraft:overworld as @a]";
        ShareCard card = BuiltInCardParsers.parseBracketWaypoint(evil, "", "JourneyMap");
        assertNotNull(card);
        assertEquals("minecraft:overworld", card.location().dimension());
        assertFalse(BuiltInCardParsers.isValidDimension("minecraft:overworld as @a"));
        assertTrue(BuiltInCardParsers.isValidDimension("twilightforest:twilight_forest"));
    }

    @Test
    void stateStoreUsesLruInsteadOfWipingAll() {
        CardStateStore.clear();
        for (int i = 0; i < 520; i++) {
            CardStateStore.update(CardState.dimmed("rp:" + i, "已领取").build());
        }
        assertNull(CardStateStore.get("rp:0"), "oldest entries evict via LRU");
        assertNotNull(CardStateStore.get("rp:519"), "recent entries stay intact");
    }

    @Test
    void wireCodecRoundTripsCardsAndStates() {
        ShareCard rp = ShareCard.redPacket("rp:abcd12")
                .title("恭喜发财，大吉大利")
                .subtitle("拼手气红包 · 点击领取")
                .source("模块商店 · 红包")
                .badge("剩余 5/10")
                .redPacketPayload(new ShareCard.RedPacketPayload(
                        "abcd12", "恭喜发财，大吉大利", "拼手气红包 · 点击领取",
                        true, false, "", 5, 10, "星币", false))
                .onClickCommand("/redpacket claim abcd12")
                .serverAuthoritative(true)
                .build();

        byte[] encoded = CardWireCodec.encodeCard(rp);
        ShareCard decoded = CardWireCodec.decodeCard(encoded);
        assertNotNull(decoded);
        assertEquals("rp:abcd12", decoded.cardId());
        assertEquals(ShareCard.Template.RED_PACKET, decoded.template());
        assertTrue(decoded.serverAuthoritative());
        assertEquals("恭喜发财，大吉大利", decoded.redPacket().greeting());
        assertEquals("/redpacket claim abcd12", decoded.bodyAction().value());

        CardState state = CardState.dimmed("rp:abcd12", "已领取 · 88 星币")
                .badgeText("已领取")
                .onClickCommand("/redpacket info abcd12")
                .build();
        CardState decodedState = CardWireCodec.decodeState(CardWireCodec.encodeState(state));
        assertNotNull(decodedState);
        assertTrue(decodedState.dimmed());
        assertEquals("已领取 · 88 星币", decodedState.statusText());
        assertEquals("/redpacket info abcd12", decodedState.overrideBodyAction().value());
    }

    @Test
    void dispatchesClientCallbacksThroughRegistry() {
        AtomicBoolean fired = new AtomicBoolean(false);
        CardRegistry.registerCallback("demo:open", ctx -> fired.set(true));
        ShareCard card = ShareCard.general("Demo")
                .addAction("demo:open", "打开", ShareCard.ActionKind.CLIENT_CALLBACK, "demo:open", true)
                .build();
        assertTrue(CardRegistry.dispatchCallback(
                new CardRegistry.ActionCallbackContext(card, null, card.actions().get(0))));
        assertTrue(fired.get());
    }

    @Test
    void minimapCodecRoundTripsAndValidatesBounds() {
        int w = MinimapPreviewCodec.DEFAULT_WIDTH;
        int h = MinimapPreviewCodec.DEFAULT_HEIGHT;
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                pixels[y * w + x] = (x > 30 && x < 42) ? 0xFF3182CE : 0xFF6BA64E;
            }
        }
        String encoded = MinimapPreviewCodec.encode(w, h, pixels);
        assertNotNull(encoded);
        assertTrue(encoded.length() < 4_000, "typical terrain preview should stay compact");
        assertEquals(encoded, MinimapPreviewCodec.validateEncoded(encoded));

        MinimapPreviewCodec.Decoded decoded = MinimapPreviewCodec.decode(encoded);
        assertNotNull(decoded);
        assertEquals(w, decoded.width());
        assertEquals(h, decoded.height());
        assertEquals(w * h * 4, decoded.rgbaPixels().length);
        // Check a water pixel roughly matches blue channel > red channel
        int waterIdx = (20 * w + 36) * 4;
        int r = decoded.rgbaPixels()[waterIdx] & 0xFF;
        int b = decoded.rgbaPixels()[waterIdx + 2] & 0xFF;
        assertTrue(b > r + 80, "water pixel preserves blue color");

        // Reject malformed or oversized payloads
        assertNull(MinimapPreviewCodec.validateEncoded("!!!not-base64!!!"));
        assertNull(MinimapPreviewCodec.encode(200, 200, new int[200 * 200]));
    }

    @Test
    void universalMatcherAutoAdaptsPairRequestsAndSingleActions() {
        // 1. Generic Party invite -> auto interactive card with requester avatar & verified native clicks
        var partySignals = new UniversalCardMatcher.MessageSignals(
                "[组队] Steve 邀请你加入冒险小队 [同意] [拒绝]",
                "", true, java.util.List.of(),
                java.util.List.of(
                        new UniversalCardMatcher.ClickActionSpec("[同意]", ShareCard.ActionKind.RUN_COMMAND, "/party accept Steve"),
                        new UniversalCardMatcher.ClickActionSpec("[拒绝]", ShareCard.ActionKind.RUN_COMMAND, "/party deny Steve")
                ), false, "minecraft:overworld");
        ShareCard partyCard = UniversalCardMatcher.matchPairRequest(partySignals);
        assertNotNull(partyCard);
        assertTrue(partyCard.cardId().startsWith("req:party:"));
        assertEquals("Steve", partyCard.general().avatarPlayerName());
        assertEquals("组队", partyCard.sourceLabel());
        assertEquals(2, partyCard.actions().size());
        assertTrue(partyCard.isNativeClickCommand("/party accept Steve"));

        // 2. Generic Mail single-button system notification -> auto action card
        var mailSignals = new UniversalCardMatcher.MessageSignals(
                "[邮件] 你收到每日签到奖励 [领取]",
                "", true, java.util.List.of(),
                java.util.List.of(
                        new UniversalCardMatcher.ClickActionSpec("[领取]", ShareCard.ActionKind.RUN_COMMAND, "/mail claim 42")
                ), false, "minecraft:overworld");
        ShareCard mailCard = UniversalCardMatcher.matchSingleAction(mailSignals);
        assertNotNull(mailCard);
        assertEquals("邮件", mailCard.sourceLabel());
        assertEquals(1, mailCard.actions().size());
        assertEquals("领取", mailCard.actions().get(0).label());
        assertTrue(mailCard.isNativeClickCommand("/mail claim 42"));

        // 3. Generic coordinate + /tp click command -> auto Location card
        var locSignals = new UniversalCardMatcher.MessageSignals(
                "[传送点] 遗迹入口 (256, 70, -512) [前往]",
                "", true, java.util.List.of(),
                java.util.List.of(
                        new UniversalCardMatcher.ClickActionSpec("[前往]", ShareCard.ActionKind.RUN_COMMAND, "/stp 256 70 -512")
                ), false, "minecraft:overworld");
        ShareCard locCard = UniversalCardMatcher.matchGenericLocation(locSignals);
        assertNotNull(locCard);
        assertEquals(ShareCard.Template.LOCATION, locCard.template());
        assertEquals(256, locCard.location().blockX());
        assertEquals(70, locCard.location().blockY());
        assertEquals(-512, locCard.location().blockZ());
    }

    @Test
    void universalMatcherGuardrailsBlockFalsePositivesAndDangerousCommands() {
        // Dangerous admin / double-slash / economy / session-hijack commands MUST be blocked
        assertTrue(UniversalCardMatcher.isDangerousCommand("/op Steve"));
        assertTrue(UniversalCardMatcher.isDangerousCommand("//eval Runtime.getRuntime()"));
        assertTrue(UniversalCardMatcher.isDangerousCommand("//set 0"));
        assertTrue(UniversalCardMatcher.isDangerousCommand("/transfer evil.host 25565"));
        assertTrue(UniversalCardMatcher.isDangerousCommand("/tp @a 0 -100 0"));
        assertTrue(UniversalCardMatcher.isDangerousCommand("/pay Griefer 50000"));
        assertTrue(UniversalCardMatcher.isDangerousCommand("minecraft:ban Alex"));
        assertTrue(UniversalCardMatcher.isDangerousCommand("/execute as @a run stop"));
        assertTrue(UniversalCardMatcher.isDangerousCommand("/lp user Steve parent set admin"));
        assertFalse(UniversalCardMatcher.isDangerousCommand("/party accept Steve"));

        // Non-system player chat must NEVER trigger pair-request phishing cards
        var playerPhish = new UniversalCardMatcher.MessageSignals(
                "[交易] 系统补贴 [同意] [取消]", "Griefer", false, java.util.List.of(),
                java.util.List.of(
                        new UniversalCardMatcher.ClickActionSpec("[同意]", ShareCard.ActionKind.RUN_COMMAND, "/party accept Griefer"),
                        new UniversalCardMatcher.ClickActionSpec("[取消]", ShareCard.ActionKind.RUN_COMMAND, "/party deny Griefer")
                ), false, "minecraft:overworld");
        assertNull(UniversalCardMatcher.matchPairRequest(playerPhish), "player chat must never trigger pair request");

        // Polarity inversion guard ("不同意" must NEVER match positive "同意") & ASCII exact-match guard ("[Notch]"/"[Book]")
        assertFalse(UniversalCardMatcher.isAcceptCommandOrLabel("[不同意]", "/vote no"));
        assertTrue(UniversalCardMatcher.isDenyCommandOrLabel("[不同意]", "/vote no"));
        var asciiFalsePos = new UniversalCardMatcher.MessageSignals(
                "[Notch] 送给了 [Book] 一个礼物", "", true, java.util.List.of(),
                java.util.List.of(
                        new UniversalCardMatcher.ClickActionSpec("[Notch]", ShareCard.ActionKind.RUN_COMMAND, "/profile Notch"),
                        new UniversalCardMatcher.ClickActionSpec("[Book]", ShareCard.ActionKind.RUN_COMMAND, "/profile Book")
                ), false, "minecraft:overworld");
        assertNull(UniversalCardMatcher.matchPairRequest(asciiFalsePos), "Notch/Book must not match no/ok");

        var evilPair = new UniversalCardMatcher.MessageSignals(
                "领福利 [接受] [拒绝]", "", true, java.util.List.of(),
                java.util.List.of(
                        new UniversalCardMatcher.ClickActionSpec("[接受]", ShareCard.ActionKind.RUN_COMMAND, "/op Evil"),
                        new UniversalCardMatcher.ClickActionSpec("[拒绝]", ShareCard.ActionKind.RUN_COMMAND, "/party deny")
                ), false, "minecraft:overworld");
        assertNull(UniversalCardMatcher.matchPairRequest(evilPair), "dangerous command must never auto-adapt");

        // Pagination menus must NOT become cards
        var pageMenu = new UniversalCardMatcher.MessageSignals(
                "帮助列表 [上一页] [下一页]", "", true, java.util.List.of(),
                java.util.List.of(
                        new UniversalCardMatcher.ClickActionSpec("[上一页]", ShareCard.ActionKind.RUN_COMMAND, "/help 1"),
                        new UniversalCardMatcher.ClickActionSpec("[下一页]", ShareCard.ActionKind.RUN_COMMAND, "/help 3")
                ), false, "minecraft:overworld");
        assertNull(UniversalCardMatcher.matchPairRequest(pageMenu));

        // Non-system player chat must NOT trigger single-action card
        var playerFake = new UniversalCardMatcher.MessageSignals(
                "点我领奖 [领取]", "Griefer", false, java.util.List.of(),
                java.util.List.of(
                        new UniversalCardMatcher.ClickActionSpec("[领取]", ShareCard.ActionKind.RUN_COMMAND, "/pay Griefer 1000")
                ), false, "minecraft:overworld");
        assertNull(UniversalCardMatcher.matchSingleAction(playerFake));

        // nativeClickCommands MUST NOT survive wire serialization (anti-spoofing!)
        ShareCard withNative = ShareCard.general("Test").build()
                .withNativeClickCommands(java.util.List.of("/party accept Steve"));
        assertTrue(withNative.isNativeClickCommand("/party accept Steve"));
        ShareCard afterWire = CardWireCodec.decodeCard(CardWireCodec.encodeCard(withNative));
        assertNotNull(afterWire);
        assertFalse(afterWire.isNativeClickCommand("/party accept Steve"),
                "wire-decoded cards must never inherit transient nativeClickCommands");
    }

    @Test
    void jsonRuleEngineAndSignalAdaptersWorkSafely() {
        String json = """
                {
                  "version": 1,
                  "enabled": true,
                  "rules": [
                    {
                      "id": "custom_boss",
                      "enabled": true,
                      "priority": 10,
                      "systemOnly": true,
                      "textRegex": "世界首领 (.+) 已刷新",
                      "commandPrefix": "/boss",
                      "title": "世界首领：$1",
                      "subtitle": "限时讨伐已开启",
                      "source": "副本活动",
                      "accentColor": "#EF4444",
                      "buttonLabel": "立即讨伐"
                    },
                    {
                      "id": "broken_regex",
                      "enabled": true,
                      "textRegex": "[unclosed"
                    },
                    {
                      "id": "redos_trap",
                      "enabled": true,
                      "priority": 1,
                      "textRegex": "(a+)+$"
                    },
                    {
                      "id": "rp_rule",
                      "enabled": true,
                      "priority": 5,
                      "template": "RED_PACKET",
                      "textRegex": "(.+) 发了一个红包 (.+)",
                      "commandPrefix": "/rp",
                      "title": "$1 · $2",
                      "source": "活动商店"
                    }
                  ]
                }
                """;
        assertEquals(3, CardRuleEngine.loadFromJson(json), "invalid regex rule skipped safely");
        var bossSignals = new UniversalCardMatcher.MessageSignals(
                "[活动] 世界首领 暗影巨龙 已刷新 [前往]",
                "", true, java.util.List.of(),
                java.util.List.of(new UniversalCardMatcher.ClickActionSpec("[前往]", ShareCard.ActionKind.RUN_COMMAND, "/boss tp dragon")),
                false, "minecraft:overworld");
        ShareCard bossCard = CardRuleEngine.match(bossSignals);
        assertNotNull(bossCard);
        assertEquals("世界首领：暗影巨龙", bossCard.title());
        assertEquals("副本活动", bossCard.sourceLabel());
        assertEquals("立即讨伐", bossCard.actions().get(0).label());
        assertTrue(bossCard.isNativeClickCommand("/boss tp dragon"));

        // Verify ReDoS trap terminates immediately without freezing
        var redosSignals = new UniversalCardMatcher.MessageSignals(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!", "", true, java.util.List.of(),
                java.util.List.of(), false, "minecraft:overworld");
        assertNull(CardRuleEngine.match(redosSignals));

        // Verify RED_PACKET template honoring + single-pass $1/$2 anti-cross-injection
        var rpSignals = new UniversalCardMatcher.MessageSignals(
                "$2 发了一个红包 新年快乐", "", true, java.util.List.of(),
                java.util.List.of(new UniversalCardMatcher.ClickActionSpec("[领取]", ShareCard.ActionKind.RUN_COMMAND, "/rp claim 7")),
                false, "minecraft:overworld");
        ShareCard rpRuleCard = CardRuleEngine.match(rpSignals);
        assertNotNull(rpRuleCard);
        assertEquals(ShareCard.Template.RED_PACKET, rpRuleCard.template());
        assertEquals("$2 · 新年快乐", rpRuleCard.title(), "$1 containing $2 must not re-expand into group 2");
        CardRuleEngine.clear();

        // Priority SignalCardAdapter dispatch
        CardRegistry.SignalCardAdapter low = new CardRegistry.SignalCardAdapter() {
            @Override public ShareCard adapt(UniversalCardMatcher.MessageSignals s) { return ShareCard.general("low").build(); }
            @Override public int priority() { return 1; }
        };
        CardRegistry.SignalCardAdapter high = new CardRegistry.SignalCardAdapter() {
            @Override public ShareCard adapt(UniversalCardMatcher.MessageSignals s) { return ShareCard.general("high").build(); }
            @Override public int priority() { return 50; }
        };
        CardRegistry.registerSignalAdapter(low);
        CardRegistry.registerSignalAdapter(high);
        try {
            ShareCard chosen = CardRegistry.adaptSignals(bossSignals);
            assertNotNull(chosen);
            assertEquals("high", chosen.title());
        } finally {
            CardRegistry.unregisterSignalAdapter(low);
            CardRegistry.unregisterSignalAdapter(high);
        }
    }
}
