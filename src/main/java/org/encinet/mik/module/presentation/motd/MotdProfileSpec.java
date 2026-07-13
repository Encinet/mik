package org.encinet.mik.module.presentation.motd;

public record MotdProfileSpec(
        String brand,
        String category,
        String[] normal,
        String[][] eggs,
        String[] afk,
        String[] night,
        String[] knownPlayer
) {
    public MotdProfileSpec {
        normal = normal.clone();
        eggs = cloneBranches(eggs);
        afk = afk.clone();
        night = night.clone();
        knownPlayer = knownPlayer.clone();
    }

    @Override
    public String[] normal() {
        return normal.clone();
    }

    @Override
    public String[][] eggs() {
        return cloneBranches(eggs);
    }

    @Override
    public String[] afk() {
        return afk.clone();
    }

    @Override
    public String[] night() {
        return night.clone();
    }

    @Override
    public String[] knownPlayer() {
        return knownPlayer.clone();
    }

    private static String[][] cloneBranches(String[][] branches) {
        String[][] copy = new String[branches.length][];
        for (int i = 0; i < branches.length; i++) {
            copy[i] = branches[i].clone();
        }
        return copy;
    }
}
