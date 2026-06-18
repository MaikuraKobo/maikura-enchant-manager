package jp.maikurakobo.enchantmanager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Locale;
import java.nio.file.Files;
import java.nio.file.Path;

public class MaikuraEnchantManagerMod implements ModInitializer {
    public static final String MOD_ID = "maikura_enchant_manager";
    public static final Identifier TERMINAL_ID = Identifier.of(MOD_ID, "enchant_manager_terminal");

    public static final class Config {
        public boolean allowUnsafeLevels = false;
        public boolean allowAllEnchantments = false;
        public boolean allowCategoryFilter = false;
        /**
         * false: Minecraft標準の競合（例: シルクタッチ x 幸運）を尊重する。
         * true : 開発・検証用に競合を無視して併用を許可する。
         */
        public boolean allowVanillaConflicts = false;

        public boolean showAllTab() {
            return allowAllEnchantments;
        }

        public boolean showCategoryFilter() {
            return allowCategoryFilter;
        }

        public boolean showUnsafeLevels() {
            return allowUnsafeLevels;
        }
    }

    public static final Config CONFIG = new Config();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Path.of("config", "maikura_enchant_manager.json");

    public static void loadConfig() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                saveConfig();
                return;
            }
            Config loaded = GSON.fromJson(Files.readString(CONFIG_PATH), Config.class);
            if (loaded != null) {
                CONFIG.allowUnsafeLevels = loaded.allowUnsafeLevels;
                CONFIG.allowAllEnchantments = loaded.allowAllEnchantments;
                CONFIG.allowCategoryFilter = loaded.allowCategoryFilter;
                CONFIG.allowVanillaConflicts = loaded.allowVanillaConflicts;
            }
        } catch (Exception ignored) {
            saveConfig();
        }
    }

    public static void saveConfig() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(CONFIG));
        } catch (Exception ignored) {
        }
    }


    public static final RegistryKey<Block> ENCHANT_MANAGER_TERMINAL_BLOCK_KEY = RegistryKey.of(
            RegistryKeys.BLOCK,
            TERMINAL_ID
    );
    public static final RegistryKey<Item> ENCHANT_MANAGER_TERMINAL_ITEM_KEY = RegistryKey.of(
            RegistryKeys.ITEM,
            TERMINAL_ID
    );

    public static final Block ENCHANT_MANAGER_TERMINAL = new EnchantManagerTerminalBlock(
            AbstractBlock.Settings.create()
                    .registryKey(ENCHANT_MANAGER_TERMINAL_BLOCK_KEY)
                    .strength(5.5F, 3600000.0F)
                    // 作業台系ブロックとして、素手や任意ツールでも破壊時に自己ドロップするようにする。
                    // requiresTool() を付けると、適正ツール以外で破壊した時にドロップしない。
                    .luminance(state -> 12)
    );
    public static final Item ENCHANT_MANAGER_TERMINAL_ITEM = new BlockItem(
            ENCHANT_MANAGER_TERMINAL,
            new Item.Settings().registryKey(ENCHANT_MANAGER_TERMINAL_ITEM_KEY).fireproof()
    );

    public static final ScreenHandlerType<EnchantManagerScreenHandler> ENCHANT_MANAGER_SCREEN_HANDLER = Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of(MOD_ID, "enchant_manager"),
            new ScreenHandlerType<>(EnchantManagerScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
    );

    @Override
    public void onInitialize() {
        loadConfig();
        Registry.register(Registries.BLOCK, TERMINAL_ID, ENCHANT_MANAGER_TERMINAL);
        Registry.register(Registries.ITEM, TERMINAL_ID, ENCHANT_MANAGER_TERMINAL_ITEM);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(ENCHANT_MANAGER_TERMINAL_ITEM));

        PayloadTypeRegistry.playC2S().register(SearchQueryPayload.ID, SearchQueryPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SearchQueryPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                if (player.currentScreenHandler instanceof EnchantManagerScreenHandler handler
                        && handler.syncId == payload.syncId()) {
                    handler.setSearchQuery(payload.query(), player);
                }
            });
        });
    }

    public record SearchQueryPayload(int syncId, String query) implements CustomPayload {
        public static final Id<SearchQueryPayload> ID = new Id<>(Identifier.of(MOD_ID, "search_query"));
        public static final PacketCodec<RegistryByteBuf, SearchQueryPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, SearchQueryPayload::syncId,
                PacketCodecs.STRING, SearchQueryPayload::query,
                SearchQueryPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private static final class EnchantManagerTerminalBlock extends Block {
        public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;

        EnchantManagerTerminalBlock(Settings settings) {
            super(settings);
            setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        public BlockState getPlacementState(ItemPlacementContext ctx) {
            return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
        }

        @Override
        protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
            if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) {
                loadConfig();
                serverPlayer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                        (syncId, playerInventory, p) -> new EnchantManagerScreenHandler(syncId, playerInventory),
                        Text.literal("エンチャント管理端末")
                ));
            }
            return ActionResult.SUCCESS;
        }
    }

    private enum Tab {
        SUITABLE("適正"),
        ALL("全て");

        final String label;
        Tab(String label) { this.label = label; }
    }

    private enum Category {
        ALL("全カテゴリ"),
        TOOL("ツール"),
        WEAPON("武器"),
        ARMOR("防具"),
        RANGED("飛び道具"),
        SPECIAL("特殊"),
        CURSE("呪い"),
        MODDED("MOD追加");

        final String label;
        Category(String label) { this.label = label; }

        Category next() {
            Category[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        Category previous() {
            Category[] values = values();
            return values[(ordinal() + values.length - 1) % values.length];
        }
    }

    public static final class EnchantManagerScreenHandler extends ScreenHandler {
        public static final int SIZE = 61;
        public static final int INPUT_SLOT = 0;
        public static final int TAB_SUITABLE_SLOT = 1;
        public static final int TAB_ALL_SLOT = 2;
        public static final int LEVEL_NORMAL_SLOT = 3;
        public static final int LEVEL_1_SLOT = 4;
        public static final int LEVEL_5_SLOT = 5;
        public static final int LEVEL_10_SLOT = 6;
        public static final int LEVEL_50_SLOT = 7;
        public static final int LEVEL_100_SLOT = 8;
        public static final int LEVEL_255_SLOT = 44;
        public static final int PAGE_PREV_SLOT = 54;
        public static final int PAGE_NEXT_SLOT = 55;
        public static final int CURRENT_PREV_SLOT = 56;
        public static final int CURRENT_NEXT_SLOT = 57;
        public static final int CLEAR_SLOT = 45;
        public static final int APPLY_ALL_SLOT = 46;
        public static final int CAT_ALL_SLOT = 47;
        public static final int LIST_START = 9;
        public static final int LIST_COUNT = 11;
        public static final int CURRENT_START = 34;
        public static final int CURRENT_COUNT = 10;
        public static final int CAT_TOOL_SLOT = 48;
        public static final int CAT_WEAPON_SLOT = 49;
        public static final int CAT_ARMOR_SLOT = 50;
        public static final int CAT_RANGED_SLOT = 51;
        public static final int CAT_CURSE_SLOT = 52;
        public static final int CAT_MODDED_SLOT = 53;

        private final SimpleInventory menuInventory;
        private Tab tab = Tab.SUITABLE;
        private Category category = Category.ALL;
        private int page = 0;
        private int currentPage = 0;
        private int selectedIndex = 0;
        /** 0 = 通常（各エンチャント本来の最大Lv）、それ以外は固定付与Lv */
        private int selectedLevel = 0;
        private int lastListTotal = 0;
        private int lastCurrentTotal = 0;
        private String searchQuery = "";

        public EnchantManagerScreenHandler(int syncId, PlayerInventory playerInventory) {
            this(syncId, playerInventory, new SimpleInventory(SIZE));
        }

        private EnchantManagerScreenHandler(int syncId, PlayerInventory playerInventory, SimpleInventory inventory) {
            super(ENCHANT_MANAGER_SCREEN_HANDLER, syncId);
            this.menuInventory = inventory;
            addManagerSlots();
            addPlayerSlots(playerInventory);
            loadConfig();
            rebuild(playerInventory.player);
        }

        public ItemStack getMenuStack(int index) {
            if (index < 0 || index >= SIZE) return ItemStack.EMPTY;
            return menuInventory.getStack(index);
        }

        public int getListScrollOffset() {
            return page;
        }

        public int getCurrentScrollOffset() {
            return currentPage;
        }

        public int getListTotalCount() {
            return lastListTotal;
        }

        public int getCurrentTotalCount() {
            return lastCurrentTotal;
        }

        public void setSearchQuery(String query, PlayerEntity player) {
            String next = query == null ? "" : query.trim();
            if (next.length() > 50) next = next.substring(0, 50);
            if (this.searchQuery.equals(next)) return;
            this.searchQuery = next;
            this.page = 0;
            this.selectedIndex = 0;
            rebuild(player);
            sendContentUpdates();
        }

        @Override
        public boolean canUse(PlayerEntity player) {
            return true;
        }

        private void addManagerSlots() {
            // r9: 画面上に表示するスロットは対象アイテムのみ。
            // それ以外の操作用スロットは画面外に置き、HandledScreen側で文字ボタンとして描画・クリック処理する。
            addSlot(new Slot(menuInventory, INPUT_SLOT, 20, 48));
            for (int i = 1; i < SIZE; i++) {
                addSlot(new DisplaySlot(menuInventory, i, -2000, -2000));
            }
        }

        private void addPlayerSlots(PlayerInventory playerInventory) {
            int baseX = 84;
            int baseY = 166;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    addSlot(new Slot(playerInventory, col + row * 9 + 9, baseX + col * 18, baseY + row * 18));
                }
            }
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col, baseX + col * 18, baseY + 58));
            }
        }

        private static final class DisplaySlot extends Slot {
            DisplaySlot(SimpleInventory inventory, int index, int x, int y) { super(inventory, index, x, y); }
            @Override public boolean canInsert(ItemStack stack) { return false; }
            @Override public boolean canTakeItems(PlayerEntity playerEntity) { return false; }
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
            if (slotIndex >= 0 && slotIndex < SIZE) {
                if (slotIndex == INPUT_SLOT) {
                    super.onSlotClick(slotIndex, button, actionType, player);
                    rebuild(player);
                    sendContentUpdates();
                    return;
                }
                if (player instanceof ServerPlayerEntity serverPlayer) {
                    handleButton(serverPlayer, slotIndex, button, actionType);
                    rebuild(player);
                    sendContentUpdates();
                }
                return;
            }
            super.onSlotClick(slotIndex, button, actionType, player);
            rebuild(player);
            sendContentUpdates();
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int slot) {
            if (slot < 0 || slot >= this.slots.size()) return ItemStack.EMPTY;
            Slot clicked = this.slots.get(slot);
            if (!clicked.hasStack()) return ItemStack.EMPTY;

            // 対象スロットをShiftクリックした場合は、安全返却処理へ一本化する。
            if (slot == INPUT_SLOT) {
                ItemStack original = clicked.getStack().copy();
                returnInputToPlayer(player, false);
                rebuild(player);
                sendContentUpdates();
                return original;
            }

            // プレイヤーインベントリ側をShiftクリックした場合は、対象スロットが空の時だけ投入する。
            // 対象スロットに既にアイテムがある時のShift入れ替えは、環境によってフリーズ/消失疑いがあるため無効化。
            // 入れ替えたい場合は通常クリックで手動操作する。
            if (slot >= SIZE) {
                ItemStack inputStack = menuInventory.getStack(INPUT_SLOT);
                if (!inputStack.isEmpty()) {
                    rebuild(player);
                    sendContentUpdates();
                    return ItemStack.EMPTY;
                }

                ItemStack clickedStack = clicked.getStack();
                ItemStack original = clickedStack.copy();
                menuInventory.setStack(INPUT_SLOT, clickedStack.copy());
                clicked.setStack(ItemStack.EMPTY);

                markInputDirty();
                clicked.markDirty();
                rebuild(player);
                sendContentUpdates();
                return original;
            }

            return ItemStack.EMPTY;
        }

        @Override
        public void onClosed(PlayerEntity player) {
            returnInputToPlayer(player, false);
            super.onClosed(player);
        }

        private void returnInputToPlayer(PlayerEntity player, boolean showMessage) {
            ItemStack stack = menuInventory.getStack(INPUT_SLOT);
            if (stack.isEmpty()) return;

            ItemStack returning = stack.copy();
            menuInventory.setStack(INPUT_SLOT, ItemStack.EMPTY);
            markInputDirty();

            if (!player.getInventory().insertStack(returning)) {
                player.dropItem(returning, false);
            }

            if (showMessage && player instanceof ServerPlayerEntity serverPlayer) {
                message(serverPlayer, "投入アイテムを取り出しました");
            }
        }

        private void markInputDirty() {
            menuInventory.markDirty();
            if (!this.slots.isEmpty()) {
                this.slots.get(INPUT_SLOT).markDirty();
            }
        }

        private void handleButton(ServerPlayerEntity player, int slot, int mouseButton, SlotActionType actionType) {
            ItemStack target = menuInventory.getStack(INPUT_SLOT);
            List<RegistryEntry.Reference<Enchantment>> list = visibleEnchantments(player, target, tab, category, searchQuery);
            lastListTotal = list.size();
            int maxPage = Math.max(0, list.size() - LIST_COUNT);
            if (slot == TAB_SUITABLE_SLOT) { tab = Tab.SUITABLE; page = 0; return; }
            if (slot == TAB_ALL_SLOT) {
                if (CONFIG.showAllTab()) { tab = Tab.ALL; page = 0; }
                return;
            }
            if (slot == LEVEL_NORMAL_SLOT) { selectedLevel = 0; return; }
            if (slot == LEVEL_1_SLOT) {
                if (CONFIG.showUnsafeLevels()) selectedLevel = 1;
                return;
            }
            if (slot == LEVEL_5_SLOT) {
                if (CONFIG.showUnsafeLevels()) selectedLevel = 5;
                return;
            }
            if (slot == LEVEL_10_SLOT) {
                if (CONFIG.showUnsafeLevels()) selectedLevel = 10;
                return;
            }
            if (slot == LEVEL_50_SLOT) {
                if (CONFIG.showUnsafeLevels()) selectedLevel = 50;
                return;
            }
            if (slot == LEVEL_100_SLOT) {
                if (CONFIG.showUnsafeLevels()) selectedLevel = 100;
                return;
            }
            if (slot == LEVEL_255_SLOT) {
                if (CONFIG.showUnsafeLevels()) selectedLevel = 255;
                return;
            }
            if (slot == CAT_ALL_SLOT) {
                if (CONFIG.showCategoryFilter()) { category = Category.ALL; page = 0; selectedIndex = 0; }
                return;
            }
            if (slot == CAT_TOOL_SLOT) {
                if (CONFIG.showCategoryFilter()) { category = Category.TOOL; page = 0; selectedIndex = 0; }
                return;
            }
            if (slot == CAT_WEAPON_SLOT) {
                if (CONFIG.showCategoryFilter()) { category = Category.WEAPON; page = 0; selectedIndex = 0; }
                return;
            }
            if (slot == CAT_ARMOR_SLOT) {
                if (CONFIG.showCategoryFilter()) { category = Category.ARMOR; page = 0; selectedIndex = 0; }
                return;
            }
            if (slot == CAT_RANGED_SLOT) {
                if (CONFIG.showCategoryFilter()) { category = Category.RANGED; page = 0; selectedIndex = 0; }
                return;
            }
            if (slot == CAT_CURSE_SLOT) {
                if (CONFIG.showCategoryFilter()) { category = Category.CURSE; page = 0; selectedIndex = 0; }
                return;
            }
            if (slot == CAT_MODDED_SLOT) {
                if (CONFIG.showCategoryFilter()) { category = Category.MODDED; page = 0; selectedIndex = 0; }
                return;
            }
            if (slot == PAGE_PREV_SLOT) { page = Math.max(0, page - 1); return; }
            if (slot == PAGE_NEXT_SLOT) { page = Math.min(maxPage, page + 1); return; }
            if (slot == CURRENT_PREV_SLOT) { currentPage = Math.max(0, currentPage - 1); return; }
            if (slot == CURRENT_NEXT_SLOT) {
                int currentMaxPage = Math.max(0, currentEnchantments(player, target).size() - CURRENT_COUNT);
                currentPage = Math.min(currentMaxPage, currentPage + 1);
                return;
            }
            if (slot == CLEAR_SLOT) {
                if (target.isEmpty()) { message(player, "左上スロットにアイテムを入れてください"); return; }
                clearEnchantments(target);
                markInputDirty();
                message(player, "投入アイテムのエンチャントを削除しました");
                return;
            }
            if (slot == APPLY_ALL_SLOT) {
                if (target.isEmpty()) { message(player, "左上スロットにアイテムを入れてください"); return; }
                int level = selectedLevel;
                if (tab == Tab.ALL) {
                    applyAll(player, target, false, selectedLevel);
                    markInputDirty();
                    message(player, "全エンチャントを" + levelText(level) + "で付与しました");
                } else {
                    applyAll(player, target, true, selectedLevel);
                    markInputDirty();
                    message(player, "適正エンチャントを" + levelText(level) + "で付与しました");
                }
                return;
            }
            if (slot >= LIST_START && slot < LIST_START + LIST_COUNT) {
                int index = page + (slot - LIST_START);
                if (index >= 0 && index < list.size() && !target.isEmpty()) {
                    selectedIndex = index;
                    RegistryEntry.Reference<Enchantment> entry = list.get(index);
                    int now = getLevel(target, entry);
                    int next;
                    if (mouseButton == 1) {
                        next = Math.max(0, now - 1);
                    } else {
                        next = selectedLevel <= 0 ? Math.max(1, entry.value().getMaxLevel()) : selectedLevel;
                    }
                    if (next > 0) {
                        String conflict = conflictName(target, entry);
                        if (conflict != null) {
                            message(player, entry.value().description().getString() + " は付与できません（競合中：" + conflict + "）");
                            return;
                        }
                    }
                    setEnchantmentLevel(target, entry, next);
                    markInputDirty();
                    if (next <= 0) {
                        message(player, entry.value().description().getString() + " を削除しました");
                    } else {
                        message(player, entry.value().description().getString() + " Lv" + next + " を付与しました");
                    }
                }
                return;
            }
            if (slot >= CURRENT_START && slot < CURRENT_START + CURRENT_COUNT) {
                int index = currentPage + (slot - CURRENT_START);
                List<RegistryEntry.Reference<Enchantment>> current = currentEnchantments(player, target);
                if (index >= 0 && index < current.size() && !target.isEmpty()) {
                    RegistryEntry.Reference<Enchantment> entry = current.get(index);
                    setEnchantmentLevel(target, entry, 0);
                    markInputDirty();
                    message(player, entry.value().description().getString() + " を削除しました");
                }
            }
        }

        private void rebuild(PlayerEntity player) {
            ItemStack target = menuInventory.getStack(INPUT_SLOT);
            if (!CONFIG.showAllTab() && tab == Tab.ALL) {
                tab = Tab.SUITABLE;
            }
            if (!CONFIG.showCategoryFilter()) {
                category = Category.ALL;
            }
            if (!CONFIG.showUnsafeLevels()) {
                selectedLevel = 0;
            }
            for (int i = 1; i < SIZE; i++) menuInventory.setStack(i, ItemStack.EMPTY);

            menuInventory.setStack(TAB_SUITABLE_SLOT, button(tab == Tab.SUITABLE ? Items.LIME_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE, tab == Tab.SUITABLE ? "▶ 適正エンチャント" : "適正エンチャント"));
            if (CONFIG.showAllTab()) {
                menuInventory.setStack(TAB_ALL_SLOT, button(tab == Tab.ALL ? Items.PURPLE_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE, tab == Tab.ALL ? "▶ 全エンチャント" : "全エンチャント"));
            }
            menuInventory.setStack(CLEAR_SLOT, button(Items.BARRIER, "全削除"));
            menuInventory.setStack(APPLY_ALL_SLOT, button(tab == Tab.ALL ? Items.NETHER_STAR : Items.ENCHANTED_BOOK, tab == Tab.ALL ? "全付与" : "適正全付与"));
            menuInventory.setStack(LEVEL_NORMAL_SLOT, button(selectedLevel == 0 ? Items.LIME_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE, selectedLevel == 0 ? "▶ 通常" : "通常"));
            if (CONFIG.showUnsafeLevels()) {
                menuInventory.setStack(LEVEL_1_SLOT, button(selectedLevel == 1 ? Items.LIME_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE, selectedLevel == 1 ? "▶ Lv1" : "Lv1"));
                menuInventory.setStack(LEVEL_5_SLOT, button(selectedLevel == 5 ? Items.LIME_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE, selectedLevel == 5 ? "▶ Lv5" : "Lv5"));
                menuInventory.setStack(LEVEL_10_SLOT, button(selectedLevel == 10 ? Items.LIME_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE, selectedLevel == 10 ? "▶ Lv10" : "Lv10"));
                menuInventory.setStack(LEVEL_50_SLOT, button(selectedLevel == 50 ? Items.LIME_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE, selectedLevel == 50 ? "▶ Lv50" : "Lv50"));
                menuInventory.setStack(LEVEL_100_SLOT, button(selectedLevel == 100 ? Items.LIME_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE, selectedLevel == 100 ? "▶ Lv100" : "Lv100"));
                menuInventory.setStack(LEVEL_255_SLOT, button(selectedLevel == 255 ? Items.LIME_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE, selectedLevel == 255 ? "▶ Lv255" : "Lv255"));
            }
            if (CONFIG.showCategoryFilter()) {
                menuInventory.setStack(CAT_ALL_SLOT, button(category == Category.ALL ? Items.LIME_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE, category == Category.ALL ? "▶ 全" : "全"));
                menuInventory.setStack(CAT_TOOL_SLOT, button(category == Category.TOOL ? Items.LIME_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE, category == Category.TOOL ? "▶ ツール" : "ツール"));
                menuInventory.setStack(CAT_WEAPON_SLOT, button(category == Category.WEAPON ? Items.LIME_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE, category == Category.WEAPON ? "▶ 武器" : "武器"));
                menuInventory.setStack(CAT_ARMOR_SLOT, button(category == Category.ARMOR ? Items.LIME_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE, category == Category.ARMOR ? "▶ 防具" : "防具"));
                menuInventory.setStack(CAT_RANGED_SLOT, button(category == Category.RANGED ? Items.LIME_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE, category == Category.RANGED ? "▶ 弓" : "弓"));
                menuInventory.setStack(CAT_CURSE_SLOT, button(category == Category.CURSE ? Items.LIME_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE, category == Category.CURSE ? "▶ 呪い" : "呪い"));
                menuInventory.setStack(CAT_MODDED_SLOT, button(category == Category.MODDED ? Items.LIME_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE, category == Category.MODDED ? "▶ MOD" : "MOD"));
            }

            List<RegistryEntry.Reference<Enchantment>> list = visibleEnchantments(player, target, tab, category, searchQuery);
            lastListTotal = list.size();
            int maxPage = Math.max(0, list.size() - LIST_COUNT);
            if (page > maxPage) page = maxPage;
            if (selectedIndex >= list.size()) selectedIndex = Math.max(0, list.size() - 1);

            int start = page;
            for (int i = 0; i < LIST_COUNT && start + i < list.size(); i++) {
                RegistryEntry.Reference<Enchantment> entry = list.get(start + i);
                int lv = getLevel(target, entry);
                String suffix = lv > 0 ? " Lv" + lv : "";
                String conflict = conflictName(target, entry);
                if (lv <= 0 && conflict != null) {
                    menuInventory.setStack(LIST_START + i, enchantButton(entry, "§7" + entry.value().description().getString() + "（競合）", conflict));
                } else {
                    menuInventory.setStack(LIST_START + i, enchantButton(entry, entry.value().description().getString() + suffix));
                }
            }

            List<RegistryEntry.Reference<Enchantment>> current = currentEnchantments(player, target);
            lastCurrentTotal = current.size();
            int currentMaxPage = Math.max(0, current.size() - CURRENT_COUNT);
            if (currentPage > currentMaxPage) currentPage = currentMaxPage;
            if (!target.isEmpty()) {
                int currentStart = currentPage;
                for (int i = 0; i < CURRENT_COUNT && currentStart + i < current.size(); i++) {
                    RegistryEntry.Reference<Enchantment> entry = current.get(currentStart + i);
                    int lv = getLevel(target, entry);
                    menuInventory.setStack(CURRENT_START + i, enchantButton(entry, entry.value().description().getString() + " Lv" + lv));
                }
            }

            // 何も表示していない操作枠は黒ガラスで埋めて、投入できるのが左上1スロットだけだと分かるようにする。
            for (int i = 1; i < SIZE; i++) {
                if (menuInventory.getStack(i).isEmpty()) {
                    menuInventory.setStack(i, button(Items.BLACK_STAINED_GLASS_PANE, " "));
                }
            }
        }

        private void takeOut(ServerPlayerEntity player) {
            returnInputToPlayer(player, true);
        }
    }

    private static List<RegistryEntry.Reference<Enchantment>> currentEnchantments(PlayerEntity player, ItemStack target) {
        if (!(player instanceof ServerPlayerEntity sp) || target.isEmpty()) return List.of();
        ItemEnchantmentsComponent component = enchantmentComponent(target);
        if (component == null || component.isEmpty()) return List.of();
        return sp.getCommandSource().getRegistryManager()
                .getOrThrow(RegistryKeys.ENCHANTMENT)
                .streamEntries()
                .filter(entry -> component.getLevel(entry) > 0)
                .sorted(Comparator.comparing(e -> e.value().description().getString()))
                .toList();
    }

    private static List<RegistryEntry.Reference<Enchantment>> visibleEnchantments(PlayerEntity player, ItemStack target, Tab tab, Category category, String query) {
        if (!(player instanceof ServerPlayerEntity sp)) return List.of();
        List<RegistryEntry.Reference<Enchantment>> all = sp.getCommandSource().getRegistryManager()
                .getOrThrow(RegistryKeys.ENCHANTMENT)
                .streamEntries()
                .filter(entry -> category == Category.ALL || matchesCategory(entry, category))
                .filter(entry -> matchesSearch(entry, query))
                .sorted(Comparator.comparing(e -> e.value().description().getString()))
                .toList();
        if (tab == Tab.ALL) return all;
        if (target.isEmpty()) return List.of();
        // 入力アイテムがある場合、適正タブではMinecraft標準の適正判定を使う。
        // 本やエンチャント本は全エンチャントを対象にする。
        return all.stream()
                .filter(entry -> isSuitable(target, entry))
                // 適正タブでは呪いを出さず、呪いカテゴリへ集約する。
                .filter(entry -> !matchesCategory(entry, Category.CURSE))
                .toList();
    }

    private static boolean isSuitable(ItemStack stack, RegistryEntry.Reference<Enchantment> entry) {
        if (stack.isEmpty()) return true;
        if (stack.isOf(Items.BOOK) || stack.isOf(Items.ENCHANTED_BOOK)) return true;
        return entry.value().isAcceptableItem(stack);
    }

    private static void applyAll(ServerPlayerEntity player, ItemStack stack, boolean suitableOnly, int selectedLevel) {
        if (stack.isEmpty()) return;
        List<RegistryEntry.Reference<Enchantment>> list = player.getCommandSource().getRegistryManager()
                .getOrThrow(RegistryKeys.ENCHANTMENT)
                .streamEntries()
                .sorted(Comparator.comparing(e -> e.value().description().getString()))
                .filter(entry -> !suitableOnly || (isSuitable(stack, entry) && !matchesCategory(entry, Category.CURSE)))
                .toList();
        for (RegistryEntry.Reference<Enchantment> entry : list) {
            if (conflictName(stack, entry) != null) {
                continue;
            }
            int level = selectedLevel <= 0 ? Math.max(1, entry.value().getMaxLevel()) : selectedLevel;
            setEnchantmentLevel(stack, entry, level);
        }
    }

    private static String conflictName(ItemStack stack, RegistryEntry<Enchantment> candidate) {
        if (CONFIG.allowVanillaConflicts || stack.isEmpty()) {
            return null;
        }
        ItemEnchantmentsComponent component = enchantmentComponent(stack);
        if (component == null || component.isEmpty()) {
            return null;
        }
        for (RegistryEntry<Enchantment> existing : component.getEnchantments()) {
            if (component.getLevel(existing) <= 0 || existing.equals(candidate)) {
                continue;
            }
            if (!Enchantment.canBeCombined(existing, candidate)) {
                return existing.value().description().getString();
            }
        }
        return null;
    }

    private static String levelText(int level) {
        return level <= 0 ? "通常最大Lv" : "Lv" + level;
    }


    private static boolean matchesCategory(RegistryEntry.Reference<Enchantment> entry, Category category) {
        String id = idOf(entry);
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return switch (category) {
            case ALL -> true;
            case MODDED -> !id.startsWith("minecraft:");
            case CURSE -> path.contains("curse") || path.equals("binding_curse") || path.equals("vanishing_curse");
            case TOOL -> containsAny(path, "efficiency", "fortune", "silk_touch", "mending", "unbreaking", "luck_of_the_sea", "lure");
            case WEAPON -> containsAny(path, "sharpness", "smite", "bane_of_arthropods", "knockback", "fire_aspect", "looting", "sweeping", "density", "breach", "wind_burst", "impaling", "loyalty", "riptide", "channeling");
            case ARMOR -> containsAny(path, "protection", "feather_falling", "respiration", "aqua_affinity", "thorns", "depth_strider", "frost_walker", "soul_speed", "swift_sneak");
            case RANGED -> containsAny(path, "power", "punch", "flame", "infinity", "multishot", "quick_charge", "piercing");
            case SPECIAL -> !(matchesCategory(entry, Category.TOOL) || matchesCategory(entry, Category.WEAPON) || matchesCategory(entry, Category.ARMOR) || matchesCategory(entry, Category.RANGED) || matchesCategory(entry, Category.CURSE));
        };
    }

    private static boolean containsAny(String text, String... keys) {
        for (String key : keys) {
            if (text.contains(key)) return true;
        }
        return false;
    }

    private static boolean matchesSearch(RegistryEntry.Reference<Enchantment> entry, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String q = query.toLowerCase(java.util.Locale.ROOT).trim();
        if (q.isEmpty()) {
            return true;
        }

        String id = idOf(entry).toLowerCase(java.util.Locale.ROOT);
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        String name = entry.value().description().getString().toLowerCase(java.util.Locale.ROOT);
        String desc = descriptionOf(entry).toLowerCase(java.util.Locale.ROOT);

        return id.contains(q) || path.contains(q) || name.contains(q) || desc.contains(q);
    }

    private static String idOf(RegistryEntry.Reference<Enchantment> entry) {
        return entry.registryKey().getValue().toString();
    }

    private static String descriptionOf(RegistryEntry.Reference<Enchantment> entry) {
        String id = idOf(entry);
        return switch (id) {
            case "minecraft:protection" -> "多くのダメージを軽減します";
            case "minecraft:fire_protection" -> "炎や溶岩などの火炎ダメージを軽減します";
            case "minecraft:feather_falling" -> "落下ダメージを軽減します";
            case "minecraft:blast_protection" -> "爆発ダメージを軽減します";
            case "minecraft:projectile_protection" -> "飛び道具のダメージを軽減します";
            case "minecraft:respiration" -> "水中で息が長く続きます";
            case "minecraft:aqua_affinity" -> "水中での採掘速度低下を抑えます";
            case "minecraft:thorns" -> "攻撃してきた相手へ反撃ダメージを与えることがあります";
            case "minecraft:depth_strider" -> "水中での移動速度を上げます";
            case "minecraft:frost_walker" -> "歩いた水面を氷にします";
            case "minecraft:soul_speed" -> "ソウルサンドやソウルソイル上で速く移動できます";
            case "minecraft:swift_sneak" -> "スニーク移動速度を上げます";
            case "minecraft:sharpness" -> "近接攻撃のダメージを上げます";
            case "minecraft:smite" -> "アンデッド系へのダメージを上げます";
            case "minecraft:bane_of_arthropods" -> "虫系へのダメージを上げます";
            case "minecraft:knockback" -> "攻撃時のノックバックを強くします";
            case "minecraft:fire_aspect" -> "攻撃した相手を燃やします";
            case "minecraft:looting" -> "モブのドロップ数やレアドロップ率を上げます";
            case "minecraft:sweeping_edge" -> "範囲攻撃のダメージを上げます";
            case "minecraft:efficiency" -> "採掘速度を上げます";
            case "minecraft:silk_touch" -> "ブロックをそのまま回収できます";
            case "minecraft:unbreaking" -> "耐久値の消費を抑えます";
            case "minecraft:fortune" -> "一部ブロックのドロップ数を増やします";
            case "minecraft:power" -> "弓のダメージを上げます";
            case "minecraft:punch" -> "弓のノックバックを強くします";
            case "minecraft:flame" -> "矢に火を付けます";
            case "minecraft:infinity" -> "矢を消費せずに弓を撃てます";
            case "minecraft:luck_of_the_sea" -> "釣りで良いアイテムが出やすくなります";
            case "minecraft:lure" -> "魚がかかるまでの時間を短くします";
            case "minecraft:loyalty" -> "投げたトライデントが戻ってきます";
            case "minecraft:impaling" -> "水生系などへのダメージを上げます";
            case "minecraft:riptide" -> "水中や雨天でトライデントと一緒に突進します";
            case "minecraft:channeling" -> "雷雨時に雷を落とします";
            case "minecraft:multishot" -> "クロスボウから複数の矢を発射します";
            case "minecraft:quick_charge" -> "クロスボウの装填を速くします";
            case "minecraft:piercing" -> "矢が複数の対象を貫通します";
            case "minecraft:mending" -> "経験値で耐久値を修復します";
            case "minecraft:binding_curse" -> "装備を外せなくなる呪いです";
            case "minecraft:vanishing_curse" -> "死亡時にアイテムが消える呪いです";
            default -> "説明文なし";
        };
    }

    private static int getLevel(ItemStack stack, RegistryEntry<Enchantment> entry) {
        if (stack.isEmpty()) return 0;
        ItemEnchantmentsComponent component = enchantmentComponent(stack);
        return component == null ? 0 : component.getLevel(entry);
    }

    private static void setEnchantmentLevel(ItemStack stack, RegistryEntry<Enchantment> entry, int level) {
        if (stack.isEmpty()) return;
        ItemStack target = stack;
        if (stack.isOf(Items.BOOK) && level > 0) {
            target.setCount(stack.getCount());
            // GenericContainer内のItemStack参照を差し替えられない場面があるため、dev1ではBOOKへ直接STORED_ENCHANTMENTSを付ける。
            // 実プレイでは次版でENCHANTED_BOOK変換を専用処理にする予定。
        }
        ItemEnchantmentsComponent current = enchantmentComponent(target);
        ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(current == null ? ItemEnchantmentsComponent.DEFAULT : current);
        builder.set(entry, Math.max(0, level));
        setEnchantComponent(target, builder.build());
    }

    private static ItemEnchantmentsComponent enchantmentComponent(ItemStack stack) {
        if (stack.isOf(Items.BOOK) || stack.isOf(Items.ENCHANTED_BOOK)) {
            return stack.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        }
        return stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
    }

    private static void setEnchantComponent(ItemStack stack, ItemEnchantmentsComponent component) {
        if (stack.isOf(Items.BOOK) || stack.isOf(Items.ENCHANTED_BOOK)) {
            stack.set(DataComponentTypes.STORED_ENCHANTMENTS, component);
        } else {
            stack.set(DataComponentTypes.ENCHANTMENTS, component);
        }
    }

    private static void clearEnchantments(ItemStack stack) {
        if (stack.isEmpty()) return;
        setEnchantComponent(stack, ItemEnchantmentsComponent.DEFAULT);
    }


    private static ItemStack enchantButton(RegistryEntry.Reference<Enchantment> entry, String name) {
        return enchantButton(entry, name, null);
    }

    private static ItemStack enchantButton(RegistryEntry.Reference<Enchantment> entry, String name, String conflict) {
        ItemStack stack = button(conflict == null ? Items.ENCHANTED_BOOK : Items.GRAY_DYE, name);
        List<Text> lore = new ArrayList<>();
        lore.add(Text.literal("ID: " + idOf(entry)));
        lore.add(Text.literal("説明: " + descriptionOf(entry)));
        lore.add(Text.literal("最大Lv: " + entry.value().getMaxLevel()));
        if (conflict != null) {
            lore.add(Text.literal("競合中：" + conflict));
            lore.add(Text.literal("設定で『バニラ競合無視』をONにすると併用できます"));
        }
        stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
        return stack;
    }

    private static ItemStack button(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        return stack;
    }

    private static void message(ServerPlayerEntity player, String message) {
        player.sendMessage(Text.literal(message), false);
    }
}
