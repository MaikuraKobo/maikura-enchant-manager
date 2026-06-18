package jp.maikurakobo.enchantmanager;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class EnchantManagerScreen extends HandledScreen<MaikuraEnchantManagerMod.EnchantManagerScreenHandler> {
    private static final int BG = 0xEE17111F;
    private static final int PANEL = 0xAA2A2038;
    private static final int PANEL_DARK = 0xAA21182E;
    private static final int LINE = 0xFFB48C3A;
    private static final int LINE_DIM = 0xFF4A3A62;
    private static final int GOLD = 0xFFFFD966;
    private static final int CYAN = 0xFF80D8FF;
    private static final int WHITE = 0xFFE8E8E8;
    private static final int GRAY = 0xFFB0B0B0;
    private static final int GREEN = 0xFF9BE26C;
    private static final int RED = 0xFFFF7777;

    private boolean searchFocused = false;
    private TextFieldWidget searchBox;

    public EnchantManagerScreen(MaikuraEnchantManagerMod.EnchantManagerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 332;
        this.backgroundHeight = 256;
        this.playerInventoryTitleY = 158;
        this.titleX = 10;
        this.titleY = 8;
    }

    @Override
    protected void init() {
        super.init();
        this.x = (this.width - this.backgroundWidth) / 2;
        this.y = (this.height - this.backgroundHeight) / 2;

        // r45: 入力処理はTextFieldWidgetに任せる。
        // 1.21.11ではcharTyped/keyPressedのシグネチャ差異があるため、直接overrideしない。
        this.searchBox = new TextFieldWidget(this.textRenderer, this.x + 244, this.y + 13, 76, 10, Text.literal(""));
        this.searchBox.setMaxLength(50);
        this.searchBox.setChangedListener(text -> {
            if (this.client != null && this.client.getNetworkHandler() != null) {
                ClientPlayNetworking.send(new MaikuraEnchantManagerMod.SearchQueryPayload(this.handler.syncId, text));
            }
        });
        this.searchBox.setDrawsBackground(false);
        this.addDrawableChild(this.searchBox);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = this.x;
        int y = this.y;
        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, BG);
        drawBorder(context, x, y, backgroundWidth, backgroundHeight, LINE);

        panel(context, x + 8, y + 28, 64, 220);
        panel(context, x + 78, y + 28, 110, 132);
        panel(context, x + 194, y + 28, 130, 132);

        // 対象アイテム投入スロットを明確化
        slotFrame(context, x + 19, y + 47);

        // プレイヤーインベントリ枠を復活
        inventoryPanel(context, x + 84, y + 166);
        // 検索欄は検索実装前の表示枠。r37では安定版として入力処理は未実装。
        context.fill(x + 204, y + 10, x + 324, y + 24, PANEL_DARK);
        drawBorder(context, x + 204, y + 10, 120, 14, LINE_DIM);
    }

    private static void panel(DrawContext context, int x, int y, int width, int height) {
        context.fill(x, y, x + width, y + height, PANEL);
        drawBorder(context, x, y, width, height, LINE_DIM);
    }

    private static void slotFrame(DrawContext context, int x, int y) {
        context.fill(x, y, x + 20, y + 20, 0xFF0F0B16);
        drawBorder(context, x, y, 20, 20, 0xFF8C86A0);
        context.fill(x + 2, y + 2, x + 18, y + 18, 0xFF2B2632);
    }

    private static void inventoryPanel(DrawContext context, int x, int y) {
        context.fill(x - 4, y - 4, x + 166, y + 80, 0x66302040);
        drawBorder(context, x - 4, y - 4, 170, 84, LINE_DIM);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slotFrame(context, x + col * 18 - 1, y + row * 18 - 1);
            }
        }
        for (int col = 0; col < 9; col++) {
            slotFrame(context, x + col * 18 - 1, y + 58 - 1);
        }
    }

    private void drawSmallText(DrawContext context, String text, int x, int y, int color) {
        context.drawText(this.textRenderer, Text.literal(text), x, y, color, false);
    }

    private static void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y, x + 1, y + height, color);
        context.fill(x + width - 1, y, x + width, y + height, color);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, GOLD, false);
        drawSmallText(context, "検索:", 208, 13, CYAN);
        boolean focused = this.searchBox != null && this.searchBox.isFocused();
        String query = this.searchBox == null ? "" : this.searchBox.getText();
        if (query.isBlank()) {
            drawSmallText(context, focused ? "入力中..." : "検索...", 244, 13, focused ? GOLD : GRAY);
        }
        drawBorder(context, 204, 10, 120, 14, focused ? GOLD : LINE_DIM);

        drawSmallText(context, "対象アイテム", 14, 32, CYAN);
        drawSmallText(context, "エンチャント一覧", 98, 32, CYAN);
        drawSmallText(context, "付与済み", 202, 32, CYAN);

        drawTextButton(context, 12, 72, 28, 14, "適正", MaikuraEnchantManagerMod.EnchantManagerScreenHandler.TAB_SUITABLE_SLOT, mouseX, mouseY);
        if (MaikuraEnchantManagerMod.CONFIG.showAllTab()) {
            drawTextButton(context, 42, 72, 28, 14, "全て", MaikuraEnchantManagerMod.EnchantManagerScreenHandler.TAB_ALL_SLOT, mouseX, mouseY);
        }

        if (MaikuraEnchantManagerMod.CONFIG.showCategoryFilter()) {
            drawSmallText(context, "カテゴリ", 14, 89, CYAN);
            drawTextButton(context, 12, 99, 28, 13, "全", MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CAT_ALL_SLOT, mouseX, mouseY);
            drawTextButton(context, 42, 99, 28, 13, "道具", MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CAT_TOOL_SLOT, mouseX, mouseY);
            drawTextButton(context, 12, 114, 28, 13, "武器", MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CAT_WEAPON_SLOT, mouseX, mouseY);
            drawTextButton(context, 42, 114, 28, 13, "防具", MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CAT_ARMOR_SLOT, mouseX, mouseY);
            drawTextButton(context, 12, 129, 28, 13, "弓", MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CAT_RANGED_SLOT, mouseX, mouseY);
            drawTextButton(context, 42, 129, 28, 13, "呪い", MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CAT_CURSE_SLOT, mouseX, mouseY);
            drawTextButton(context, 12, 144, 58, 13, "MOD", MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CAT_MODDED_SLOT, mouseX, mouseY);
        }

        drawSmallText(context, "付与Lv", 14, MaikuraEnchantManagerMod.CONFIG.showCategoryFilter() ? 161 : 91, CYAN);
        int lvY = MaikuraEnchantManagerMod.CONFIG.showCategoryFilter() ? 171 : 101;
        drawTextButton(context, 12, lvY, 28, 12, "通常", MaikuraEnchantManagerMod.EnchantManagerScreenHandler.LEVEL_NORMAL_SLOT, mouseX, mouseY);
        if (MaikuraEnchantManagerMod.CONFIG.showUnsafeLevels()) {
            drawTextButton(context, 42, lvY, 28, 12, "Lv1", MaikuraEnchantManagerMod.EnchantManagerScreenHandler.LEVEL_1_SLOT, mouseX, mouseY);
            drawTextButton(context, 12, lvY + 15, 28, 12, "Lv5", MaikuraEnchantManagerMod.EnchantManagerScreenHandler.LEVEL_5_SLOT, mouseX, mouseY);
            drawTextButton(context, 42, lvY + 15, 28, 12, "Lv10", MaikuraEnchantManagerMod.EnchantManagerScreenHandler.LEVEL_10_SLOT, mouseX, mouseY);
            drawTextButton(context, 12, lvY + 30, 58, 12, "Lv50", MaikuraEnchantManagerMod.EnchantManagerScreenHandler.LEVEL_50_SLOT, mouseX, mouseY);
            drawTextButton(context, 12, lvY + 45, 58, 12, "Lv100", MaikuraEnchantManagerMod.EnchantManagerScreenHandler.LEVEL_100_SLOT, mouseX, mouseY);
            drawTextButton(context, 12, lvY + 60, 58, 12, "Lv255", MaikuraEnchantManagerMod.EnchantManagerScreenHandler.LEVEL_255_SLOT, mouseX, mouseY);
        }
        drawTextButton(context, 198, 146, 54, 12, "全付与", MaikuraEnchantManagerMod.EnchantManagerScreenHandler.APPLY_ALL_SLOT, mouseX, mouseY);
        drawTextButton(context, 262, 146, 56, 12, "全削除", MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CLEAR_SLOT, mouseX, mouseY);

        drawEnchantList(context, mouseX, mouseY);
        drawCurrentList(context, mouseX, mouseY);
    }

    private String label(int menuSlot, String fallback) {
        ItemStack stack = handler.getMenuStack(menuSlot);
        if (stack.isEmpty()) return fallback;
        String text = stack.getName().getString();
        text = text.replace("カテゴリ: ", "");
        if (text.length() > 4) text = text.substring(0, 4);
        return text;
    }

    private void drawEnchantList(DrawContext context, int mouseX, int mouseY) {
        int listRows = MaikuraEnchantManagerMod.EnchantManagerScreenHandler.LIST_COUNT;
        for (int i = 0; i < listRows; i++) {
            int menuSlot = MaikuraEnchantManagerMod.EnchantManagerScreenHandler.LIST_START + i;
            ItemStack stack = handler.getMenuStack(menuSlot);
            int rowY = 46 + i * 10;
            int color = stack.isEmpty() ? GRAY : WHITE;
            String name = stack.isEmpty() ? "" : stack.getName().getString();
            boolean selected = isSelectedInfo(name);
            if (selected) {
                context.fill(82, rowY - 1, 182, rowY + 9, 0x884A3A62);
                color = GOLD;
            } else if (isInside(mouseX, mouseY, 82, rowY - 1, 100, 10)) {
                context.fill(82, rowY - 1, 182, rowY + 9, 0x553D3155);
                color = GOLD;
            }
            drawSmallText(context, trim(name, 16), 84, rowY, color);
        }
        drawListScrollBar(context);
    }

    private void drawCurrentList(DrawContext context, int mouseX, int mouseY) {
        int currentRows = MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CURRENT_COUNT;
        for (int i = 0; i < currentRows; i++) {
            int menuSlot = MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CURRENT_START + i;
            ItemStack stack = handler.getMenuStack(menuSlot);
            int rowY = 46 + i * 10;
            int color = stack.isEmpty() ? GRAY : GREEN;
            if (isInside(mouseX, mouseY, 198, rowY - 1, 122, 10)) {
                context.fill(198, rowY - 1, 320, rowY + 9, 0x553D3155);
                color = GOLD;
            }
            String name = stack.isEmpty() ? "" : stack.getName().getString();
            drawSmallText(context, trim(name, 20), 200, rowY, color);
            if (!stack.isEmpty()) {
                context.fill(306, rowY - 1, 322, rowY + 9, 0x55221122);
                drawBorder(context, 306, rowY - 1, 16, 10, RED);
                drawSmallText(context, "×", 311, rowY, RED);
            }
        }
        drawCurrentScrollBar(context);
    }

    private void drawListScrollBar(DrawContext context) {
        int total = handler.getListTotalCount();
        int offset = handler.getListScrollOffset();
        drawScrollBar(context, 183, 46, 106, total, MaikuraEnchantManagerMod.EnchantManagerScreenHandler.LIST_COUNT, offset);
    }

    private void drawCurrentScrollBar(DrawContext context) {
        int total = handler.getCurrentTotalCount();
        int offset = handler.getCurrentScrollOffset();
        drawScrollBar(context, 320, 46, 96, total, MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CURRENT_COUNT, offset);
    }

    private void drawScrollBar(DrawContext context, int x, int y, int height, int total, int visible, int offset) {
        if (total <= visible || visible <= 0) return;
        context.fill(x, y, x + 4, y + height, 0xAA3D3155);
        int knobHeight = Math.max(10, height * visible / total);
        int maxOffset = Math.max(1, total - visible);
        int knobY = y + (height - knobHeight) * Math.max(0, Math.min(offset, maxOffset)) / maxOffset;
        context.fill(x, knobY, x + 4, knobY + knobHeight, 0xFFB8B1C8);
    }

    private void drawEnchantTooltip(DrawContext context, int mouseX, int mouseY) {
        int mx = mouseX - this.x;
        int my = mouseY - this.y;
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < MaikuraEnchantManagerMod.EnchantManagerScreenHandler.LIST_COUNT; i++) {
            if (isInside(mx, my, 82, 45 + i * 10, 100, 10)) {
                hovered = handler.getMenuStack(MaikuraEnchantManagerMod.EnchantManagerScreenHandler.LIST_START + i);
                break;
            }
        }
        if (hovered.isEmpty()) {
            for (int i = 0; i < MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CURRENT_COUNT; i++) {
                if (isInside(mx, my, 198, 45 + i * 10, 106, 10)) {
                    hovered = handler.getMenuStack(MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CURRENT_START + i);
                    break;
                }
                // 右端の×削除ボタン上では、エンチャント名のツールチップを出さない。
            }
        }
        if (hovered.isEmpty()) return;

        List<Text> lines = new ArrayList<>();
        lines.add(hovered.getName());
        net.minecraft.component.type.LoreComponent loreComponent = hovered.get(net.minecraft.component.DataComponentTypes.LORE);
        List<Text> lore = loreComponent == null ? List.of() : loreComponent.lines();
        boolean shift = isShiftPressed();
        String description = "";
        String id = "";
        String maxLv = "";
        for (Text line : lore) {
            String text = line.getString();
            if (text.startsWith("説明:")) description = text.substring("説明:".length()).trim();
            else if (text.startsWith("ID:")) id = text.substring("ID:".length()).trim();
            else if (text.startsWith("最大Lv:")) maxLv = text.substring("最大Lv:".length()).trim();
        }
        if (!description.isBlank()) {
            lines.add(Text.literal("§7" + description));
        }
        if (shift) {
            if (!id.isBlank()) lines.add(Text.literal("§8ID: §7" + id));
            if (!maxLv.isBlank()) lines.add(Text.literal("§8最大Lv: §7" + maxLv));
        } else {
            lines.add(Text.literal("§8Shiftで詳細表示"));
        }
        context.drawTooltip(this.textRenderer, lines, mouseX, mouseY);
    }

    private boolean isShiftPressed() {
        return isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || isKeyDown(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private boolean isKeyDown(int key) {
        if (this.client == null || this.client.getWindow() == null) return false;
        long handle = this.client.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
    }

    private static String clean(String s) {
        if (s == null || s.isBlank()) return "";
        return s.replace("説明: ", "").replace("ID: ", "ID: ").replace("最大Lv: ", "最大Lv: ");
    }

    private void drawDecorButton(DrawContext context, int x, int y, int width, int height, String label, boolean selected, int mouseX, int mouseY) {
        boolean hover = isInside(mouseX, mouseY, x, y, width, height);
        int bg = selected ? 0xAA4A3A62 : (hover ? 0xAA3D3155 : 0x882A2038);
        context.fill(x, y, x + width, y + height, bg);
        drawBorder(context, x, y, width, height, selected ? GREEN : (hover ? GOLD : LINE_DIM));
        drawSmallText(context, label, x + 3, y + Math.max(2, (height - 8) / 2 + 1), selected ? GREEN : WHITE);
    }

    private void drawTextButton(DrawContext context, int x, int y, int width, int height, String label, int menuSlot, int mouseX, int mouseY) {
        boolean hover = isInside(mouseX, mouseY, x, y, width, height);
        boolean selected = isSelectedButton(menuSlot);
        int bg = selected ? 0xAA4A3A62 : (hover ? 0xAA3D3155 : 0x882A2038);
        context.fill(x, y, x + width, y + height, bg);
        drawBorder(context, x, y, width, height, selected ? GREEN : (hover ? GOLD : LINE_DIM));
        int color = menuSlot == MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CLEAR_SLOT ? RED : (selected ? GOLD : WHITE);
        int textY = y + Math.max(2, (height - 8) / 2 + 1);
        if ("通常".equals(label)) {
            textY -= 1;
        }
        drawSmallText(context, label, x + 3, textY, color);
    }

    private boolean isSelectedButton(int menuSlot) {
        ItemStack stack = handler.getMenuStack(menuSlot);
        String name = stack.isEmpty() ? "" : stack.getName().getString();
        if (menuSlot == MaikuraEnchantManagerMod.EnchantManagerScreenHandler.TAB_SUITABLE_SLOT
                || menuSlot == MaikuraEnchantManagerMod.EnchantManagerScreenHandler.TAB_ALL_SLOT) {
            return name.startsWith("▶");
        }
        if (menuSlot == MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CAT_ALL_SLOT
                || menuSlot == MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CAT_TOOL_SLOT
                || menuSlot == MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CAT_WEAPON_SLOT
                || menuSlot == MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CAT_ARMOR_SLOT
                || menuSlot == MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CAT_RANGED_SLOT
                || menuSlot == MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CAT_CURSE_SLOT
                || menuSlot == MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CAT_MODDED_SLOT
                || menuSlot == MaikuraEnchantManagerMod.EnchantManagerScreenHandler.LEVEL_NORMAL_SLOT
                || menuSlot == MaikuraEnchantManagerMod.EnchantManagerScreenHandler.LEVEL_1_SLOT
                || menuSlot == MaikuraEnchantManagerMod.EnchantManagerScreenHandler.LEVEL_5_SLOT
                || menuSlot == MaikuraEnchantManagerMod.EnchantManagerScreenHandler.LEVEL_10_SLOT
                || menuSlot == MaikuraEnchantManagerMod.EnchantManagerScreenHandler.LEVEL_50_SLOT
                || menuSlot == MaikuraEnchantManagerMod.EnchantManagerScreenHandler.LEVEL_100_SLOT
                || menuSlot == MaikuraEnchantManagerMod.EnchantManagerScreenHandler.LEVEL_255_SLOT) {
            return name.startsWith("▶");
        }
        return false;
    }

    private boolean isSelectedInfo(String listName) {
        // 詳細パネルをツールチップ化したため、固定選択表示は行わない。
        return false;
    }


    @Override
    public boolean keyPressed(KeyInput input) {
        // 検索欄フォーカス中は、Eキーなどのインベントリ閉じる操作をGUI本体へ流さない。
        // Esc/Deleteだけは検索クリア用として先に処理し、それ以外はTextFieldWidgetへ渡す。
        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (isKeyDown(GLFW.GLFW_KEY_ESCAPE)) {
                this.searchBox.setText("");
                this.searchBox.setFocused(false);
                this.searchFocused = false;
                return true;
            }
            if (isKeyDown(GLFW.GLFW_KEY_DELETE)) {
                this.searchBox.setText("");
                return true;
            }
            this.searchBox.keyPressed(input);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int mx = (int) click.x() - this.x;
        int my = (int) click.y() - this.y;

        // r45: 検索欄のクリックはTextFieldWidgetへ渡す。
        searchFocused = isInside(mx, my, 204, 10, 120, 14);
        if (this.searchBox != null) {
            this.searchBox.setFocused(searchFocused);
        }
        if (searchFocused) {
            super.mouseClicked(click, doubled);
            return true;
        }

        int menuSlot = slotAt(mx, my);
        if (menuSlot >= 0 && this.client != null && this.client.interactionManager != null && this.client.player != null) {
            SlotActionType type = click.hasShift() ? SlotActionType.QUICK_MOVE : SlotActionType.PICKUP;
            this.client.interactionManager.clickSlot(this.handler.syncId, menuSlot, click.button(), type, this.client.player);
            return true;
        }
        int playerSlot = playerSlotAt(mx, my);
        if (click.hasShift() && playerSlot >= 0 && this.client != null && this.client.interactionManager != null && this.client.player != null) {
            this.client.interactionManager.clickSlot(this.handler.syncId, playerSlot, click.button(), SlotActionType.QUICK_MOVE, this.client.player);
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int mx = (int) mouseX - this.x;
        int my = (int) mouseY - this.y;
        if (this.client != null && this.client.interactionManager != null && this.client.player != null) {
            if (isInside(mx, my, 78, 28, 110, 132)) {
                int slot = verticalAmount < 0 ? MaikuraEnchantManagerMod.EnchantManagerScreenHandler.PAGE_NEXT_SLOT : MaikuraEnchantManagerMod.EnchantManagerScreenHandler.PAGE_PREV_SLOT;
                this.client.interactionManager.clickSlot(this.handler.syncId, slot, 0, SlotActionType.PICKUP, this.client.player);
                return true;
            }
            if (isInside(mx, my, 194, 28, 130, 132)) {
                int slot = verticalAmount < 0 ? MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CURRENT_NEXT_SLOT : MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CURRENT_PREV_SLOT;
                this.client.interactionManager.clickSlot(this.handler.syncId, slot, 0, SlotActionType.PICKUP, this.client.player);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private int playerSlotAt(int mx, int my) {
        int baseX = 84;
        int baseY = 166;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                if (isInside(mx, my, baseX + col * 18, baseY + row * 18, 18, 18)) {
                    return MaikuraEnchantManagerMod.EnchantManagerScreenHandler.SIZE + row * 9 + col;
                }
            }
        }
        for (int col = 0; col < 9; col++) {
            if (isInside(mx, my, baseX + col * 18, baseY + 58, 18, 18)) {
                return MaikuraEnchantManagerMod.EnchantManagerScreenHandler.SIZE + 27 + col;
            }
        }
        return -1;
    }

    private int slotAt(int mx, int my) {
        if (isInside(mx, my, 12, 72, 28, 14)) return MaikuraEnchantManagerMod.EnchantManagerScreenHandler.TAB_SUITABLE_SLOT;
        if (MaikuraEnchantManagerMod.CONFIG.showAllTab() && isInside(mx, my, 42, 72, 28, 14)) return MaikuraEnchantManagerMod.EnchantManagerScreenHandler.TAB_ALL_SLOT;
        if (MaikuraEnchantManagerMod.CONFIG.showCategoryFilter() && isInside(mx, my, 12, 99, 28, 13)) return MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CAT_ALL_SLOT;
        if (MaikuraEnchantManagerMod.CONFIG.showCategoryFilter() && isInside(mx, my, 42, 99, 28, 13)) return MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CAT_TOOL_SLOT;
        if (MaikuraEnchantManagerMod.CONFIG.showCategoryFilter() && isInside(mx, my, 12, 114, 28, 13)) return MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CAT_WEAPON_SLOT;
        if (MaikuraEnchantManagerMod.CONFIG.showCategoryFilter() && isInside(mx, my, 42, 114, 28, 13)) return MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CAT_ARMOR_SLOT;
        if (MaikuraEnchantManagerMod.CONFIG.showCategoryFilter() && isInside(mx, my, 12, 129, 28, 13)) return MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CAT_RANGED_SLOT;
        if (MaikuraEnchantManagerMod.CONFIG.showCategoryFilter() && isInside(mx, my, 42, 129, 28, 13)) return MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CAT_CURSE_SLOT;
        if (MaikuraEnchantManagerMod.CONFIG.showCategoryFilter() && isInside(mx, my, 12, 144, 58, 13)) return MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CAT_MODDED_SLOT;
        if (isInside(mx, my, 12, 171, 28, 12)) return MaikuraEnchantManagerMod.EnchantManagerScreenHandler.LEVEL_NORMAL_SLOT;
        if (isInside(mx, my, 42, 171, 28, 12)) return MaikuraEnchantManagerMod.EnchantManagerScreenHandler.LEVEL_1_SLOT;
        if (isInside(mx, my, 12, 186, 28, 12)) return MaikuraEnchantManagerMod.EnchantManagerScreenHandler.LEVEL_5_SLOT;
        if (isInside(mx, my, 42, 186, 28, 12)) return MaikuraEnchantManagerMod.EnchantManagerScreenHandler.LEVEL_10_SLOT;
        if (isInside(mx, my, 12, 201, 58, 12)) return MaikuraEnchantManagerMod.EnchantManagerScreenHandler.LEVEL_50_SLOT;
        if (isInside(mx, my, 12, 216, 58, 12)) return MaikuraEnchantManagerMod.EnchantManagerScreenHandler.LEVEL_100_SLOT;
        if (isInside(mx, my, 12, 231, 58, 12)) return MaikuraEnchantManagerMod.EnchantManagerScreenHandler.LEVEL_255_SLOT;
        if (isInside(mx, my, 198, 146, 54, 12)) return MaikuraEnchantManagerMod.EnchantManagerScreenHandler.APPLY_ALL_SLOT;
        if (isInside(mx, my, 262, 146, 56, 12)) return MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CLEAR_SLOT;
        for (int i = 0; i < MaikuraEnchantManagerMod.EnchantManagerScreenHandler.LIST_COUNT; i++) {
            if (isInside(mx, my, 82, 45 + i * 10, 100, 10)) return MaikuraEnchantManagerMod.EnchantManagerScreenHandler.LIST_START + i;
        }
        for (int i = 0; i < MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CURRENT_COUNT; i++) {
            // 付与済みは右端の×だけを削除ボタンにする。
            if (isInside(mx, my, 306, 45 + i * 10, 16, 10)) return MaikuraEnchantManagerMod.EnchantManagerScreenHandler.CURRENT_START + i;
        }
        return -1;
    }

    private static boolean isInside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static String[] wrap(String s, int max, int lines) {
        if (s == null || s.isBlank()) return new String[]{""};
        java.util.List<String> out = new java.util.ArrayList<>();
        String rest = s;
        while (!rest.isEmpty() && out.size() < lines) {
            if (rest.length() <= max) {
                out.add(rest);
                break;
            }
            out.add(rest.substring(0, max));
            rest = rest.substring(max);
        }
        return out.toArray(new String[0]);
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "…";
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
        this.drawEnchantTooltip(context, mouseX, mouseY);
    }
}
