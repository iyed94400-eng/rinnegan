package com.susano;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Le Rinnegan : donne les six voies de Pain. Clic droit = utiliser le pouvoir choisi. */
public class RinneganItem extends Item {
    public RinneganItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity sp) {
            RinneganManager.use(sp);
        }
        return TypedActionResult.success(stack, world.isClient);
    }
}
