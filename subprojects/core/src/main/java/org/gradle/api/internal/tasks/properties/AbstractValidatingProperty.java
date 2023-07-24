/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.tasks.properties;

import com.google.common.base.Suppliers;
import org.gradle.api.provider.HasConfigurableValue;
import org.gradle.api.provider.Provider;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.reflect.problems.ValidationProblemId;
import org.gradle.internal.reflect.validation.Severity;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.util.internal.DeferredUtil;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public abstract class AbstractValidatingProperty implements ValidatingProperty {
    private final String propertyName;
    private final PropertyValue value;
    private final boolean optional;
    private final ValidationAction validationAction;

    public AbstractValidatingProperty(String propertyName, PropertyValue value, boolean optional, ValidationAction validationAction) {
        this.propertyName = propertyName;
        this.value = value;
        this.optional = optional;
        this.validationAction = validationAction;
    }

    public static void reportValueNotSet(String propertyName, TypeValidationContext context, boolean hasConfigurableValue) {
        context.visitPropertyProblem(problem -> {
            problem.withId(ValidationProblemId.VALUE_NOT_SET)
                .reportAs(Severity.ERROR)
                .forProperty(propertyName)
                .withDescription("doesn't have a configured value")
                .happensBecause("This property isn't marked as optional and no value has been configured")
                .documentedAt("validation_problems", "value_not_set");
            if (hasConfigurableValue) {
                problem.addPossibleSolution(() -> "Assign a value to '" + propertyName + "'");
            } else {
                problem.addPossibleSolution(() -> "The value of '" + propertyName + "' is calculated, make sure a valid value can be calculated");
            }
            problem.addPossibleSolution(() -> "Mark property '" + propertyName + "' as optional");
        });
    }

    @Override
    public void validate(PropertyValidationContext context) {
        // unnest callables without resolving deferred values (providers, factories)
        Object unnested = DeferredUtil.unpackNestableDeferred(value.call());
        if (isPresent(unnested)) {
            // only resolve deferred values if actually required by some action
            Supplier<Object> valueSupplier = Suppliers.memoize(() -> DeferredUtil.unpack(unnested));
            validationAction.validate(propertyName, valueSupplier, context);
        } else {
            if (!optional) {
                reportValueNotSet(propertyName, context, hasConfigurableValue(unnested));
            }
        }
    }

    private static boolean isPresent(@Nullable Object value) {
        if (value instanceof Provider) {
            // carefully check for presence without necessarily resolving
            return ((Provider<?>) value).isPresent();
        }
        return value != null;
    }

    private static boolean hasConfigurableValue(@Nullable Object value) {
        return value == null || HasConfigurableValue.class.isAssignableFrom(value.getClass());
    }

    @Override
    public void prepareValue() {
        value.maybeFinalizeValue();
    }

    @Override
    public void cleanupValue() {
    }
}
