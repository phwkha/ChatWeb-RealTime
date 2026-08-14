package com.web.backend.config;

import com.web.backend.common.AuthProvider;
import com.web.backend.common.UserStatus;
import com.web.backend.model.PermissionEntity;
import com.web.backend.model.RoleEntity;
import com.web.backend.model.UserEntity;
import com.web.backend.repository.PermissionRepository;
import com.web.backend.repository.RoleRepository;
import com.web.backend.repository.UserRepository;
import com.web.backend.service.CuckooFilterService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import org.springframework.context.annotation.Profile;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j(topic = "DATABASE-SEEDER")
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;

    private final RoleRepository roleRepository;

    private final PermissionRepository permissionRepository;

    private final PasswordEncoder passwordEncoder;

    private final CuckooFilterService cuckooFilterService;

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.admin.default-password}")
    private String adminPassword;

    private static final String ADMIN_2_STRING = "admin";
    private static final String ADMIN_3_STRING = "Admin";
    private static final String ADMIN_EXAMPLE_COM_STRING = "admin@example.com";
    private static final String NG_I_D_NG_C_B_N_STRING = "Người dùng cơ bản";
    private static final String ONLINE_USERS_COUNT_STRING = "online_users_count";
    private static final String ONLINE_USERS_STRING = "online_users";
    private static final String QU_N_TR_VI_N_H_TH_NG_STRING = "Quản trị viên hệ thống";
    private static final String SUPER_STRING = "Super";
    private static final String USER_STRING = "USER";
    private static final String ADMIN_STRING = "ADMIN";

    private static final String FILTER_EMAILS_STRING = "filter:emails";
    private static final String FILTER_USERNAMES_STRING = "filter:usernames";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(String... args) throws Exception {
        log.info("Database seeding started...");
        // Admin permissions to match AdminController
        PermissionEntity pAdminView = createPermissionIfNotFound("ADMIN_VIEW", "Xem quyền quản trị");
        PermissionEntity pAdminCreate = createPermissionIfNotFound("ADMIN_CREATE", "Tạo quyền quản trị");
        PermissionEntity pAdminUpdate = createPermissionIfNotFound("ADMIN_UPDATE", "Sửa quyền quản trị");
        PermissionEntity pAdminDelete = createPermissionIfNotFound("ADMIN_DELETE", "Xóa quyền quản trị");
        PermissionEntity pAdminLock = createPermissionIfNotFound("ADMIN_LOCK", "Khóa user");
        PermissionEntity pAdminUnlock = createPermissionIfNotFound("ADMIN_UNLOCK", "Mở khóa user");
        PermissionEntity pAdminDeleteAvatar = createPermissionIfNotFound("ADMIN_DELETE_AVATAR", "Xóa avatar");

        // Role permissions
        PermissionEntity pRoleViewAll = createPermissionIfNotFound("ROLE_VIEW_ALL", "Xem danh sách role");
        PermissionEntity pRoleViewAllPermission = createPermissionIfNotFound("ROLE_VIEW_ALL_PERMISSION",
                "Xem tất cả quyền");
        PermissionEntity pRoleAdd = createPermissionIfNotFound("ROLE_ADD", "Thêm role");
        PermissionEntity pRoleUpdate = createPermissionIfNotFound("ROLE_UPDATE", "Cập nhật role");
        PermissionEntity pRoleDelete = createPermissionIfNotFound("ROLE_DELETE", "Xóa role");

        // Email permissions
        PermissionEntity pSendEmail = createPermissionIfNotFound("SEND_EMAIL", "Gửi email");

        // Chat permissions
        PermissionEntity pAdminSendMessage = createPermissionIfNotFound("ADMIN_SEND-MESSAGE", "Gửi tin nhắn hệ thống");

        RoleEntity roleAdmin = createRoleIfNotFound(ADMIN_STRING, QU_N_TR_VI_N_H_TH_NG_STRING);
        createRoleIfNotFound(USER_STRING, NG_I_D_NG_C_B_N_STRING);

        assignPermissionToRole(roleAdmin,
                pAdminView, pAdminCreate, pAdminUpdate, pAdminDelete, pAdminLock, pAdminUnlock, pAdminDeleteAvatar,
                pRoleViewAll, pRoleViewAllPermission, pRoleAdd, pRoleUpdate, pRoleDelete,
                pSendEmail, pAdminSendMessage);

        if (!userRepository.existsByUsername(ADMIN_2_STRING)) {
            UserEntity admin = new UserEntity();
            admin.setUsername(ADMIN_2_STRING);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setEmail(ADMIN_EXAMPLE_COM_STRING);
            admin.setUserStatus(UserStatus.ACTIVE);
            admin.setAuthProvider(AuthProvider.LOCAL);
            admin.setFirstName(SUPER_STRING);
            admin.setLastName(ADMIN_3_STRING);

            admin.setRole(roleAdmin);

            userRepository.save(admin);
        }
        log.info("Database seeding completed successfully");

        if (!Boolean.TRUE.equals(redisTemplate.hasKey(FILTER_EMAILS_STRING))) {
            log.info("Initializing Cuckoo filter in Redis...");
            List<UserEntity> allUsers = userRepository.findAll();
            for (UserEntity u : allUsers) {
                cuckooFilterService.add(FILTER_EMAILS_STRING, u.getEmail());
                cuckooFilterService.add(FILTER_USERNAMES_STRING, u.getUsername());
            }
            log.info("Cuckoo filter initialized with {} existing users", allUsers.size());
        }

    }

    private PermissionEntity createPermissionIfNotFound(String name, String desc) {
        return permissionRepository.findByName(name)
                .orElseGet(() -> {
                    PermissionEntity p = new PermissionEntity();
                    p.setName(name);
                    p.setDescription(desc);
                    return permissionRepository.save(p);
                });
    }

    private RoleEntity createRoleIfNotFound(String name, String desc) {
        return roleRepository.findByName(name)
                .orElseGet(() -> {
                    RoleEntity r = new RoleEntity();
                    r.setName(name);
                    r.setDescription(desc);
                    return roleRepository.save(r);
                });
    }

    private void assignPermissionToRole(RoleEntity role, PermissionEntity... permissions) {
        if (role == null)
            return;
        for (PermissionEntity p : permissions) {
            role.getPermissions().add(p);
        }
        roleRepository.save(role);
    }

    @Bean
    public CommandLineRunner cleanupOnlineStatus() {
        return args -> {
            String onlineUsersKey = ONLINE_USERS_STRING;
            String onlineUsersCountKey = ONLINE_USERS_COUNT_STRING;

            if (Boolean.TRUE.equals(redisTemplate.hasKey(onlineUsersKey))) {
                redisTemplate.delete(onlineUsersKey);
            }
            if (Boolean.TRUE.equals(redisTemplate.hasKey(onlineUsersCountKey))) {
                redisTemplate.delete(onlineUsersCountKey);
            }
            log.info("Reset online user states in Redis to prevent phantom data");
        };
    }
}