/**
 * Categorizes the administrative and structural levels of a campus.
 *
 * Used by {@link LocationNode} to enforce hierarchical semantics across
 * the {@link CampusTree} (e.g. CAMPUS -> BUILDING -> FLOOR -> ROOM / LAB).
 */
public enum LocationType {

    /** Topmost root representing the entire educational campus. */
    CAMPUS,

    /** Distinct building or block on campus (e.g., B Building, A Building). */
    BUILDING,

    /** Specific floor level within a building (e.g., Ground Floor, First Floor). */
    FLOOR,

    /** General classroom, lecture hall, or administrative office. */
    ROOM,

    /** Practical laboratory (e.g., Operating Systems Lab, Computer Network Lab). */
    LAB
}

