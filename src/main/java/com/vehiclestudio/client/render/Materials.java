package com.vehiclestudio.client.render;

import com.vehiclestudio.data.VehicleDefinition;
import com.vehiclestudio.data.MaterialIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Texturas disponibles para las piezas. Todas salen del atlas de bloques. */
public final class Materials {

    public record Preset(String id, String key) {
    }

    public static final List<Preset> PRESETS = List.of(
            new Preset(MaterialIds.SOLID, "solid"),
            new Preset(MaterialIds.SMOOTH, "smooth"),
            new Preset(MaterialIds.METAL, "metal"),
            new Preset(MaterialIds.CARBON, "carbon"),
            new Preset(MaterialIds.PLATES, "plates"),
            new Preset(MaterialIds.TREAD_PLATE, "tread_plate"),
            new Preset(MaterialIds.RIVETS, "rivets"),
            new Preset(MaterialIds.GRILLE, "grille"),
            new Preset(MaterialIds.STRIPES, "stripes"),
            new Preset(MaterialIds.CHECKER, "checker"),
            new Preset(MaterialIds.LEATHER, "leather"),
            new Preset(MaterialIds.LAMP, "lamp"),
            new Preset(MaterialIds.CLEAR, "clear"),
            new Preset("minecraft:block/glass", "glass"),
            new Preset("minecraft:block/iron_bars", "iron_bars"),
            new Preset("minecraft:block/white_concrete", "concrete"),
            new Preset("minecraft:block/white_concrete_powder", "concrete_powder"),
            new Preset("minecraft:block/white_terracotta", "terracotta"),
            new Preset("minecraft:block/white_wool", "wool"),
            new Preset("minecraft:block/iron_block", "iron"),
            new Preset("minecraft:block/gold_block", "gold"),
            new Preset("minecraft:block/diamond_block", "diamond"),
            new Preset("minecraft:block/emerald_block", "emerald"),
            new Preset("minecraft:block/netherite_block", "netherite"),
            new Preset("minecraft:block/copper_block", "copper"),
            new Preset("minecraft:block/cut_copper", "cut_copper"),
            new Preset("minecraft:block/oxidized_copper", "oxidized_copper"),
            new Preset("minecraft:block/quartz_block_side", "quartz"),
            new Preset("minecraft:block/smooth_stone", "smooth_stone"),
            new Preset("minecraft:block/stone", "stone"),
            new Preset("minecraft:block/polished_blackstone", "blackstone"),
            new Preset("minecraft:block/obsidian", "obsidian"),
            new Preset("minecraft:block/oak_planks", "planks"),
            new Preset("minecraft:block/spruce_planks", "spruce_planks"),
            new Preset("minecraft:block/dark_oak_planks", "dark_planks"),
            new Preset("minecraft:block/oak_log", "bark"),
            new Preset("minecraft:block/bamboo_block", "bamboo"),
            new Preset("minecraft:block/hay_block_side", "hay"),
            new Preset("minecraft:block/bricks", "bricks"),
            new Preset("minecraft:block/redstone_lamp_on", "redstone_lamp"),
            new Preset("minecraft:block/glowstone", "glowstone"),
            new Preset("minecraft:block/sea_lantern", "sea_lantern"),
            new Preset("minecraft:block/prismarine", "prismarine"),
            new Preset("minecraft:block/amethyst_block", "amethyst"),
            new Preset("minecraft:block/slime_block", "slime"),
            new Preset("minecraft:block/honey_block_side", "honey"),
            new Preset("minecraft:block/ice", "ice"),
            new Preset("minecraft:block/snow", "snow"),
            new Preset("minecraft:block/sand", "sand"),
            new Preset("minecraft:block/pumpkin_side", "pumpkin"),
            new Preset("minecraft:block/melon_side", "melon"),
            new Preset("minecraft:block/water_still", "water"),
            new Preset("minecraft:block/lava_still", "lava"),
            new Preset("minecraft:block/nether_portal", "portal")
    );

    private static final ResourceLocation FALLBACK = ResourceLocation.parse(MaterialIds.SOLID);
    private static final Map<String, ResourceLocation> CACHE = new ConcurrentHashMap<>();

    private Materials() {
    }

    public static ResourceLocation location(String material) {
        String key = material == null ? "" : material;
        ResourceLocation cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        if (CACHE.size() > 2048) {
            CACHE.clear();
        }
        ResourceLocation rl = ResourceLocation.tryParse(VehicleDefinition.cleanMaterial(key));
        if (rl == null) {
            rl = FALLBACK;
        }
        CACHE.put(key, rl);
        return rl;
    }

    public static Function<ResourceLocation, TextureAtlasSprite> atlas() {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
    }

    public static TextureAtlasSprite sprite(Function<ResourceLocation, TextureAtlasSprite> atlas, String material) {
        return atlas.apply(location(material));
    }

    public static TextureAtlasSprite sprite(String material) {
        return sprite(atlas(), material);
    }

    /** true si la textura existe en el atlas (si no, Minecraft usaria la textura de "falta textura"). */
    public static boolean exists(String material) {
        return !sprite(material).contents().name().equals(MissingTextureAtlasSprite.getLocation());
    }

    public static Component displayName(String material) {
        String normalized = VehicleDefinition.cleanMaterial(material);
        for (Preset p : PRESETS) {
            if (p.id().equals(normalized)) {
                return Component.translatable("material.vehiclestudio." + p.key());
            }
        }
        return Component.literal(normalized);
    }
}
