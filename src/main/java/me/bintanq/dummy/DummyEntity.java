package me.bintanq.dummy;

import java.util.UUID;

public class DummyEntity {

    private final UUID uuid;
    private final DummyType type;
    private final double maxHp;
    private final String nametag;

    public DummyEntity(UUID uuid, DummyType type, double maxHp, String nametag) {
        this.uuid = uuid;
        this.type = type;
        this.maxHp = maxHp;
        this.nametag = nametag;
    }

    public UUID getUuid() { return uuid; }
    public DummyType getType() { return type; }
    public double getMaxHp() { return maxHp; }
    public String getNametag() { return nametag; }
}