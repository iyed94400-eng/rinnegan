package com.susano;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Les six voies de Pain : pouvoirs du Rinnegan (touches J = utiliser, K = pouvoir suivant). */
public final class RinneganManager {
    public static final String[] PATH_KEYS = {"deva", "banshou", "chibaku", "preta", "naraka", "animal", "human", "asura"};
    private static final int PATHS = PATH_KEYS.length;
    /** Recharge (ticks) de chaque pouvoir. */
    private static final int[] CD = {80, 60, 600, 400, 800, 600, 200, 300};

    private static final double SHINRA_R = 28.0, BANSHOU_R = 30.0;
    private static final double CHIBAKU_RANGE = 70.0, CHIBAKU_CRATER = 14.0;
    private static final int CHIBAKU_LIFE = 200;

    private static final DustParticleEffect BLACK = new DustParticleEffect(new Vector3f(0.03f, 0.0f, 0.05f), 3.5f);
    private static final DustParticleEffect PURPLE = new DustParticleEffect(new Vector3f(0.55f, 0.25f, 0.9f), 1.6f);

    private static final Map<UUID, Integer> SELECTED = new HashMap<>();
    private static final Map<UUID, long[]> CDS = new HashMap<>();
    private static final Map<UUID, Long> PRETA = new HashMap<>();
    private static final Map<UUID, Long> NARAKA = new HashMap<>();
    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Summon> SUMMONS = new ArrayList<>();

    private interface Task {
        /** Retourne false quand la tache est terminee. */
        boolean tick(MinecraftServer server);
    }

    private record Summon(LivingEntity entity, long until) { }

    private RinneganManager() { }

    public static void clear() {
        TASKS.clear();
        SUMMONS.clear();
        PRETA.clear();
        NARAKA.clear();
    }

    // ------------------------------------------------------------ choix et utilisation

    private static boolean hasRinnegan(ServerPlayerEntity p) {
        if (p.getMainHandStack().getItem() instanceof RinneganItem || p.getOffHandStack().getItem() instanceof RinneganItem) {
            return true;
        }
        for (ItemStack s : p.getInventory().main) {
            if (s.getItem() instanceof RinneganItem) return true;
        }
        return false;
    }

    /** Touche K : pouvoir suivant (delta = 1) ou precedent (-1). */
    public static void cycle(ServerPlayerEntity p, int delta) {
        if (!hasRinnegan(p)) {
            p.sendMessage(Text.translatable("message.susanoo.need_rinnegan"), true);
            return;
        }
        int s = Math.floorMod(SELECTED.getOrDefault(p.getUuid(), 0) + delta, PATHS);
        SELECTED.put(p.getUuid(), s);
        p.sendMessage(Text.translatable("message.susanoo.path", Text.translatable("path.susanoo." + PATH_KEYS[s])), true);
        p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                SoundCategory.PLAYERS, 1.5f, 0.8f);
    }

    /** Touche J (ou clic droit sur le Rinnegan) : utilise le pouvoir choisi. */
    public static void use(ServerPlayerEntity p) {
        if (!hasRinnegan(p)) {
            p.sendMessage(Text.translatable("message.susanoo.need_rinnegan"), true);
            return;
        }
        int path = SELECTED.getOrDefault(p.getUuid(), 0);
        ServerWorld w = p.getServerWorld();
        long now = w.getTime();
        long[] cd = CDS.computeIfAbsent(p.getUuid(), k -> new long[PATHS]);
        if (now < cd[path]) {
            p.sendMessage(Text.translatable("message.susanoo.rin_cd", Text.translatable("path.susanoo." + PATH_KEYS[path]),
                    (cd[path] - now + 19) / 20), true);
            return;
        }
        cd[path] = now + CD[path];
        p.sendMessage(Text.translatable("path.susanoo." + PATH_KEYS[path]), true);
        switch (path) {
            case 0 -> shinraTensei(p);
            case 1 -> bansho(p);
            case 2 -> chibaku(p);
            case 3 -> preta(p);
            case 4 -> naraka(p);
            case 5 -> animal(p);
            case 6 -> {
                if (!human(p)) {
                    cd[path] = 0;
                }
            }
            default -> asura(p);
        }
    }

    // ------------------------------------------------------------ tick

    public static void tick(MinecraftServer server) {
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            if (!it.next().tick(server)) {
                it.remove();
            }
        }
        Iterator<Summon> si = SUMMONS.iterator();
        while (si.hasNext()) {
            Summon s = si.next();
            if (s.entity().isRemoved() || !s.entity().isAlive()) {
                si.remove();
            } else if (s.entity().getWorld().getTime() >= s.until()) {
                ServerWorld w = (ServerWorld) s.entity().getWorld();
                w.spawnParticles(ParticleTypes.POOF, s.entity().getX(), s.entity().getY() + 0.5, s.entity().getZ(), 12, 0.4, 0.4, 0.4, 0.05);
                s.entity().discard();
                si.remove();
            }
        }
        Iterator<Map.Entry<UUID, Long>> pi = PRETA.entrySet().iterator();
        while (pi.hasNext()) {
            Map.Entry<UUID, Long> en = pi.next();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(en.getKey());
            if (p == null || p.getWorld().getTime() > en.getValue()) {
                pi.remove();
                continue;
            }
            p.getServerWorld().spawnParticles(PURPLE, p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.6, 0.9, 0.6, 0);
        }
    }

    private static ServerPlayerEntity player(MinecraftServer s, UUID id) {
        return s.getPlayerManager().getPlayer(id);
    }

    // ------------------------------------------------------------ voie Deva

    /** Shinra Tensei : repousse tout autour de toi, aplatit ce qui depasse du sol. */
    private static void shinraTensei(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        Vec3d c = p.getPos();
        Box box = new Box(c, c).expand(SHINRA_R, SHINRA_R, SHINRA_R);
        for (LivingEntity t : w.getEntitiesByClass(LivingEntity.class, box, x -> SusanooManager.canTarget(p, x))) {
            double d = t.getPos().distanceTo(c);
            if (d > SHINRA_R) continue;
            float k = (float) (1.0 - d / SHINRA_R);
            t.damage(w.getDamageSources().playerAttack(p), 40f * (0.3f + 0.7f * k));
            Vec3d out = outward(t.getPos(), c);
            t.addVelocity(out.x * (2 + 4 * k), 0.6 + 0.8 * k, out.z * (2 + 4 * k));
            t.velocityModified = true;
        }
        for (ProjectileEntity pr : w.getEntitiesByClass(ProjectileEntity.class, box, x -> x.getOwner() != p)) {
            Vec3d out = outward(pr.getPos(), c);
            pr.setVelocity(out.x * 2.5, 0.2, out.z * 2.5);
            pr.velocityModified = true;
        }
        SusanooManager.fx(w, ParticleTypes.SONIC_BOOM, c.add(0, 1, 0), 1, 0, 0, 0, 0);
        w.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 8f, 0.6f);
        w.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 8f, 0.5f);
        final UUID id = p.getUuid();
        TASKS.add(new Task() {
            int age;
            double last;

            @Override
            public boolean tick(MinecraftServer server) {
                ServerPlayerEntity owner = player(server, id);
                if (owner == null) return false;
                age++;
                double cur = SHINRA_R * Math.min(1.0, age / 10.0);
                ServerWorld world = owner.getServerWorld();
                int n = (int) (12 + cur * 2.5);
                for (int i = 0; i < n; i++) {
                    double a = i * Math.PI * 2 / n;
                    SusanooManager.fx(world, i % 2 == 0 ? ParticleTypes.CLOUD : ParticleTypes.LARGE_SMOKE,
                            new Vec3d(c.x + Math.cos(a) * cur, c.y + 0.6, c.z + Math.sin(a) * cur), 1, 0.2, 0.3, 0.2, 0.03);
                }
                shell(world, owner, c, last, cur);
                last = cur;
                return age < 10;
            }
        });
    }

    private static Vec3d outward(Vec3d pos, Vec3d c) {
        Vec3d o = new Vec3d(pos.x - c.x, 0, pos.z - c.z);
        return o.lengthSquared() < 1.0e-4 ? new Vec3d(0, 0, 1) : o.normalize();
    }

    /** Detruit la demi-sphere au-dessus des pieds, entre deux rayons (le sol reste : le terrain est aplati). */
    private static void shell(ServerWorld w, ServerPlayerEntity p, Vec3d c, double r0, double r1) {
        if (!w.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) return;
        int ir = (int) Math.ceil(r1);
        int cx = (int) Math.floor(c.x), cy = (int) Math.floor(c.y), cz = (int) Math.floor(c.z);
        for (int dx = -ir; dx <= ir; dx++) {
            for (int dz = -ir; dz <= ir; dz++) {
                if (!w.getChunkManager().isChunkLoaded((cx + dx) >> 4, (cz + dz) >> 4)) continue;
                for (int dy = 0; dy <= ir; dy++) {
                    double d2 = dx * dx + dz * dz + dy * dy;
                    if (d2 > r1 * r1 || d2 <= r0 * r0) continue;
                    SusanooManager.wipe(w, p, new BlockPos(cx + dx, cy + dy, cz + dz));
                }
            }
        }
    }

    /** Banshō Ten'in : attire tout vers toi pendant 1,5 s, ecrase ce qui arrive au contact. */
    private static void bansho(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 4f, 0.5f);
        final UUID id = p.getUuid();
        TASKS.add(new Task() {
            int age;

            @Override
            public boolean tick(MinecraftServer server) {
                ServerPlayerEntity owner = player(server, id);
                if (owner == null || age >= 30) return false;
                age++;
                ServerWorld world = owner.getServerWorld();
                Vec3d c = owner.getPos().add(0, 1, 0);
                Box box = new Box(c, c).expand(BANSHOU_R);
                for (LivingEntity t : world.getEntitiesByClass(LivingEntity.class, box, x -> SusanooManager.canTarget(owner, x))) {
                    Vec3d to = c.subtract(t.getPos().add(0, t.getHeight() / 2.0, 0));
                    double d = to.length();
                    if (d < 2.5) {
                        if (age % 10 == 0) t.damage(world.getDamageSources().playerAttack(owner), 12f);
                        continue;
                    }
                    t.setVelocity(to.normalize().multiply(Math.min(2.0, 0.8 + d * 0.08)));
                    t.velocityModified = true;
                }
                for (ItemEntity it : world.getEntitiesByClass(ItemEntity.class, box, x -> true)) {
                    Vec3d to = c.subtract(it.getPos());
                    if (to.length() > 1.5) {
                        it.setVelocity(to.normalize().multiply(0.9));
                        it.velocityModified = true;
                    }
                }
                if (age % 2 == 0) {
                    for (int i = 0; i < 12; i++) {
                        double a = world.random.nextDouble() * Math.PI * 2;
                        double r = 6 + world.random.nextDouble() * (BANSHOU_R - 6);
                        Vec3d at = c.add(Math.cos(a) * r, (world.random.nextDouble() - 0.3) * 4, Math.sin(a) * r);
                        Vec3d in = c.subtract(at).normalize().multiply(0.6);
                        SusanooManager.fx(world, PURPLE, at, 0, in.x, in.y, in.z, 1.0);
                    }
                }
                return true;
            }
        });
    }

    /** Chibaku Tensei : une planete de roche se forme, tout est aspire vers elle puis ecrase. */
    private static void chibaku(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        Vec3d target = aim(p, CHIBAKU_RANGE, CHIBAKU_CRATER + 10);
        MeteorEntity sphere = SusanooMod.METEOR.create(w);
        if (sphere == null) return;
        sphere.setStyle(1);
        sphere.setSize(8f);
        sphere.setVelocity(Vec3d.ZERO);
        sphere.setPosition(target.x, target.y + 2, target.z);
        w.spawnEntity(sphere);
        w.playSound(null, target.x, target.y, target.z, SoundEvents.ENTITY_WARDEN_EMERGE, SoundCategory.PLAYERS, 8f, 0.5f);
        final UUID id = p.getUuid();
        final BlockPos center = BlockPos.ofFloored(target.x, target.y - 2, target.z);
        TASKS.add(new Task() {
            int age;
            int nextY = (int) CHIBAKU_CRATER;

            @Override
            public boolean tick(MinecraftServer server) {
                ServerPlayerEntity owner = player(server, id);
                if (owner == null || sphere.isRemoved()) {
                    sphere.discard();
                    return false;
                }
                age++;
                ServerWorld world = owner.getServerWorld();
                Vec3d pos = new Vec3d(target.x, target.y + 2 + Math.min(28.0, age * 0.4), target.z);
                sphere.setPosition(pos.x, pos.y, pos.z);
                // le sol est arrache par couches, de haut en bas
                int r = (int) CHIBAKU_CRATER;
                if (world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
                    for (int layer = 0; layer < 3 && nextY >= -r; layer++, nextY--) {
                        int rr = (int) Math.floor(Math.sqrt(r * r - nextY * nextY));
                        for (int dx = -rr; dx <= rr; dx++) {
                            for (int dz = -rr; dz <= rr; dz++) {
                                if (dx * dx + dz * dz + nextY * nextY > r * r) continue;
                                SusanooManager.wipe(world, owner, center.add(dx, nextY, dz));
                            }
                        }
                    }
                }
                // attraction : tout ce qui vit est aspire vers la sphere
                Box box = new Box(pos, pos).expand(45);
                for (LivingEntity t : world.getEntitiesByClass(LivingEntity.class, box, x -> SusanooManager.canTarget(owner, x))) {
                    Vec3d to = pos.subtract(t.getPos().add(0, t.getHeight() / 2.0, 0));
                    double d = to.length();
                    if (d < 2) continue;
                    t.setVelocity(t.getVelocity().multiply(0.8).add(to.normalize().multiply(0.45)));
                    t.velocityModified = true;
                    t.fallDistance = 0f;
                    if (d < 12 && age % 10 == 0) {
                        t.damage(world.getDamageSources().magic(), 15f);
                    }
                }
                if (age % 2 == 0) {
                    for (int i = 0; i < 10; i++) {
                        double a = world.random.nextDouble() * Math.PI * 2;
                        double r2 = 9 + world.random.nextDouble() * 3;
                        SusanooManager.fx(world, i % 2 == 0 ? BLACK : PURPLE,
                                pos.add(Math.cos(a) * r2, (world.random.nextDouble() - 0.5) * 8, Math.sin(a) * r2), 2, 0.3, 0.3, 0.3, 0.01);
                    }
                }
                if (age >= CHIBAKU_LIFE) {
                    world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 10f, 0.5f);
                    SusanooManager.fx(world, ParticleTypes.EXPLOSION_EMITTER, pos, 12, 5, 5, 5, 0);
                    SusanooManager.fx(world, ParticleTypes.LARGE_SMOKE, pos, 300, 8, 8, 8, 0.2);
                    for (LivingEntity t : world.getEntitiesByClass(LivingEntity.class, new Box(pos, pos).expand(25),
                            x -> SusanooManager.canTarget(owner, x))) {
                        t.damage(world.getDamageSources().playerAttack(owner), 400f);
                    }
                    sphere.discard();
                    return false;
                }
                return true;
            }
        });
    }

    /** Point vise (bloc touche, sinon sol sous le point d'arrivee), jamais plus pres que minDist. */
    private static Vec3d aim(ServerPlayerEntity p, double range, double minDist) {
        ServerWorld w = p.getServerWorld();
        Vec3d eye = p.getEyePos();
        Vec3d end = eye.add(p.getRotationVec(1f).multiply(range));
        BlockHitResult hit = w.raycast(new RaycastContext(eye, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        Vec3d t = hit.getType() == HitResult.Type.BLOCK ? hit.getPos() : SusanooManager.surface(w, p, end.x, end.y, end.z);
        if (Math.hypot(t.x - p.getX(), t.z - p.getZ()) < minDist) {
            Vec3d f = Vec3d.fromPolar(0f, p.getYaw());
            t = SusanooManager.surface(w, p, p.getX() + f.x * minDist, p.getY() + 40, p.getZ() + f.z * minDist);
        }
        return t;
    }

    // ------------------------------------------------------------ voie Preta

    /** Absorption de chakra : 10 s pendant lesquelles les degats sont annules et se transforment en absorption. */
    private static void preta(ServerPlayerEntity p) {
        PRETA.put(p.getUuid(), p.getWorld().getTime() + 200);
        p.sendMessage(Text.translatable("message.susanoo.preta"), true);
        p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 2f, 1.5f);
    }

    /** Appele par ALLOW_DAMAGE. Retourne true si les degats doivent continuer. */
    public static boolean pretaAbsorb(ServerPlayerEntity p, DamageSource src, float amount) {
        Long until = PRETA.get(p.getUuid());
        if (until == null) return true;
        if (p.getWorld().getTime() > until) {
            PRETA.remove(p.getUuid());
            return true;
        }
        if (src.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
        p.setAbsorptionAmount(Math.min(20f, p.getAbsorptionAmount() + amount * 0.5f));
        p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.5f, 0.6f);
        return false;
    }

    // ------------------------------------------------------------ voie Naraka

    /** Roi de l'Enfer : soigne toi et tes allies, et te ressuscite si tu meurs dans la minute qui suit. */
    private static void naraka(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        NARAKA.put(p.getUuid(), w.getTime() + 1200);
        heal(p);
        Box box = p.getBoundingBox().expand(12);
        for (LivingEntity t : w.getEntitiesByClass(LivingEntity.class, box,
                x -> x != p && (x instanceof PlayerEntity || (x instanceof TameableEntity te && te.isOwner(p))))) {
            heal(t);
        }
        SusanooManager.fx(w, BLACK, p.getPos().add(0, 1.5, 0), 120, 3, 2, 3, 0.02);
        SusanooManager.fx(w, ParticleTypes.SOUL, p.getPos().add(0, 1, 0), 40, 2, 1.5, 2, 0.05);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 3f, 0.6f);
    }

    private static void heal(LivingEntity t) {
        t.setHealth(t.getMaxHealth());
        List<StatusEffect> bad = new ArrayList<>();
        for (StatusEffectInstance e : t.getStatusEffects()) {
            if (e.getEffectType().getCategory() == StatusEffectCategory.HARMFUL) bad.add(e.getEffectType());
        }
        for (StatusEffect e : bad) t.removeStatusEffect(e);
        t.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 1));
        if (t instanceof PlayerEntity pl) {
            pl.getHungerManager().setFoodLevel(20);
        }
    }

    /** Appele par ALLOW_DEATH. Retourne false (annule la mort) si le Roi de l'Enfer protege le joueur. */
    public static boolean allowDeath(ServerPlayerEntity p) {
        Long until = NARAKA.get(p.getUuid());
        if (until == null) return true;
        NARAKA.remove(p.getUuid());
        if (p.getWorld().getTime() > until) return true;
        heal(p);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 2));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 4));
        p.sendMessage(Text.translatable("message.susanoo.revived"), true);
        ServerWorld w = p.getServerWorld();
        SusanooManager.fx(w, ParticleTypes.SOUL, p.getPos().add(0, 1, 0), 60, 1, 1.5, 1, 0.1);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 2f, 0.7f);
        return false;
    }

    // ------------------------------------------------------------ voie Animale

    /** Invoque quatre loups enrages et un golem qui combattent pour toi pendant 30 s. */
    private static void animal(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        long until = w.getTime() + 600;
        Vec3d c = p.getPos();
        for (int i = 0; i < 4; i++) {
            WolfEntity wolf = EntityType.WOLF.create(w);
            if (wolf == null) continue;
            double a = i * Math.PI / 2;
            wolf.refreshPositionAndAngles(c.x + Math.cos(a) * 3, c.y, c.z + Math.sin(a) * 3, p.getYaw(), 0f);
            wolf.setTamed(true);
            wolf.setOwner(p);
            wolf.setCustomName(Text.translatable("entity.susanoo.summon"));
            wolf.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 700, 3));
            wolf.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 700, 2));
            wolf.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 700, 2));
            wolf.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 700, 4));
            w.spawnEntity(wolf);
            SUMMONS.add(new Summon(wolf, until));
        }
        IronGolemEntity golem = EntityType.IRON_GOLEM.create(w);
        if (golem != null) {
            golem.refreshPositionAndAngles(c.x, c.y, c.z - 4, p.getYaw(), 0f);
            golem.setPlayerCreated(true);
            golem.setCustomName(Text.translatable("entity.susanoo.summon"));
            golem.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 700, 3));
            golem.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 700, 2));
            w.spawnEntity(golem);
            SUMMONS.add(new Summon(golem, until));
        }
        SusanooManager.fx(w, BLACK, c.add(0, 0.5, 0), 80, 3, 0.5, 3, 0.02);
        w.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_WOLF_HOWL, SoundCategory.PLAYERS, 4f, 0.7f);
    }

    // ------------------------------------------------------------ voie Humaine

    /** Extraction d'ame : arrache l'ame de la creature visee (la tue) et te soigne. Retourne false sans cible. */
    private static boolean human(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1f);
        LivingEntity best = null;
        double bestD = 35.0;
        for (LivingEntity t : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(35), x -> SusanooManager.canTarget(p, x))) {
            Vec3d to = t.getEyePos().subtract(eye);
            double d = to.length();
            if (d > bestD || to.normalize().dotProduct(look) < 0.97 || !p.canSee(t)) continue;
            best = t;
            bestD = d;
        }
        if (best == null) {
            p.sendMessage(Text.translatable("message.susanoo.no_target"), true);
            return false;
        }
        Vec3d from = best.getPos().add(0, best.getHeight() / 2.0, 0);
        for (double d = 0; d < bestD; d += 0.6) {
            Vec3d at = from.lerp(eye, d / bestD);
            SusanooManager.fx(w, ParticleTypes.SOUL, at, 1, 0.1, 0.1, 0.1, 0.01);
        }
        best.damage(w.getDamageSources().magic(), 1000f);
        p.heal(10f);
        p.setAbsorptionAmount(Math.min(20f, p.getAbsorptionAmount() + 6f));
        w.playSound(null, best.getX(), best.getY(), best.getZ(), SoundEvents.PARTICLE_SOUL_ESCAPE, SoundCategory.PLAYERS, 4f, 0.6f);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.PLAYERS, 1.5f, 1.4f);
        return true;
    }

    // ------------------------------------------------------------ voie Asura

    /** Salve de missiles explosifs tires des epaules. */
    private static void asura(ServerPlayerEntity p) {
        final UUID id = p.getUuid();
        TASKS.add(new Task() {
            int age;

            @Override
            public boolean tick(MinecraftServer server) {
                ServerPlayerEntity owner = player(server, id);
                if (owner == null || age >= 12) return false;
                age++;
                if (age % 2 != 0) return true;
                ServerWorld world = owner.getServerWorld();
                Vec3d eye = owner.getEyePos();
                Vec3d look = owner.getRotationVec(1f);
                Vec3d right = new Vec3d(-look.z, 0, look.x).normalize();
                for (int side = -1; side <= 1; side += 2) {
                    Vec3d pos = eye.add(right.multiply(side * 1.6)).add(0, 0.2 + world.random.nextDouble() * 0.6, 0).add(look.multiply(1.5));
                    Vec3d dir = look.add((world.random.nextDouble() - 0.5) * 0.12, (world.random.nextDouble() - 0.5) * 0.12,
                            (world.random.nextDouble() - 0.5) * 0.12).normalize();
                    FireballEntity fb = new FireballEntity(world, owner, dir.x, dir.y, dir.z, 3);
                    fb.setPosition(pos.x, pos.y, pos.z);
                    world.spawnEntity(fb);
                }
                world.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 3f, 0.6f);
                return true;
            }
        });
    }
}
