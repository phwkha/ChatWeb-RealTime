package com.web.backend.repository.specification;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import com.web.backend.model.postgres.AddressEntity;
import com.web.backend.model.postgres.UserEntity;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AddressSpecification implements Specification<UserEntity> {

    private transient List<SpecSearchCriteria> criteriaList;

    private static final String ADDRESSES_STRING = "addresses";
    private static final String PERCENT_STRING = "%";

    private static final Set<String> ALLOWED_ADDRESS_FIELDS = Set.of(
            "houseNumber", "street", "ward", "district", "city", "country", "postalCode"
    );

    @Override
    public Predicate toPredicate(@NonNull final Root<UserEntity> root, @Nullable final CriteriaQuery<?> query,
            @NonNull final CriteriaBuilder builder) {

        Predicate finalPredicate = null;
        Join<UserEntity, AddressEntity> addressJoin = null;

        for (SpecSearchCriteria criteria : criteriaList) {
            if (!ALLOWED_ADDRESS_FIELDS.contains(criteria.getKey())) {
                continue;
            }

            if (addressJoin == null) {
                addressJoin = root.join(ADDRESSES_STRING, JoinType.INNER);
            }

            Predicate currentPredicate = buildPredicate(addressJoin, criteria, builder);
            if (currentPredicate != null) {
                finalPredicate = combinePredicates(builder, finalPredicate, currentPredicate, criteria.isOrPredicate());
            }
        }

        return finalPredicate != null ? finalPredicate : builder.conjunction();
    }

    private Predicate buildPredicate(Path<AddressEntity> path, SpecSearchCriteria criteria, CriteriaBuilder builder) {
        Path<?> fieldPath;
        try {
            fieldPath = path.get(criteria.getKey());
        } catch (IllegalArgumentException e) {
            return null;
        }

        Object value = resolveValue(fieldPath.getJavaType(), criteria.getValue(), builder);
        if (value instanceof Predicate predicateValue) {
            return predicateValue;
        }

        return createOperationPredicate(fieldPath, criteria.getOperation(), value, builder);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Object resolveValue(Class<?> javaType, Object rawValue, CriteriaBuilder builder) {
        if (javaType.isEnum() && rawValue instanceof String stringVal) {
            try {
                return Enum.valueOf((Class<Enum>) javaType, stringVal.toUpperCase());
            } catch (IllegalArgumentException e) {
                return builder.disjunction();
            }
        }
        return rawValue;
    }

    private Predicate createOperationPredicate(Path<?> path, SearchOperation operation, Object value, CriteriaBuilder builder) {
        String strVal = value != null ? value.toString().toLowerCase() : "";
        return switch (operation) {
            case EQUALITY -> builder.equal(path, value);
            case NEGATION -> builder.notEqual(path, value);
            case GREATER_THAN -> builder.greaterThan(builder.lower(path.as(String.class)), strVal);
            case LESS_THAN -> builder.lessThan(builder.lower(path.as(String.class)), strVal);
            case LIKE, CONTAINS -> builder.like(builder.lower(path.as(String.class)), PERCENT_STRING + strVal + PERCENT_STRING);
            case STARTS_WITH -> builder.like(builder.lower(path.as(String.class)), strVal + PERCENT_STRING);
            case ENDS_WITH -> builder.like(builder.lower(path.as(String.class)), PERCENT_STRING + strVal);
        };
    }

    private Predicate combinePredicates(CriteriaBuilder builder, Predicate current, Predicate next, boolean isOr) {
        if (current == null) {
            return next;
        }
        return isOr ? builder.or(current, next) : builder.and(current, next);
    }
}