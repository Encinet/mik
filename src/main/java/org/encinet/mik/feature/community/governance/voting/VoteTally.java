package org.encinet.mik.feature.community.governance.voting;

public record VoteTally(int yes, int no, int abstain, int participating) {
    public VoteTally {
        if (yes < 0 || no < 0 || abstain < 0 || participating < yes + no + abstain) {
            throw new IllegalArgumentException("vote counts must not be negative");
        }
    }

    public VoteTally(int yes, int no, int abstain) { this(yes, no, abstain, yes + no + abstain); }
}
