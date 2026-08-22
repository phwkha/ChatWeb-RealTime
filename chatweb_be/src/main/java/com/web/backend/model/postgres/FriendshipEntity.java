package com.web.backend.model.postgres;

import com.web.backend.common.FriendshipStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "friendships",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = { "requester_id", "addressee_id" })
        },
        indexes = {
                @Index(name = "idx_friendship_requester_status", columnList = "requester_id, status"),
                @Index(name = "idx_friendship_addressee_status", columnList = "addressee_id, status")
        })
@Getter
@Setter
public class FriendshipEntity extends AbstractEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private UserEntity requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "addressee_id", nullable = false)
    private UserEntity addressee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FriendshipStatus status;
}