package com.cloudmart.pet.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** {@link InEnum} 校验器：按枚举常量名匹配（大小写敏感，契约码一律大写下划线）。 */
public class InEnumValidator implements ConstraintValidator<InEnum, String> {

    private Set<String> acceptedNames;

    @Override
    public void initialize(InEnum annotation) {
        this.acceptedNames = Arrays.stream(annotation.value().getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || acceptedNames.contains(value);
    }
}
