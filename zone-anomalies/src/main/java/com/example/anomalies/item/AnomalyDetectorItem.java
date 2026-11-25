package com.example.anomalies.item;

import com.example.anomalies.AnomalyConfig;
import com.example.anomalies.block.AnomalyMarker;
import com.example.anomalies.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Детектор аномалий. Работает, просто лёжа в инвентаре: пищит тем чаще и выше, чем ближе аномалия.
 * В руке дополнительно показывает название и расстояние над хотбаром.
 * Всё считается на клиенте (звук слышит только владелец), сервер не нагружается.
 */
public class AnomalyDetectorItem extends Item {
    private static long lastTick = -1;   // защита от двойной обработки, если детекторов в инвентаре несколько
    private static int scanTimer;
    private static int beepTimer;
    private static double nearest = -1.0;
    private static float nearestPitch = 1.0F;
    private static Component nearestName = Component.empty();
    private static double nearestDx, nearestDz;
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    public AnomalyDetectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!level.isClientSide || !(entity instanceof Player player)) {
            return;
        }
        long now = level.getGameTime();
        if (now == lastTick) {
            return;
        }
        lastTick = now;

        double range = AnomalyConfig.DETECTOR_RANGE.get();
        if (--scanTimer <= 0) {
            scanTimer = 8;
            scan(level, player, range);
            boolean inHand = isSelected || player.getOffhandItem() == stack;
            if (inHand && nearest >= 0.0) {
                ChatFormatting danger = nearest < 4.0 ? ChatFormatting.RED : nearest < 9.0 ? ChatFormatting.GOLD : ChatFormatting.GREEN;
                player.displayClientMessage(Component.translatable("message.anomalies.detector",
                        nearestName, String.format("%.1f", nearest), arrow(player)).withStyle(danger), true);
            }
        }
        if (nearest < 0.0) {
            beepTimer = 0;
            return;
        }
        if (--beepTimer <= 0) {
            double closeness = 1.0 - Math.min(1.0, nearest / range);                 // 0 далеко … 1 вплотную
            beepTimer = (int) Mth.lerp(closeness, 40.0, 3.0);                         // ближе — чаще
            float pitch = (float) (nearestPitch + closeness * 0.5);                   // ближе — выше
            level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(), ModSounds.DETECTOR_BEEP.get(),
                    SoundSource.PLAYERS, 0.6F, pitch, false);
        }
    }

    /** Ищет ближайшую аномалию в радиусе (по горизонтали — вся дальность, по вертикали — ±6 блоков). */
    private static void scan(Level level, Player player, double range) {
        int r = (int) Math.ceil(range);
        int px = player.getBlockX(), py = player.getBlockY(), pz = player.getBlockZ();
        double best = Double.MAX_VALUE;
        BlockState bestState = null;
        double bestDx = 0.0, bestDz = 0.0;
        var cursor = new net.minecraft.core.BlockPos.MutableBlockPos();

        for (int x = px - r; x <= px + r; x++) {
            for (int y = py - 6; y <= py + 6; y++) {
                for (int z = pz - r; z <= pz + r; z++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.getBlock() instanceof AnomalyMarker) {
                        double d = player.position().distanceTo(new net.minecraft.world.phys.Vec3(x + 0.5, y + 0.5, z + 0.5));
                        if (d < best) {
                            best = d;
                            bestState = state;
                            bestDx = x + 0.5 - player.getX();
                            bestDz = z + 0.5 - player.getZ();
                        }
                    }
                }
            }
        }
        if (bestState != null && best <= range) {
            nearest = best;
            nearestDx = bestDx;
            nearestDz = bestDz;
            nearestPitch = ((AnomalyMarker) bestState.getBlock()).detectorPitch();
            nearestName = bestState.getBlock().getName();
        } else {
            nearest = -1.0;
        }
    }

    /** Стрелка направления на аномалию относительно того, куда смотрит игрок. */
    private static String arrow(Player player) {
        double targetYaw = Math.toDegrees(Math.atan2(-nearestDx, nearestDz));
        double rel = Mth.wrapDegrees(targetYaw - player.getYRot());
        int index = Math.floorMod((int) Math.round(rel / 45.0), 8);
        return ARROWS[index];
    }
}
