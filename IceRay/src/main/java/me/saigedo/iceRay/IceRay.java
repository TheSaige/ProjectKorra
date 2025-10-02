package me.saigedo.iceRay;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.ComboAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.WaterAbility;
import com.projectkorra.projectkorra.ability.util.ComboManager;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.region.RegionProtection;
import com.projectkorra.projectkorra.util.BlockSource;
import com.projectkorra.projectkorra.util.ClickType;
import com.projectkorra.projectkorra.util.TempBlock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Objects;

public final class IceRay extends WaterAbility implements AddonAbility, ComboAbility {
    private static final double STEP = 0.5;

    public enum AbilityState {
        PREPARED,
        STARTED,
        FINISHED
    }

    @Attribute(Attribute.COOLDOWN) private final long cooldown;
    @Attribute(Attribute.DURATION) private final long duration;
    @Attribute(Attribute.RADIUS) private final int radius;
    @Attribute(Attribute.SPEED) private final double speed;
    @Attribute(Attribute.RANGE) private final int range;

    private AbilityState abilityState;
    private Location headLocation;

    private boolean rayFinished;
    private long rayFinishedTime;
    private long preparedStartTime;

    private double traveledBlocks = 0.0;

    public IceRay(Player player) {
        super(player);

        int sourceRange = ConfigManager.defaultConfig.get().getInt("ExtraAbilities.Saigedo.IceRay.SourceRange", 6);

        this.cooldown = ConfigManager.defaultConfig.get().getLong("ExtraAbilities.Saigedo.IceRay.Cooldown",5000);
        this.range = ConfigManager.defaultConfig.get().getInt("ExtraAbilities.Saigedo.IceRay.Range",20);
        this.speed = ConfigManager.defaultConfig.get().getDouble("ExtraAbilities.Saigedo.IceRay.Speed",4);
        this.radius = ConfigManager.defaultConfig.get().getInt("ExtraAbilities.Saigedo.IceRay.Radius",6);
        this.duration = ConfigManager.defaultConfig.get().getLong("ExtraAbilities.Saigedo.IceRay.revertIce",6000);

        if (!this.bPlayer.canBendIgnoreBinds(this) || CoreAbility.getAbility(this.player, IceRay.class) != null) {
            return;
        }

        this.abilityState = AbilityState.PREPARED;

        final Block sourceBlock = BlockSource.getWaterSourceBlock(this.player, sourceRange, ClickType.SHIFT_DOWN, true, true, this.bPlayer.canPlantbend());
        if (sourceBlock == null) {
            return;
        }

        final Location origin = sourceBlock.getLocation().add(0, 1, 0);

        this.headLocation = origin.clone();
        this.preparedStartTime = System.currentTimeMillis();
        this.rayFinished = false;

        start();
    }

    @Override
    public void progress() {
        switch (this.abilityState) {
            case PREPARED -> {
                if (!this.player.isSneaking()) {
                    remove();
                    return;
                }

                if (System.currentTimeMillis() - this.preparedStartTime > 3500) {
                    remove();
                    return;
                }

                this.snowParticleAnimation(this.player, this.headLocation, 5, 0, 1, 0, 0.5);
            }

            case STARTED -> {
                final Vector look = this.player.getEyeLocation().getDirection();
                final Vector dir = new Vector(look.getX(), 0, look.getZ()).normalize();

                final boolean advance = this.advanceRay(dir, this.radius, this.range, this.speed);

                if (!rayFinished) {
                    if (!advance) {
                        this.rayFinished = true;
                        this.rayFinishedTime = System.currentTimeMillis();
                        this.abilityState = AbilityState.FINISHED;
                    }
                } else {
                    this.rayFinishedTime = System.currentTimeMillis();
                    this.abilityState = AbilityState.FINISHED;
                }
            }

            case FINISHED -> {
                if (System.currentTimeMillis() - this.rayFinishedTime > this.duration) {
                    remove();
                }
            }
        }
    }

    private boolean advanceRay(final Vector directionUnit, final int effectRadius, final int maxRangeBlocks, final double blocksPerTick) {
        final Vector dir = directionUnit.clone().setY(0).normalize();
        final double segmentLength = Math.max(0.0, blocksPerTick);
        final int segmentSteps = Math.max(1, (int) Math.ceil(segmentLength / STEP));
        final double stepLength = segmentLength / segmentSteps;

        Location cursor = this.headLocation.clone();

        for (int i = 0; i < segmentSteps; i++) {
            final Location next = cursor.clone().add(dir.clone().multiply(stepLength));
            next.setY(this.headLocation.getY());

            final boolean blockedHere = GeneralMethods.isSolid(next.getBlock());
            final boolean blockedHeadroom = GeneralMethods.isSolid(next.clone().add(0, 1, 0).getBlock());
            if (blockedHere || blockedHeadroom) {
                this.headLocation = cursor.clone();
                return false;
            }

            if (!applyAreaEffects2D(next, effectRadius)) {
                this.headLocation = next.clone();
                return false;
            }

            this.traveledBlocks += stepLength;
            if (this.traveledBlocks >= (double) maxRangeBlocks) {
                this.headLocation = next.clone();
                return false;
            }

            cursor = next;
            snowParticleAnimation(this.player, next, 4, 0.6, 0.0, 0.6, 0.25);
        }

        this.headLocation = cursor.clone();
        return true;
    }

    private boolean applyAreaEffects2D(final Location center, final int effectRadius) {
        final int centerX = center.getBlockX();
        final int centerY = center.getBlockY();
        final int centerZ = center.getBlockZ();

        final int radius = Math.max(0, effectRadius);
        final int radiusSquared = radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radiusSquared) {
                    continue;
                }

                final Location location = new Location(center.getWorld(), centerX + dx, centerY, centerZ + dz);
                final Block here = location.getBlock();
                final Block below = here.getRelative(BlockFace.DOWN);

                if (here.getType() == Material.LAVA || here.getType() == Material.LAVA_CAULDRON || below.getType() == Material.LAVA || below.getType() == Material.LAVA_CAULDRON) {
                    Objects.requireNonNull(center.getWorld()).spawnParticle(Particle.CLOUD, center, 20, Math.random(), Math.random(), Math.random(), 0.3);
                    return false;
                }

                if (below.getType() == Material.WATER && here.getType().isAir()) {
                    if (!RegionProtection.isRegionProtected(this, below.getLocation())) {
                        new TempBlock(below, Material.ICE.createBlockData(), this.duration, this);
                    }
                    continue;
                }

                if (below.getType().isSolid() && GeneralMethods.isTransparent(here)) {
                    if (!RegionProtection.isRegionProtected(this, here.getLocation())) {
                        new TempBlock(here, Material.SNOW.createBlockData(), this.duration, this);
                    }
                }
            }
        }
        return true;
    }

    public void startAbility() {
        this.abilityState = AbilityState.STARTED;
    }

    private void snowParticleAnimation(final Player player, final Location location, final int count, final double spreadX, final double spreadY, final double spreadZ, final double speed) {
        player.getWorld().spawnParticle(Particle.ITEM_SNOWBALL, location, count, spreadX, spreadY, spreadZ, speed);
    }

    @Override
    public void remove() {
        super.remove();
        this.bPlayer.addCooldown(this);
    }

    @Override
    public boolean isSneakAbility() {
        return true;
    }

    @Override
    public boolean isHarmlessAbility() {
        return false;
    }

    @Override
    public long getCooldown() {
        return this.cooldown;
    }

    @Override
    public Location getLocation() {
        return this.headLocation;
    }

    @Override
    public void load() {
        Bukkit.getPluginManager().registerEvents(new IceRayListener(), ProjectKorra.plugin);

        ConfigManager.defaultConfig.get().addDefault("ExtraAbilities.Saigedo.IceRay.SourceRange", 6);
        ConfigManager.defaultConfig.get().addDefault("ExtraAbilities.Saigedo.IceRay.Cooldown", 5000);
        ConfigManager.defaultConfig.get().addDefault("ExtraAbilities.Saigedo.IceRay.Range", 20);
        ConfigManager.defaultConfig.get().addDefault("ExtraAbilities.Saigedo.IceRay.Speed", 4);
        ConfigManager.defaultConfig.get().addDefault("ExtraAbilities.Saigedo.IceRay.Radius", 6);
        ConfigManager.defaultConfig.get().addDefault("ExtraAbilities.Saigedo.IceRay.revertIce", 6000);

        ConfigManager.defaultConfig.save();
    }

    @Override
    public void stop() {}

    @Override
    public Object createNewComboInstance(Player player) {
        return new IceRay(player);
    }

    @Override
    public ArrayList<ComboManager.AbilityInformation> getCombination() {
        ArrayList<ComboManager.AbilityInformation> combo = new ArrayList<>();
        combo.add(new ComboManager.AbilityInformation("PhaseChange", ClickType.SHIFT_DOWN));
        combo.add(new ComboManager.AbilityInformation("PhaseChange", ClickType.SHIFT_UP));
        combo.add(new ComboManager.AbilityInformation("PhaseChange", ClickType.SHIFT_DOWN));
        combo.add(new ComboManager.AbilityInformation("PhaseChange", ClickType.SHIFT_UP));
        combo.add(new ComboManager.AbilityInformation("PhaseChange", ClickType.SHIFT_DOWN));

        return combo;
    }

    @Override
    public String getAuthor() {
        return "Saigedo";
    }

    @Override
    public String getName() {
        return "IceRay";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Send out a line of snow";
    }

    @Override
    public String getInstructions() {
        return """
                PhaseChange (Tap Sneak on Water Source) ->\s
                PhaseChange (Tap Sneak on Water Source) ->\s
                PhaseChange (Hold Sneak on a Water Source) ->\s
                FrostBreath (Left Click)""";
    }

    public AbilityState getAbilityState() {
        return this.abilityState;
    }
}
