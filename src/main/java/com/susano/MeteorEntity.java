package com.susano;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.world.World;

/** La meteorite : un gros rocher en feu. Le serveur la deplace, le client la dessine (voir MeteorRenderer). */
public class MeteorEntity extends Entity {
    private static final TrackedData<Float> SIZE =
            DataTracker.registerData(MeteorEntity.class, TrackedDataHandlerRegistry.FLOAT);

    /** 0 = meteorite en feu, 1 = sphere de Chibaku Tensei (roche grise, aura sombre). */
    private static final TrackedData<Integer> STYLE =
            DataTracker.registerData(MeteorEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public MeteorEntity(EntityType<? extends MeteorEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(SIZE, 3f);
        this.dataTracker.startTracking(STYLE, 0);
    }

    public float getSize() { return this.dataTracker.get(SIZE); }
    public void setSize(float s) { this.dataTracker.set(SIZE, s); }
    public int getStyle() { return this.dataTracker.get(STYLE); }
    public void setStyle(int s) { this.dataTracker.set(STYLE, s); }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) {
            if (getStyle() != 0) {
                return;
            }
            // prediction cote client (vol en ligne droite) ; le serveur corrige la position
            this.setPosition(this.getX() + this.getVelocity().x, this.getY() + this.getVelocity().y, this.getZ() + this.getVelocity().z);
        } else if (getStyle() == 0 && this.age > 400) {
            this.discard();
        }
    }

    @Override public boolean canHit() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean shouldSave() { return false; }
    @Override public boolean isInvulnerableTo(DamageSource source) { return true; }
    @Override public boolean damage(DamageSource source, float amount) { return false; }
    @Override public boolean isAttackable() { return false; }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) { }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) { }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}
