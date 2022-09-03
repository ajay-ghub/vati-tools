package com.ajay.bio.enums;

public enum FileType {
    AB1(".ab1"),
    FASTA(".fasta");

    public String getTypeString() {
        return typeString;
    }

    private final String typeString;

    FileType(final String typeString) {
        this.typeString = typeString;
    }
}
