package org.encinet.mik.module.music.rhythm.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Batch beat tracker that turns permissive onset candidates into a coherent chart.
 *
 * <p>Unlike the PCM peak picker, this class is deliberately non-streaming. It can
 * compare every candidate with later accents, estimate tempo and phase for short
 * regions, recover an isolated weak beat, and reject off-grid decoration. When a
 * region is not periodic enough, it falls back to conservative anchors plus
 * independently strong onset candidates instead of silently producing no chart.</p>
 */
final class RhythmBeatTracker {
    private static final long MERGE_WINDOW_MILLIS = 45L;
    private static final long ACTIVE_GAP_MILLIS = 2_500L;
    private static final long TARGET_CHUNK_MILLIS = 9_000L;
    private static final long MAXIMUM_CHUNK_MILLIS = 12_000L;
    private static final long MINIMUM_OUTPUT_INTERVAL_MILLIS = 90L;
    private static final double MINIMUM_PERIOD_MILLIS = 150.0;
    private static final double MAXIMUM_PERIOD_MILLIS = 1_100.0;
    private static final double PERIOD_STEP_MILLIS = 4.0;
    private static final int MAXIMUM_PAIR_BEATS = 8;
    private static final int MINIMUM_MATCHED_BEATS = 4;
    private static final double MINIMUM_CONFIDENCE = 0.45;
    private static final double MINIMUM_PERIOD_COHERENCE = 0.28;
    private static final double MINIMUM_PHASE_ALIGNMENT = 0.35;
    private static final double MINIMUM_SLOT_OCCUPANCY = 0.48;
    private static final double STRONG_CANDIDATE_FALLBACK = 0.36;

    private RhythmBeatTracker() {
    }

    static List<RhythmPulse> refine(List<RhythmPulse> rawCandidates,
                                    List<RhythmPulse> conservativeAnchors,
                                    long durationMillis) {
        long duration = Math.max(0L, durationMillis);
        List<Candidate> candidates = merge(rawCandidates, conservativeAnchors,
                duration);
        if (candidates.size() < MINIMUM_MATCHED_BEATS) {
            return suppressNearby(fallback(candidates));
        }

        List<RhythmPulse> selected = new ArrayList<>();
        for (List<Candidate> activeGroup : activeGroups(candidates)) {
            for (List<Candidate> chunk : chunks(activeGroup)) {
                Fit fit = fit(chunk);
                if (fit.confident()) {
                    selected.addAll(renderGrid(chunk, fit, duration));
                    retainStrongOffGridAccents(selected, chunk, fit.periodMillis());
                } else {
                    selected.addAll(fallback(chunk));
                }
            }
        }
        return suppressNearby(selected);
    }

    private static Fit fit(List<Candidate> candidates) {
        if (candidates.size() < MINIMUM_MATCHED_BEATS) return Fit.rejected();
        long anchorCount = candidates.stream().filter(Candidate::anchor).count();
        if (anchorCount == 0L && candidates.size() < 8) return Fit.rejected();
        PeriodEstimate period = estimatePeriod(candidates);
        if (period.coherence() < MINIMUM_PERIOD_COHERENCE) return Fit.rejected();

        PhaseEstimate phase = estimatePhase(candidates, period.periodMillis());
        SlotSummary slots = summarizeSlots(candidates, phase.phaseMillis(),
                period.periodMillis());
        double confidence = period.coherence() * 0.45
                + phase.alignment() * 0.30 + slots.occupancy() * 0.25;
        double maximumStrength = candidates.stream().mapToDouble(candidate ->
                candidate.pulse().strength()).max().orElse(0.0);
        boolean lowSalienceCandidateOnly = anchorCount == 0L
                && maximumStrength < STRONG_CANDIDATE_FALLBACK;
        int requiredMatches = lowSalienceCandidateOnly ? 6 : MINIMUM_MATCHED_BEATS;
        double requiredCoherence = lowSalienceCandidateOnly
                ? 0.42 : MINIMUM_PERIOD_COHERENCE;
        double requiredAlignment = lowSalienceCandidateOnly
                ? 0.58 : MINIMUM_PHASE_ALIGNMENT;
        double requiredOccupancy = lowSalienceCandidateOnly
                ? 0.80 : MINIMUM_SLOT_OCCUPANCY;
        boolean confident = slots.matched() >= requiredMatches
                && period.coherence() >= requiredCoherence
                && phase.alignment() >= requiredAlignment
                && slots.occupancy() >= requiredOccupancy
                && confidence >= MINIMUM_CONFIDENCE;
        return new Fit(period.periodMillis(), phase.phaseMillis(), confidence,
                confident);
    }

    private static PeriodEstimate estimatePeriod(List<Candidate> candidates) {
        List<TempoCandidate> shortlist = new ArrayList<>();
        for (double period = MINIMUM_PERIOD_MILLIS;
             period <= MAXIMUM_PERIOD_MILLIS; period += PERIOD_STEP_MILLIS) {
            PairScore pairs = pairScore(candidates, period);
            if (pairs.weight() <= 0.0) continue;
            double coherence = pairs.alignedWeight() / pairs.weight();
            double stepQuality = adjacentStepQuality(candidates, period);
            double tempoPrior = tempoPrior(period);
            // Phase coverage is evaluated below. Keep the broad tempo prior weak
            // enough that a real 150-250 ms subdivision cannot be forced to half time.
            double score = coherence * 0.66 + stepQuality * 0.12
                    + tempoPrior * 0.08;
            shortlist.add(new TempoCandidate(period, coherence, score));
        }
        shortlist.sort(Comparator.comparingDouble(TempoCandidate::score).reversed());

        double bestPeriod = 500.0;
        double bestScore = -1.0;
        double bestCoherence = 0.0;
        for (TempoCandidate candidate : shortlist.stream().limit(32).toList()) {
            PhaseEstimate phase = estimatePhase(candidates,
                    candidate.periodMillis());
            SlotSummary slots = summarizeSlots(candidates, phase.phaseMillis(),
                    candidate.periodMillis());
            double score = candidate.score() * 0.48 + phase.alignment() * 0.34
                    + slots.occupancy() * 0.18;
            double fastPeriod = Math.clamp(
                    (300.0 - candidate.periodMillis()) / 120.0, 0.0, 1.0);
            double subdivisionStrength = Math.clamp(
                    (slots.medianStrength() - 0.30) / 0.12, 0.0, 1.0);
            score -= fastPeriod * (1.0 - subdivisionStrength) * 0.20;
            if (score > bestScore) {
                bestScore = score;
                bestPeriod = candidate.periodMillis();
                bestCoherence = candidate.coherence();
            }
        }
        return new PeriodEstimate(bestPeriod, bestCoherence);
    }

    private static PairScore pairScore(List<Candidate> candidates, double period) {
        double aligned = 0.0;
        double total = 0.0;
        double maximumGap = period * MAXIMUM_PAIR_BEATS;
        for (int later = 1; later < candidates.size(); later++) {
            Candidate right = candidates.get(later);
            for (int earlier = later - 1; earlier >= 0; earlier--) {
                Candidate left = candidates.get(earlier);
                double difference = right.timeMillis() - left.timeMillis();
                if (difference > maximumGap) break;
                int beatSteps = Math.max(1, (int) Math.round(difference / period));
                if (beatSteps > MAXIMUM_PAIR_BEATS) continue;
                double error = Math.abs(difference - beatSteps * period);
                double tolerance = Math.min(64.0, 30.0 + beatSteps * 4.0);
                double weight = Math.sqrt(left.weight() * right.weight())
                        / Math.sqrt(beatSteps);
                total += weight;
                aligned += weight * gaussian(error / tolerance);
            }
        }
        return new PairScore(aligned, total);
    }

    private static double adjacentStepQuality(List<Candidate> candidates,
                                              double period) {
        List<Candidate> reliable = candidates.stream().filter(Candidate::anchor).toList();
        if (reliable.size() < 2) reliable = candidates;
        double aligned = 0.0;
        double total = 0.0;
        for (int index = 1; index < reliable.size(); index++) {
            Candidate before = reliable.get(index - 1);
            Candidate after = reliable.get(index);
            double difference = after.timeMillis() - before.timeMillis();
            int beatSteps = Math.max(1, (int) Math.round(difference / period));
            if (beatSteps > MAXIMUM_PAIR_BEATS) continue;
            double error = Math.abs(difference - beatSteps * period);
            double tolerance = Math.min(64.0, 30.0 + beatSteps * 4.0);
            double weight = Math.sqrt(before.weight() * after.weight());
            total += weight;
            aligned += weight * gaussian(error / tolerance) / Math.sqrt(beatSteps);
        }
        return total == 0.0 ? 0.0 : aligned / total;
    }

    private static double tempoPrior(double periodMillis) {
        double logarithmicDistance = Math.log(periodMillis / 500.0)
                / Math.log(1.75);
        return gaussian(logarithmicDistance);
    }

    private static PhaseEstimate estimatePhase(List<Candidate> candidates,
                                               double period) {
        double totalWeight = candidates.stream().mapToDouble(Candidate::weight).sum();
        double sigma = Math.clamp(period * 0.09, 32.0, 58.0);
        double bestPhase = 0.0;
        double bestAlignment = 0.0;
        for (double phase = 0.0; phase < period; phase += PERIOD_STEP_MILLIS) {
            double alignment = 0.0;
            for (Candidate candidate : candidates) {
                double distance = distanceToGrid(candidate.timeMillis(), phase, period);
                alignment += candidate.weight() * gaussian(distance / sigma);
            }
            alignment /= Math.max(1.0E-9, totalWeight);
            if (alignment > bestAlignment) {
                bestAlignment = alignment;
                bestPhase = phase;
            }
        }
        return new PhaseEstimate(bestPhase, bestAlignment);
    }

    private static SlotSummary summarizeSlots(List<Candidate> candidates,
                                              double phase, double period) {
        long first = candidates.getFirst().timeMillis();
        long last = candidates.getLast().timeMillis();
        long firstSlot = (long) Math.ceil((first - phase) / period);
        long lastSlot = (long) Math.floor((last - phase) / period);
        double tolerance = searchTolerance(period);
        int slots = 0;
        int matched = 0;
        List<Double> matchedStrengths = new ArrayList<>();
        for (long slot = firstSlot; slot <= lastSlot; slot++) {
            slots++;
            Candidate nearest = bestNear(candidates,
                    phase + slot * period, tolerance);
            if (nearest != null) {
                matched++;
                matchedStrengths.add(nearest.pulse().strength());
            }
        }
        double occupancy = slots == 0 ? 0.0 : (double) matched / slots;
        matchedStrengths.sort(Double::compareTo);
        double medianStrength = matchedStrengths.isEmpty() ? 0.0
                : matchedStrengths.get(matchedStrengths.size() / 2);
        return new SlotSummary(matched, occupancy, medianStrength);
    }

    private static List<RhythmPulse> renderGrid(List<Candidate> candidates, Fit fit,
                                                long durationMillis) {
        long first = candidates.getFirst().timeMillis();
        long last = candidates.getLast().timeMillis();
        double period = fit.periodMillis();
        double tolerance = searchTolerance(period);
        long firstSlot = (long) Math.floor((first - fit.phaseMillis()) / period) - 1L;
        long lastSlot = (long) Math.ceil((last - fit.phaseMillis()) / period) + 1L;
        List<GridSlot> slots = new ArrayList<>();
        for (long slot = firstSlot; slot <= lastSlot; slot++) {
            double expected = fit.phaseMillis() + slot * period;
            if (expected < 0.0 || expected > durationMillis) continue;
            Candidate candidate = bestNear(candidates, expected, tolerance);
            slots.add(new GridSlot(Math.round(expected), candidate));
        }

        List<RhythmPulse> pulses = new ArrayList<>();
        for (int index = 0; index < slots.size(); index++) {
            GridSlot slot = slots.get(index);
            if (slot.candidate() != null) {
                pulses.add(gridPulse(slot.candidate(), slot.expectedMillis(), period));
                continue;
            }
            if (index == 0 || index + 1 >= slots.size()) continue;
            Candidate before = slots.get(index - 1).candidate();
            Candidate after = slots.get(index + 1).candidate();
            if (before != null && after != null) {
                pulses.add(inferredPulse(slot.expectedMillis(), before.pulse(),
                        after.pulse()));
            }
        }
        return pulses;
    }

    private static RhythmPulse gridPulse(Candidate candidate, long expectedMillis,
                                         double period) {
        RhythmPulse pulse = candidate.pulse();
        double closeness = 1.0 - Math.min(1.0,
                Math.abs(pulse.timeMillis() - expectedMillis)
                        / searchTolerance(period));
        double strength = Math.clamp(pulse.strength() * 0.88
                + closeness * 0.12, 0.12, 1.0);
        return new RhythmPulse(pulse.timeMillis(), strength, pulse.stereoBalance(),
                pulse.toneBalance(), pulse.signature());
    }

    private static RhythmPulse inferredPulse(long timeMillis, RhythmPulse before,
                                              RhythmPulse after) {
        double strength = Math.clamp(Math.min(before.strength(), after.strength())
                * 0.62, 0.16, 0.55);
        double stereo = (before.stereoBalance() + after.stereoBalance()) * 0.5;
        double tone = (before.toneBalance() + after.toneBalance()) * 0.5;
        long signature = Long.rotateLeft(before.signature(), 19)
                ^ after.signature() ^ timeMillis * 0x9E3779B97F4A7C15L;
        return new RhythmPulse(timeMillis, strength, stereo, tone, signature);
    }

    private static void retainStrongOffGridAccents(List<RhythmPulse> output,
                                                   List<Candidate> candidates,
                                                   double period) {
        long separation = Math.round(Math.clamp(period * 0.20,
                MINIMUM_OUTPUT_INTERVAL_MILLIS, 160.0));
        for (Candidate candidate : candidates) {
            double requiredStrength = candidate.anchor() ? 0.48 : 0.52;
            if (candidate.pulse().strength() < requiredStrength) continue;
            boolean nearGridBeat = output.stream().anyMatch(pulse -> Math.abs(
                    pulse.timeMillis() - candidate.timeMillis()) < separation);
            if (!nearGridBeat) output.add(candidate.pulse());
        }
    }

    /** Safe non-periodic fallback for short, syncopated, or weakly anchored regions. */
    private static List<RhythmPulse> fallback(List<Candidate> candidates) {
        if (candidates.isEmpty()) return List.of();
        boolean hasAnchor = candidates.stream().anyMatch(Candidate::anchor);
        double maximumStrength = candidates.stream().mapToDouble(candidate ->
                candidate.pulse().strength()).max().orElse(0.0);
        if (!hasAnchor && maximumStrength < STRONG_CANDIDATE_FALLBACK) {
            return List.of();
        }
        double candidateThreshold = hasAnchor
                ? 0.30 : Math.max(0.30, maximumStrength * 0.62);
        return candidates.stream()
                .filter(candidate -> candidate.anchor()
                        || candidate.pulse().strength() >= candidateThreshold)
                .map(Candidate::pulse)
                .toList();
    }

    private static Candidate bestNear(List<Candidate> candidates, double expected,
                                      double tolerance) {
        Candidate best = null;
        double bestScore = -1.0;
        for (Candidate candidate : candidates) {
            double distance = Math.abs(candidate.timeMillis() - expected);
            if (distance > tolerance) continue;
            double score = candidate.weight() * gaussian(distance / tolerance);
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private static double searchTolerance(double period) {
        return Math.clamp(period * 0.17, 28.0, 105.0);
    }

    private static double distanceToGrid(long timeMillis, double phase,
                                         double period) {
        double quotient = Math.rint((timeMillis - phase) / period);
        return Math.abs(timeMillis - (phase + quotient * period));
    }

    private static double gaussian(double normalizedDistance) {
        return Math.exp(-0.5 * normalizedDistance * normalizedDistance);
    }

    private static List<List<Candidate>> activeGroups(List<Candidate> candidates) {
        List<List<Candidate>> groups = new ArrayList<>();
        int start = 0;
        for (int index = 1; index < candidates.size(); index++) {
            if (candidates.get(index).timeMillis()
                    - candidates.get(index - 1).timeMillis() > ACTIVE_GAP_MILLIS) {
                groups.add(List.copyOf(candidates.subList(start, index)));
                start = index;
            }
        }
        groups.add(List.copyOf(candidates.subList(start, candidates.size())));
        return groups;
    }

    private static List<List<Candidate>> chunks(List<Candidate> group) {
        if (group.size() < MINIMUM_MATCHED_BEATS * 2
                || group.getLast().timeMillis() - group.getFirst().timeMillis()
                <= MAXIMUM_CHUNK_MILLIS) {
            return List.of(group);
        }
        List<List<Candidate>> chunks = new ArrayList<>();
        int start = 0;
        while (group.size() - start >= MINIMUM_MATCHED_BEATS) {
            long span = group.getLast().timeMillis() - group.get(start).timeMillis();
            if (span <= MAXIMUM_CHUNK_MILLIS) {
                chunks.add(List.copyOf(group.subList(start, group.size())));
                start = group.size();
                break;
            }
            int boundary = chooseBoundary(group, start);
            chunks.add(List.copyOf(group.subList(start, boundary)));
            start = boundary;
        }
        if (start < group.size()) {
            List<Candidate> tail = List.copyOf(group.subList(start, group.size()));
            if (chunks.isEmpty()) chunks.add(tail);
            else {
                List<Candidate> merged = new ArrayList<>(chunks.removeLast());
                merged.addAll(tail);
                chunks.add(List.copyOf(merged));
            }
        }
        return List.copyOf(chunks);
    }

    private static int chooseBoundary(List<Candidate> group, int start) {
        long target = group.get(start).timeMillis() + TARGET_CHUNK_MILLIS;
        int latest = group.size() - MINIMUM_MATCHED_BEATS;
        int best = Math.min(start + MINIMUM_MATCHED_BEATS, latest);
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int index = start + MINIMUM_MATCHED_BEATS; index <= latest; index++) {
            long time = group.get(index).timeMillis();
            long distance = Math.abs(time - target);
            if (distance > 3_000L) continue;
            long gap = time - group.get(index - 1).timeMillis();
            double score = gap - distance * 0.08;
            if (score > bestScore) {
                bestScore = score;
                best = index;
            }
        }
        if (bestScore == Double.NEGATIVE_INFINITY) {
            for (int index = start + MINIMUM_MATCHED_BEATS; index <= latest; index++) {
                if (group.get(index).timeMillis() >= target) return index;
            }
        }
        return best;
    }

    private static List<Candidate> merge(List<RhythmPulse> rawCandidates,
                                         List<RhythmPulse> anchors,
                                         long durationMillis) {
        List<Candidate> all = new ArrayList<>();
        valid(rawCandidates, durationMillis).forEach(pulse ->
                all.add(new Candidate(pulse, false)));
        valid(anchors, durationMillis).forEach(pulse ->
                all.add(new Candidate(pulse, true)));
        all.sort(Comparator.comparingLong(Candidate::timeMillis)
                .thenComparing(Candidate::anchor, Comparator.reverseOrder()));

        List<Candidate> merged = new ArrayList<>();
        int index = 0;
        while (index < all.size()) {
            int end = index + 1;
            long clusterStart = all.get(index).timeMillis();
            Candidate best = all.get(index);
            boolean anchored = best.anchor();
            while (end < all.size()
                    && all.get(end).timeMillis() - clusterStart <= MERGE_WINDOW_MILLIS) {
                Candidate candidate = all.get(end);
                anchored |= candidate.anchor();
                if (candidate.weight() > best.weight()) best = candidate;
                end++;
            }
            merged.add(new Candidate(best.pulse(), anchored));
            index = end;
        }
        return List.copyOf(merged);
    }

    private static List<RhythmPulse> valid(List<RhythmPulse> pulses,
                                           long durationMillis) {
        if (pulses == null || pulses.isEmpty()) return List.of();
        return pulses.stream().filter(java.util.Objects::nonNull)
                .filter(pulse -> pulse.timeMillis() <= durationMillis)
                .sorted(Comparator.comparingLong(RhythmPulse::timeMillis))
                .toList();
    }

    private static List<RhythmPulse> suppressNearby(List<RhythmPulse> pulses) {
        List<RhythmPulse> priority = new ArrayList<>(pulses);
        priority.sort(Comparator.comparingDouble(RhythmPulse::strength).reversed()
                .thenComparingLong(RhythmPulse::timeMillis));
        java.util.NavigableSet<Long> times = new java.util.TreeSet<>();
        List<RhythmPulse> selected = new ArrayList<>();
        for (RhythmPulse pulse : priority) {
            Long before = times.floor(pulse.timeMillis());
            if (before != null && pulse.timeMillis() - before
                    < MINIMUM_OUTPUT_INTERVAL_MILLIS) continue;
            Long after = times.ceiling(pulse.timeMillis());
            if (after != null && after - pulse.timeMillis()
                    < MINIMUM_OUTPUT_INTERVAL_MILLIS) continue;
            times.add(pulse.timeMillis());
            selected.add(pulse);
        }
        selected.sort(Comparator.comparingLong(RhythmPulse::timeMillis));
        return List.copyOf(selected);
    }

    private record Candidate(RhythmPulse pulse, boolean anchor) {
        private long timeMillis() {
            return pulse.timeMillis();
        }

        private double weight() {
            return 0.20 + pulse.strength() * pulse.strength()
                    + (anchor ? 0.85 : 0.0);
        }
    }

    private record PairScore(double alignedWeight, double weight) {
    }

    private record PeriodEstimate(double periodMillis, double coherence) {
    }

    private record TempoCandidate(double periodMillis, double coherence,
                                  double score) {
    }

    private record PhaseEstimate(double phaseMillis, double alignment) {
    }

    private record SlotSummary(int matched, double occupancy,
                               double medianStrength) {
    }

    private record GridSlot(long expectedMillis, Candidate candidate) {
    }

    private record Fit(double periodMillis, double phaseMillis,
                       double confidence, boolean confident) {
        private static Fit rejected() {
            return new Fit(500.0, 0.0, 0.0, false);
        }
    }
}
