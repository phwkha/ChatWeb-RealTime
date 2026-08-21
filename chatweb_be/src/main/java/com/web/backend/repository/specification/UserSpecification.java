package com.web.backend.repository.specification;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import com.web.backend.model.postgres.UserEntity;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserSpecification implements Specification<UserEntity> {

    private transient List<SpecSearchCriteria> criteriaList;

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Predicate toPredicate(@NonNull final Root<UserEntity> root, @Nullable final CriteriaQuery<?> query,
            @NonNull final CriteriaBuilder builder) {
        Predicate finalPredicate = null;
        for (SpecSearchCriteria criteria : criteriaList) {
            Class<?> javaType = root.get(criteria.getKey()).getJavaType();
            Object value = criteria.getValue();

            if (javaType.isEnum() && value instanceof String string) {
                try {
                    value = Enum.valueOf((Class<Enum>) javaType, (string).toUpperCase());
                } catch (IllegalArgumentException e) {
                    return builder.disjunction();
                }
            }

            Predicate currentPredicate = switch (criteria.getOperation()) {
                case EQUALITY -> builder.equal(root.get(criteria.getKey()), value);
                case NEGATION -> builder.notEqual(root.get(criteria.getKey()), value);
                case GREATER_THAN ->
                    builder.greaterThan(root.get(criteria.getKey()), value.toString().toLowerCase());
                case LESS_THAN -> builder.lessThan(root.get(criteria.getKey()), value.toString().toLowerCase());
                case LIKE ->
                    builder.like(root.get(criteria.getKey()), "%" + value.toString().toLowerCase() + "%");
                case STARTS_WITH -> builder.like(root.get(criteria.getKey()), value + "%");
                case ENDS_WITH -> builder.like(root.get(criteria.getKey()), "%" + value);
                case CONTAINS -> builder.like(root.get(criteria.getKey()), "%" + value + "%");
            };
            if (finalPredicate == null) {
                finalPredicate = currentPredicate;
            } else {
                if (criteria.isOrPredicate()) {
                    finalPredicate = builder.or(finalPredicate, currentPredicate);
                } else {
                    finalPredicate = builder.and(finalPredicate, currentPredicate);
                }
            }
        }
        return finalPredicate;
    }
}
