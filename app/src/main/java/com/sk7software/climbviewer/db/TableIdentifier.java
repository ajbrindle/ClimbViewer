package com.sk7software.climbviewer.db;

public enum TableIdentifier {
    CLIMB("C"),
    CLIMB_POINT("CP"),
    CLIMB_ATTEMPT("CA"),
    CLIMB_ATTEMPT_POINT("CAP");

    private final String abbrev;

    TableIdentifier(String abbrev) {
        this.abbrev = abbrev;
    }

    public String getAbbrev() {
        return abbrev;
    }

    public static TableIdentifier getEnumFromAbbrev(String abbrev) {
        for (TableIdentifier i : values()) {
            if (abbrev.equals(i.getAbbrev())) {
                return i;
            }
        }
        return null;
    }

    public static String getAbbrev(String tableName) {
        TableIdentifier i = TableIdentifier.valueOf(tableName);
        return i.getAbbrev();
    }
}
