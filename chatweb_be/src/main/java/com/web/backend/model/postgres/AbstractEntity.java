package com.web.backend.model.postgres;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.Instant;

@Getter
@Setter
@MappedSuperclass
public abstract class AbstractEntity<T extends Serializable> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private T id;

    @Column(name = "create_at")
    @CreationTimestamp
    private Instant createAt;

    @Column(name = "update_at")
    @UpdateTimestamp
    private Instant updateAt;
}
