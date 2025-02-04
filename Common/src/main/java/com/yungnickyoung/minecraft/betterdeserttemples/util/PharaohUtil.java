package com.yungnickyoung.minecraft.betterdeserttemples.util;

import com.yungnickyoung.minecraft.betterdeserttemples.BetterDesertTemplesCommon;
import com.yungnickyoung.minecraft.betterdeserttemples.entity.IPharaohData;
import com.yungnickyoung.minecraft.betterdeserttemples.module.TagModule;
import com.yungnickyoung.minecraft.betterdeserttemples.world.state.ITempleStateCacheProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class PharaohUtil {
    private static final String PHARAOH_HEAD_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTM1MGMwNDk5YTY4YmNkOWM3NWIyNWMxOTIzMTQzOWIxMDhkMDI3NTlmNDM1ZTMzZTRhZWU5ZWQxZGQyNDFhMiJ9fX0=";

    public static boolean isPharaoh(Object object) {
        if (!(object instanceof Husk husk)) {
            return false;
        }

        for (ItemStack armorItem : husk.getArmorSlots()) {
            if (armorItem.is(Items.PLAYER_HEAD)) {
                ResolvableProfile profile = armorItem.get(DataComponents.PROFILE);
                if (profile == null) continue;

                return profile.properties().values().stream()
                        .filter(property -> property.name().equals("textures"))
                        .anyMatch(property -> property.value().equals(PHARAOH_HEAD_TEXTURE));
            }
        }

        return false;
    }

    public static boolean isPharaoh(CompoundTag mobNbt, RegistryAccess registryAccess) {
        if (!mobNbt.getString("id").equals("minecraft:husk")) return false;

        ListTag armorItems = mobNbt.getList("ArmorItems", Tag.TAG_COMPOUND);
        if (armorItems.size() != 4) return false;

        CompoundTag helmetTag = armorItems.getCompound(3);
        ItemStack helmetItemStack = ItemStack.parseOptional(registryAccess, helmetTag);
        if (!helmetItemStack.is(Items.PLAYER_HEAD)) return false;

        ResolvableProfile profile = helmetItemStack.get(DataComponents.PROFILE);
        return profile != null && profile.properties().values().stream()
                .filter(property -> property.name().equals("textures"))
                .anyMatch(property -> property.value().equals(PHARAOH_HEAD_TEXTURE));
    }

    public static void attachSpawnPos(CompoundTag mobNbt, Vec3 pos) {
        ListTag spawnPos = new ListTag();
        spawnPos.add(DoubleTag.valueOf(pos.x()));
        spawnPos.add(DoubleTag.valueOf(pos.y()));
        spawnPos.add(DoubleTag.valueOf(pos.z()));
        mobNbt.put("bdtOriginalSpawnPos", spawnPos);
    }

    public static void onKillOrDiscardPharaoh(Entity pharaoh, ServerLevel serverLevel, DamageSource damageSource) {
        // We determine which temple to clear based on the pharaoh's original spawn position.
        Vec3 originalSpawnPos = ((IPharaohData) pharaoh).getOriginalSpawnPos();

        if (originalSpawnPos == null) {
            // No original spawn position data -> clear the temple the pharaoh is inside of
            BetterDesertTemplesCommon.LOGGER.error("Pharaoh entity is missing original spawn position data. Attempting to clear the temple it's inside of instead...");
            tryClearTempleAtPosition(pharaoh, serverLevel, pharaoh.blockPosition(), damageSource);
        } else {
            // Clear the temple at the pharaoh's original spawn position
            BlockPos pharaohSpawnPos = new BlockPos((int) originalSpawnPos.x, (int) originalSpawnPos.y, (int) originalSpawnPos.z);
            tryClearTempleAtPosition(pharaoh, serverLevel, pharaohSpawnPos, damageSource);
        }
    }

    private static void tryClearTempleAtPosition(Entity pharaoh, ServerLevel serverLevel, BlockPos pos, DamageSource damageSource) {
        StructureStart structureStart = serverLevel.structureManager().getStructureWithPieceAt(pos, TagModule.APPLIES_MINING_FATIGUE);

        if (structureStart.isValid()) {
            BlockPos structureStartPos = structureStart.getChunkPos().getWorldPosition();

            // Clear temple state
            ((ITempleStateCacheProvider) serverLevel).getTempleStateCache().setTempleCleared(structureStartPos, true);

            // Collect list of all players in the temple
            List<ServerPlayer> playersInTemple = serverLevel.players().stream()
                    .filter(player -> serverLevel.isLoaded(player.blockPosition())
                            && serverLevel.structureManager().getStructureWithPieceAt(player.blockPosition(), TagModule.APPLIES_MINING_FATIGUE).isValid())
                    .toList();

            // Clear mining fatigue for all players in the temple
            playersInTemple.forEach(player -> {
                player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.BEACON_DEACTIVATE), SoundSource.HOSTILE, pharaoh.getX(), pharaoh.getY(), pharaoh.getZ(), 1.0F, 1.0F, serverLevel.getSeed()));
                player.removeEffect(MobEffects.DIG_SLOWDOWN);
            });

            // Also clear mining fatigue for the player who killed the pharaoh, just in case they aren't in the temple
            if (damageSource != null && damageSource.getEntity() instanceof ServerPlayer killer && !playersInTemple.contains(killer)) {
                killer.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.BEACON_DEACTIVATE), SoundSource.HOSTILE, pharaoh.getX(), pharaoh.getY(), pharaoh.getZ(), 1.0F, 1.0F, serverLevel.getSeed()));
                killer.removeEffect(MobEffects.DIG_SLOWDOWN);
            }

            BetterDesertTemplesCommon.LOGGER.info("Cleared Better Desert Temple at x={}, z={}", structureStartPos.getX(), structureStartPos.getZ());
        } else {
            BetterDesertTemplesCommon.LOGGER.error("Position provided is not inside a Better Desert Temple. Unable to clear temple.");
        }
    }
}
