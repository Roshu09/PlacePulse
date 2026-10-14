package com.placepulse.drive.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Stores the eligible pass-out years in one column, like "2026,2027". An empty set means every batch. */
@Converter
public class BatchSetConverter implements AttributeConverter<Set<Integer>, String> {

    @Override
    public String convertToDatabaseColumn(Set<Integer> years) {
        if (years == null || years.isEmpty()) {
            return null;
        }
        return new TreeSet<>(years).stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    @Override
    public Set<Integer> convertToEntityAttribute(String column) {
        if (column == null || column.isBlank()) {
            return new TreeSet<>();
        }
        return Arrays.stream(column.split(","))
                .map(String::trim)
                .filter(s -> s.matches("\\d{4}"))
                .map(Integer::valueOf)
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
