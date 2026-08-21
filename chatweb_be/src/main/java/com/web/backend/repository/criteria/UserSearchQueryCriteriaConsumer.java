package com.web.backend.repository.criteria;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.function.Consumer;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserSearchQueryCriteriaConsumer implements Consumer<SearchCriteria> {

    private Predicate predicate;
    private CriteriaBuilder builder;
    private Root<?> root;

    private static final String OP_GREATER_STRING = ">";
    private static final String OP_LESS_STRING = "<";
    private static final String OP_COLON_STRING = ":";
    private static final String PERCENT_STRING = "%";

    @Override
    public void accept(SearchCriteria param) {
        if (param.getOperation().equalsIgnoreCase(OP_GREATER_STRING)) {
            predicate = builder.and(predicate, builder
                    .greaterThanOrEqualTo(root.<String>get(param.getKey()), param.getValue().toString()));
        } else if (param.getOperation().equalsIgnoreCase(OP_LESS_STRING)) {
            predicate = builder.and(predicate, builder.lessThanOrEqualTo(
                    root.<String>get(param.getKey()), param.getValue().toString()));
        } else if (param.getOperation().equalsIgnoreCase(OP_COLON_STRING)) {
            if (root.get(param.getKey()).getJavaType() == String.class) {
                predicate = builder.and(predicate, builder.like(
                        root.<String>get(param.getKey()), PERCENT_STRING + param.getValue() + PERCENT_STRING));
            } else {
                predicate = builder.and(predicate, builder.equal(
                        root.get(param.getKey()), param.getValue()));
            }
        }
    }
}
