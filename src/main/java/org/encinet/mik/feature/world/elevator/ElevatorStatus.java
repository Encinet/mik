package org.encinet.mik.feature.world.elevator;

/** Read-only elevator state. A null direction is unconfirmed; false means no safe destination was found. */
public record ElevatorStatus(boolean onIron, Boolean canUp, Boolean canDown) { }
