package jp.maikurakobo.enchantmanager;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class MaikuraEnchantManagerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HandledScreens.register(MaikuraEnchantManagerMod.ENCHANT_MANAGER_SCREEN_HANDLER, EnchantManagerScreen::new);
    }
}
