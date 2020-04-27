/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.placeholders.filter;

import java.util.Arrays;
import java.util.Optional;

/**
 * Factory for supported filter functions.
 */
public enum FilterFunctions implements FilterFunction{

    /**
     * The {@link EqFunction} keeps the value if both passed parameters are equal to each other.
     */
    EQ(new EqFunction()),

    /**
     * The {@link NeFunction} keeps the value if both passed parameters are not equal to each other.
     */
    NE(new NeFunction());

    private final FilterFunction rqlFunction;

    FilterFunctions(final FilterFunction rqlFunction) {
        this.rqlFunction = rqlFunction;
    }

    /**
     * Looks up a {@link FilterFunction} based on the provided name which hast to equal a name returned by
     * {@link FilterFunction#getName()} of an implementation of {@link FilterFunction}.
     *
     * @param functionName the name of the function.
     * @return An optional holding an implementation of {@link FilterFunction} for which
     * {@link FilterFunction#getName()} is equal to the given functionName.
     */
    public static Optional<FilterFunction> fromName(final String functionName) {
        return Arrays.stream(FilterFunctions.values())
                .filter(rqlFunctions -> rqlFunctions.rqlFunction.getName().equals(functionName))
                .map(filterFunctions -> (FilterFunction) filterFunctions)
                .findAny();
    }

    @Override
    public String getName() {
        return rqlFunction.getName();
    }

    @Override
    public boolean apply(final String... parameters) {
        return rqlFunction.apply(parameters);
    }

}
