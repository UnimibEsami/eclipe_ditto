/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.placeholders;


import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;

import org.eclipse.ditto.model.base.common.Placeholders;

/**
 * The ExpressionResolver is able to:
 * <ul>
 * <li>resolve {@link Placeholder}s in a passed {@code template} (based on {@link PlaceholderResolver}</li>
 * <li>execute optional pipeline stages in a passed {@code template}</li>
 * </ul>
 * As a result, a resolved String is returned.
 * For example, following expressions can be resolved:
 * <ul>
 * <li>{@code {{ thing:id }} }</li>
 * <li>{@code {{ header:device_id }} }</li>
 * <li>{@code {{ topic:full }} }</li>
 * <li>{@code {{ thing:name | fn:substring-before(':') | fn:default(thing:name) }} }</li>
 * <li>{@code {{ header:unknown | fn:default('fallback') }} }</li>
 * </ul>
 */
public interface ExpressionResolver {

    /**
     * Resolve a single pipeline expression.
     *
     * @param pipelineExpression the pipeline expression.
     * @return the pipeline element after evaluation.
     */
    PipelineElement resolveAsPipelineElement(String pipelineExpression);

    /**
     * Resolves a complete expression template starting with a {@link Placeholder} followed by optional pipeline stages
     * (e.g. functions).
     *
     * @param expressionTemplate the expressionTemplate to resolve {@link org.eclipse.ditto.model.placeholders.Placeholder}s and and execute optional
     * pipeline stages
     * @param allowUnresolved whether unresolved placeholder expressions are allowed to remain in the result.
     * @return the resolved String, a signifier for resolution failure, or one for deletion.
     * @throws PlaceholderFunctionTooComplexException thrown if the {@code expressionTemplate} contains a placeholder
     * function chain which is too complex (e.g. too much chained function calls)
     */
    default PipelineElement resolve(String expressionTemplate, final boolean allowUnresolved) {
        return ExpressionResolver.substitute(expressionTemplate, allowUnresolved, this::resolveAsPipelineElement);
    }

    /**
     * Perform simple substitution on a string based on a template function.
     *
     * @param input the input string.
     * @param allowUnresolved whether unresolved placeholders are allowed.
     * @param substitutionFunction the substitution function turning the content of each placeholder into a result.
     * @return the substitution result.
     */
    static PipelineElement substitute(
            final String input,
            final boolean allowUnresolved,
            final Function<String, PipelineElement> substitutionFunction) {

        final Matcher matcher = Placeholders.pattern().matcher(input);
        final StringBuffer resultBuilder = new StringBuffer();

        while (matcher.find()) {
            final String placeholderExpression = Placeholders.groupNames()
                    .stream()
                    .map(matcher::group)
                    .filter(Objects::nonNull)
                    .findAny()
                    .orElse("");
            final PipelineElement element = substitutionFunction.apply(placeholderExpression);
            switch (element.getType()) {
                case DELETED:
                    // abort pipeline execution: the string has been deleted.
                    return element;
                case UNRESOLVED:
                    // abort pipeline execution: resolution failed where unresolved placeholders are forbidden.
                    if (!allowUnresolved) {
                        return element;
                    }
            }
            // append resolved placeholder
            element.map(resolvedValue -> {
                // increment counter inside matcher for "matcher.appendTail" later
                matcher.appendReplacement(resultBuilder, "");
                // actually append resolved value - do not attempt to interpret as regex
                resultBuilder.append(resolvedValue);
                return resolvedValue;
            });
        }

        matcher.appendTail(resultBuilder);
        return PipelineElement.resolved(resultBuilder.toString());

    }

}
