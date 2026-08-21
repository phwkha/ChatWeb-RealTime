package com.web.backend.model.postgres;

import com.web.backend.common.AuthProvider;
import com.web.backend.common.GenderType;
import com.web.backend.common.UserStatus;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.*;
import java.time.LocalDate;

@Entity
@Table(name = "users")
@Getter
@Setter
public class UserEntity extends AbstractEntity<Long> implements UserDetails {

    @Column(name = "auth_provider")
    @Enumerated(EnumType.STRING)
    private AuthProvider authProvider;

    @Column(unique = true)
    private String providerId;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(unique = true)
    private String email;

    @Column
    private String phone;

    @Column(name = "is_online")
    private boolean isOnline;

    @Column(name = "user_status")
    @Enumerated(EnumType.STRING)
    private UserStatus userStatus;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private RoleEntity role;

    @Column(columnDefinition = "TEXT", name = "public_key")
    private String publicKey;

    @Column(columnDefinition = "TEXT", name = "encrypted_rsa_private_key")
    private String encryptedRsaPrivateKey;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column
    private String avatar;

    @Column
    private LocalDate birthday;

    @Enumerated(EnumType.STRING)
    private GenderType gender;

    @Column(name = "token_version", columnDefinition = "integer default 0")
    private Integer tokenVersion = 0;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<AddressEntity> addresses = new ArrayList<>();

    public void addAddress(AddressEntity address) {
        this.addresses.add(address);
        address.setUser(this);
    }

    public void removeAddress(AddressEntity address) {
        this.addresses.remove(address);
        address.setUser(null);
    }

    private static final String ROLE_PREFIX_STRING = "ROLE_";

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<SimpleGrantedAuthority> authorities = new HashSet<>();
        if (this.role != null) {
            if (this.role.getName() != null) {
                authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX_STRING + this.role.getName().toUpperCase()));
            }
            for (PermissionEntity permission : this.role.getPermissions()) {
                authorities.add(new SimpleGrantedAuthority(permission.getName()));
            }
        }
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return !UserStatus.LOCKED.equals(userStatus);
    }

    @Override
    public boolean isEnabled() {
        return UserStatus.ACTIVE.equals(userStatus);
    }
}