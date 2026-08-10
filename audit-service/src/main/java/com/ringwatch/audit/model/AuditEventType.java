package com.ringwatch.audit.model;

/**
 * FR18 lists all four of these as required audit inputs. {@link #OVERRIDDEN} has no producer yet
 * - the analyst override endpoint (FR22) is Phase 5 dashboard work - so it's modeled now, ahead of
 * its writer, the same way {@code Decision.overriddenBy}/{@code overrideReason} already sit
 * unused in decision-engine pending the same future endpoint.
 */
public enum AuditEventType {
    CREATED,
    SCORED,
    DECIDED,
    OVERRIDDEN,
    RECONCILED
}
