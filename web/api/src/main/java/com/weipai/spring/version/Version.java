package com.weipai.spring.version;

public class Version implements Comparable<Version> {
    public static final String MAX_VERSION = "9999.12.31";
    public static final String MIN_VERSION = "1000.01.01";

    private final int year;
    private final int month;
    private final int day;

    public Version(String version) {
        String tokens[] = version.split("\\.");

        if (tokens.length != 3) {
            throw new IllegalArgumentException("Invalid version " + version + ". The version must have major and minor number.");
        }

        year = Integer.parseInt(tokens[0]);
        month = Integer.parseInt(tokens[1]);
        day = Integer.parseInt(tokens[2]);
    }

    @Override
    public int compareTo(Version other) {
        if (this.year > other.year) {
            return 1;
        } else if (this.year < other.year) {
            return -1;
        } else if (this.month > other.month) {
            return 1;
        } else if (this.month < other.month) {
            return -1;
        } else if (this.day > other.day) {
            return 1;
        } else if (this.day < other.day) {
            return -1;
        } else {
            return 0;
        }
    }

    @Override
    public String toString() {
        return "v" + year + "." + month+ "." + day;
    }
}
