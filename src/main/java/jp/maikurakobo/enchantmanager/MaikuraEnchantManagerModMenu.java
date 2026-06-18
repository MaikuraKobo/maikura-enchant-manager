package jp.maikurakobo.enchantmanager;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;

public class MaikuraEnchantManagerModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return MaikuraEnchantManagerConfigScreen::new;
    }

    private static final class MaikuraEnchantManagerConfigScreen extends Screen {
        private final Screen parent;

        private MaikuraEnchantManagerConfigScreen(Screen parent) {
            super(Text.literal("Maikura Enchant Manager 設定"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            int cx = this.width / 2;
            int y = this.height / 2 - 70;

            

            addDrawableChild(ButtonWidget.builder(label("非適正付与", MaikuraEnchantManagerMod.CONFIG.allowAllEnchantments), button -> {
                MaikuraEnchantManagerMod.CONFIG.allowAllEnchantments = !MaikuraEnchantManagerMod.CONFIG.allowAllEnchantments;
                MaikuraEnchantManagerMod.saveConfig();
                button.setMessage(label("非適正付与", MaikuraEnchantManagerMod.CONFIG.allowAllEnchantments));
            }).tooltip(Tooltip.of(Text.literal("全てタブを表示します"))).dimensions(cx - 110, y, 220, 20).build());

            addDrawableChild(ButtonWidget.builder(label("カテゴリフィルタ", MaikuraEnchantManagerMod.CONFIG.allowCategoryFilter), button -> {
                MaikuraEnchantManagerMod.CONFIG.allowCategoryFilter = !MaikuraEnchantManagerMod.CONFIG.allowCategoryFilter;
                MaikuraEnchantManagerMod.saveConfig();
                button.setMessage(label("カテゴリフィルタ", MaikuraEnchantManagerMod.CONFIG.allowCategoryFilter));
            }).tooltip(Tooltip.of(Text.literal("道具 / 武器 / 防具 / 弓 / 呪い / MOD を表示します"))).dimensions(cx - 110, y + 24, 220, 20).build());

            addDrawableChild(ButtonWidget.builder(label("高レベル付与", MaikuraEnchantManagerMod.CONFIG.allowUnsafeLevels), button -> {
                MaikuraEnchantManagerMod.CONFIG.allowUnsafeLevels = !MaikuraEnchantManagerMod.CONFIG.allowUnsafeLevels;
                MaikuraEnchantManagerMod.saveConfig();
                button.setMessage(label("高レベル付与", MaikuraEnchantManagerMod.CONFIG.allowUnsafeLevels));
            }).tooltip(Tooltip.of(Text.literal("Lv1 / Lv5 / Lv10 / Lv50 / Lv100 / Lv255 を表示します"))).dimensions(cx - 110, y + 48, 220, 20).build());

            addDrawableChild(ButtonWidget.builder(label("バニラ競合無視", MaikuraEnchantManagerMod.CONFIG.allowVanillaConflicts), button -> {
                MaikuraEnchantManagerMod.CONFIG.allowVanillaConflicts = !MaikuraEnchantManagerMod.CONFIG.allowVanillaConflicts;
                MaikuraEnchantManagerMod.saveConfig();
                button.setMessage(label("バニラ競合無視", MaikuraEnchantManagerMod.CONFIG.allowVanillaConflicts));
            }).tooltip(Tooltip.of(Text.literal("OFFではシルクタッチと幸運など、Minecraft標準の競合を尊重します"))).dimensions(cx - 110, y + 72, 220, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("完了"), button -> close()).dimensions(cx - 50, y + 114, 100, 20).build());
        }

        private static Text label(String name, boolean value) {
            return Text.literal(name + ": " + (value ? "ON" : "OFF"));
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public void close() {
            if (this.client != null) {
                this.client.setScreen(parent);
            }
        }
    }
}
