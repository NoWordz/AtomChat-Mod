package com.atom.chat.card;

import com.atom.chat.api.card.MinimapPreviewCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.Locale;

/**
 * Pure server- &amp; client-safe Minecraft terrain minimap sampler (zero {@code net.minecraft.client.*}
 * or Skija imports so dedicated servers can call it safely without {@code NoClassDefFoundError}).
 *
 * <p>Samples a {@code 64×64} grid centered on {@code (centerX, centerY, centerZ)} exclusively
 * from already-loaded chunks ({@code getChunk(cx, cz, false)}), never force-loading or generating chunks.
 */
public final class MinimapTerrainSampler {
    private static final int W = MinimapPreviewCodec.DEFAULT_WIDTH;
    private static final int H = MinimapPreviewCodec.DEFAULT_HEIGHT;

    private MinimapTerrainSampler() {
    }

    /**
     * Samples a {@code 64×64} terrain minimap centered on {@code (centerX, centerY, centerZ)}
     * from already-loaded chunks in {@code level}. Never forces chunk loading or generation.
     *
     * @return compact Base64 minimap payload, or {@code null} if fewer than 25% of pixels are loaded.
     */
    public static String sampleFromLevel(Level level, int centerX, int centerY, int centerZ, String dimension) {
        if (level == null) return null;
        try {
            boolean nether = dimension != null && dimension.toLowerCase(Locale.ROOT).contains("nether");
            int minY = level.getMinBuildHeight();
            int maxY = level.getMaxBuildHeight() - 1;
            int startX = centerX - (W / 2);
            int startZ = centerZ - (H / 2);

            int[] baseRgb = new int[W * H];
            int[] heights = new int[W * H];
            boolean[] loaded = new boolean[W * H];
            boolean[] water = new boolean[W * H];

            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int loadedCount = 0;

            LevelChunk cachedChunk = null;
            int cachedCx = Integer.MIN_VALUE;
            int cachedCz = Integer.MIN_VALUE;

            for (int py = 0; py < H; py++) {
                int wz = startZ + py;
                int cz = wz >> 4;
                int lz = wz & 15;
                for (int px = 0; px < W; px++) {
                    int wx = startX + px;
                    int cx = wx >> 4;
                    int idx = py * W + px;

                    if (cx != cachedCx || cz != cachedCz) {
                        cachedCx = cx;
                        cachedCz = cz;
                        cachedChunk = level.getChunkSource().getChunk(cx, cz, false);
                    }
                    if (cachedChunk == null) {
                        continue;
                    }

                    int lx = wx & 15;
                    int topY;
                    if (nether) {
                        topY = findNetherSurface(cachedChunk, pos, wx, wz, centerY, minY, Math.min(maxY, 122));
                    } else {
                        topY = Math.min(maxY, cachedChunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz));
                    }

                    int foundY = minY;
                    int rgb = -1;
                    boolean isWater = false;

                    for (int y = topY; y >= minY; y--) {
                        pos.set(wx, y, wz);
                        BlockState state = cachedChunk.getBlockState(pos);
                        if (state.isAir()) continue;
                        MapColor mc = state.getMapColor(level, pos);
                        if (mc == null || mc == MapColor.NONE) continue;

                        if (mc == MapColor.WATER || !state.getFluidState().isEmpty()) {
                            int col = mc.col;
                            if (mc == MapColor.WATER) {
                                int depth = measureWaterDepth(cachedChunk, pos, wx, y, wz, minY);
                                col = shadeWaterByDepth(depth);
                                isWater = true;
                            }
                            rgb = col;
                            foundY = y;
                            break;
                        }
                        rgb = mc.col;
                        foundY = y;
                        break;
                    }

                    if (rgb < 0) {
                        rgb = nether ? 0x2B1114 : 0x1E293B;
                    }
                    baseRgb[idx] = rgb;
                    heights[idx] = foundY;
                    loaded[idx] = true;
                    water[idx] = isWater;
                    loadedCount++;
                }
            }

            if (loadedCount < (W * H) / 4) {
                return null;
            }

            int[] argb = new int[W * H];
            for (int py = 0; py < H; py++) {
                for (int px = 0; px < W; px++) {
                    int idx = py * W + px;
                    if (!loaded[idx]) {
                        boolean checker = (((px >> 2) + (py >> 2)) & 1) == 0;
                        argb[idx] = checker ? 0xFF1F2937 : 0xFF1A2332;
                        continue;
                    }
                    int rgb = baseRgb[idx];
                    float shade = 1.0F;
                    if (!water[idx]) {
                        int y = heights[idx];
                        int slope = 0;
                        if (py > 0 && loaded[idx - W]) {
                            slope += clampInt(y - heights[idx - W], -3, 3);
                        }
                        if (px > 0 && loaded[idx - 1]) {
                            slope += clampInt(y - heights[idx - 1], -2, 2);
                        }
                        if (slope > 0) {
                            shade = 1.06F + Math.min(slope, 4) * 0.045F;
                        } else if (slope < 0) {
                            shade = 0.94F - Math.min(-slope, 4) * 0.048F;
                        }
                        if (((px + py) & 1) == 0) {
                            shade *= 1.015F;
                        } else {
                            shade *= 0.985F;
                        }
                    } else if (((px + py) & 1) == 0) {
                        shade = 1.03F;
                    }
                    argb[idx] = applyShade(rgb, shade);
                }
            }
            return MinimapPreviewCodec.encode(W, H, argb);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int findNetherSurface(LevelChunk chunk, BlockPos.MutableBlockPos pos,
                                         int wx, int wz, int hintY, int minY, int roofY) {
        int start = Math.max(minY + 4, Math.min(roofY, hintY + 12));
        boolean seenAir = false;
        for (int y = start; y >= minY; y--) {
            pos.set(wx, y, wz);
            BlockState s = chunk.getBlockState(pos);
            if (s.isAir()) {
                seenAir = true;
            } else if (seenAir) {
                return y;
            }
        }
        return start;
    }

    private static int measureWaterDepth(LevelChunk chunk, BlockPos.MutableBlockPos pos,
                                         int wx, int surfaceY, int wz, int minY) {
        int depth = 1;
        int limit = Math.max(minY, surfaceY - 12);
        for (int y = surfaceY - 1; y >= limit; y--) {
            pos.set(wx, y, wz);
            BlockState s = chunk.getBlockState(pos);
            if (!s.getFluidState().isEmpty()) {
                depth++;
            } else {
                break;
            }
        }
        return depth;
    }

    private static int shadeWaterByDepth(int depth) {
        if (depth <= 1) return 0x4EAEEB;
        if (depth <= 2) return 0x3F9BE0;
        if (depth <= 4) return 0x3182CE;
        if (depth <= 7) return 0x266AB8;
        return 0x1D5499;
    }

    private static int applyShade(int rgb, float factor) {
        int r = clampInt(Math.round(((rgb >>> 16) & 0xFF) * factor), 0, 255);
        int g = clampInt(Math.round(((rgb >>> 8) & 0xFF) * factor), 0, 255);
        int b = clampInt(Math.round((rgb & 0xFF) * factor), 0, 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
