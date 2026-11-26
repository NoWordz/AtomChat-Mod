package com.atom.chat.example.jei;

import com.atom.chat.api.card.ItemCardActions;
import com.atom.chat.api.card.ItemCardItems;
import com.atom.chat.api.card.ShareCard;
import com.atom.chat.api.ui.ComposerUi;
import com.atom.chat.api.ui.NavigationUi;
import com.atom.chat.api.ui.SettingsUi;
import com.atom.chat.api.ui.UiContributions;
import com.atom.chat.settings.SettingsSection;
import io.github.humbleui.skija.Path;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.util.Identifier;

/** A separate client mod that contributes a recipe lookup for item cards. */
@JeiPlugin
public final class AtomChatJeiPlugin implements IModPlugin {
    private static final String MOD_ID = "atomchat_jei_example";
    private static final String LOOKUP = MOD_ID + ":show_recipes";
    private static final String PAGE = MOD_ID + ":recipes";
    private static volatile IJeiRuntime runtime;
    private static boolean showCardLookup = true;
    private static ShareCard lastItem;

    static {
        ItemCardActions.register(new ItemCardActions.Action(LOOKUP,
                card -> I18n.translate("atomchat_jei_example.show_recipes"),
                card -> showCardLookup && runtime != null && !ItemCardItems.copyStack(card).isEmpty(),
                AtomChatJeiPlugin::showRecipes));
        registerExampleUi();
    }

    @Override
    public Identifier getPluginUid() {
        return Identifier.tryParse(MOD_ID + ":item_cards");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime available) {
        runtime = available;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        lastItem = null;
    }

    private static void registerExampleUi() {
        Path icon = Path.makeFromSVGString("M4 4h13v15H4z M7 8h7 M7 12h7 M7 16h4");
        NavigationUi.registerPage(PAGE, new NavigationUi.Page("atomchat_jei_example.page", () ->
                (canvas, viewport, context) -> {
                    float x = viewport.x() + 12F;
                    float y = viewport.y() + 28F;
                    String message = lastItem == null ? context.translate("atomchat_jei_example.empty")
                            : context.translate("atomchat_jei_example.recent") + " " + lastItem.title();
                    context.drawLabel(canvas, message, x, y);
                }));
        NavigationUi.tabs().add(PAGE, new NavigationUi.Tab(PAGE, icon),
                UiContributions.Position.after(NavigationUi.CHAT));
        ComposerUi.buttons().add(MOD_ID + ":recipe_button",
                new ComposerUi.Button(icon, context -> context.openPage(PAGE), ComposerUi.ButtonShape.SQUARE),
                UiContributions.Position.before(ComposerUi.SEND_BUTTON));
        var stock = ComposerUi.defaultSpacing();
        ComposerUi.styles().replace(MOD_ID + ":button_gap", ComposerUi.SPACING,
                new ComposerUi.Spacing(stock.gap() + 2F, stock.leadingInset(), stock.trailingInset()));
        ComposerUi.menu().add(MOD_ID + ":recipe_menu",
                new ComposerUi.MenuItem("atomchat_jei_example.page", icon, context -> context.openPage(PAGE)),
                UiContributions.Position.last());
        SettingsUi.home().add(MOD_ID + ":recipe_home",
                new SettingsUi.HomeEntry("atomchat_jei_example.page", icon, PAGE), UiContributions.Position.last());
        SettingsUi.tabs(SettingsSection.CHAT).add(PAGE, new SettingsUi.Chip("atomchat_jei_example.page"),
                UiContributions.Position.last());
        SettingsUi.groups(SettingsSection.CHAT, PAGE).add(MOD_ID + ":options",
                new SettingsUi.Group("atomchat_jei_example.options", List.of(new SettingsUi.Toggle(
                        MOD_ID + ":show_card_lookup", "atomchat_jei_example.lookup_setting",
                        "atomchat_jei_example.lookup_setting.desc", () -> showCardLookup,
                        value -> showCardLookup = value))), UiContributions.Position.last());
    }

    private static boolean showRecipes(ShareCard card) {
        IJeiRuntime active = runtime;
        if (active == null) return false;
        var item = ItemCardItems.copyStack(card);
        if (item.isEmpty()) return false;
        var focus = active.getJeiHelpers().getFocusFactory()
                .createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, item);
        active.getRecipesGui().show(focus);
        lastItem = card;
        return true;
    }
}
