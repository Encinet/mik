package org.encinet.mik.module.governance.voting.model;

public record VoteTally(int yes, int no, int abstain) {
    public VoteTally {
        if (yes < 0 || no < 0 || abstain < 0) {
            throw new IllegalArgumentException("vote counts must not be negative");
        }
    }

    public int participating() {
        return yes + no + abstain;
    }
}
