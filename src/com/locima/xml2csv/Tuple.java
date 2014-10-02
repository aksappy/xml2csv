package com.locima.xml2csv;

import java.io.Serializable;

/**
 * Represents a simple immutable tuple.
 * @param <T> the type of the first member of the tuple.
 * @param <U> the type of the second member of the tuple.
 */
public class Tuple<T extends Serializable, U> {

    private final T first;
    private final U second;

    /**
     * Constructs a new instance with the values provided.
     * @param firstValue first value of the tuple (may be null).
     * @param secondValue second value of the tuple (may be null).
     */
    public Tuple(T firstValue, U secondValue) {
        this.first = firstValue;
        this.second = secondValue;
    }

    /**
     * Get the first value of the tuple.
     * @return the first value of the tuple (may be null).
     */
    public T getFirst() {
        return this.first;
    }

    /**
     * Get the second value of the tuple.
     * @return the second value of the tuple (may be null).
     */
    public U getSecond() {
        return this.second;
    }
}