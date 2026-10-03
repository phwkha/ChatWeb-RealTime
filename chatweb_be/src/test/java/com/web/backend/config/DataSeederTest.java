package com.web.backend.config;

import com.web.backend.model.mongodb.ChatMessage;
import com.web.backend.model.postgres.PermissionEntity;
import com.web.backend.model.postgres.RoleEntity;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.repository.PermissionRepository;
import com.web.backend.repository.RoleRepository;
import com.web.backend.repository.UserRepository;
import com.web.backend.service.CuckooFilterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CuckooFilterService cuckooFilterService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private IndexOperations indexOperations;

    private DataSeeder dataSeeder;

    @BeforeEach
    void setUp() {
        dataSeeder = new DataSeeder(
                userRepository,
                roleRepository,
                permissionRepository,
                passwordEncoder,
                cuckooFilterService,
                redisTemplate,
                mongoTemplate
        );
        ReflectionTestUtils.setField(dataSeeder, "adminPassword", "admin123");
    }

    @Test
    void cleanupOnlineStatus_WhenFullRestart_CleansDatabaseAndRedisAndDropsIndexes() throws Exception {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.size("online_users")).thenReturn(0L);
        when(userRepository.resetAllOnlineStatus()).thenReturn(3);

        Set<String> routingKeys = new HashSet<>(List.of("ws:routing:servers:node1"));
        when(redisTemplate.keys("ws:routing:servers:*")).thenReturn(routingKeys);

        when(mongoTemplate.indexOps(ChatMessage.class)).thenReturn(indexOperations);

        IndexInfo index1 = mock(IndexInfo.class);
        when(index1.getName()).thenReturn("recipient_1");
        IndexInfo index2 = mock(IndexInfo.class);
        when(index2.getName()).thenReturn("conversationId_1");
        IndexInfo indexOther = mock(IndexInfo.class);
        when(indexOther.getName()).thenReturn("_id_");

        when(indexOperations.getIndexInfo()).thenReturn(List.of(index1, index2, indexOther));

        CommandLineRunner runner = dataSeeder.cleanupOnlineStatus();
        assertNotNull(runner);
        runner.run();

        verify(userRepository).resetAllOnlineStatus();
        verify(redisTemplate).delete("online_users");
        verify(redisTemplate).delete("online_users_count");
        verify(redisTemplate).delete("presence:offline_queue");
        verify(redisTemplate).delete(routingKeys);

        verify(indexOperations).dropIndex("recipient_1");
        verify(indexOperations).dropIndex("conversationId_1");
        verify(indexOperations, never()).dropIndex("_id_");
    }

    @Test
    void cleanupOnlineStatus_WhenActiveCountNull_TreatedAsFullRestart() throws Exception {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.size("online_users")).thenReturn(null);
        when(userRepository.resetAllOnlineStatus()).thenReturn(0);
        when(redisTemplate.keys("ws:routing:servers:*")).thenReturn(Collections.emptySet());

        when(mongoTemplate.indexOps(ChatMessage.class)).thenReturn(indexOperations);
        when(indexOperations.getIndexInfo()).thenReturn(Collections.emptyList());

        CommandLineRunner runner = dataSeeder.cleanupOnlineStatus();
        runner.run();

        verify(userRepository).resetAllOnlineStatus();
        verify(redisTemplate).delete("online_users");
        verify(redisTemplate).delete("online_users_count");
        verify(redisTemplate).delete("presence:offline_queue");
        verify(redisTemplate, never()).delete(any(Set.class));
    }

    @Test
    void cleanupOnlineStatus_WhenRollingUpdate_SkipsFullRestartCleanupButDropsIndexes() throws Exception {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.size("online_users")).thenReturn(5L);

        when(mongoTemplate.indexOps(ChatMessage.class)).thenReturn(indexOperations);
        when(indexOperations.getIndexInfo()).thenReturn(Collections.emptyList());

        CommandLineRunner runner = dataSeeder.cleanupOnlineStatus();
        runner.run();

        verify(userRepository, never()).resetAllOnlineStatus();
        verify(redisTemplate, never()).delete(anyString());
        verify(redisTemplate, never()).delete(any(Set.class));
        verify(mongoTemplate).indexOps(ChatMessage.class);
    }

    @Test
    void cleanupOnlineStatus_WhenIndexOpsThrowsException_HandlesGracefully() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.size("online_users")).thenReturn(5L);

        when(mongoTemplate.indexOps(ChatMessage.class)).thenThrow(new RuntimeException("Mongo connection failure"));

        CommandLineRunner runner = dataSeeder.cleanupOnlineStatus();
        assertDoesNotThrow(() -> runner.run());
    }

    @Test
    void run_WhenAdminDoesNotExistAndFilterNotInitialized_SeedsData() throws Exception {
        when(permissionRepository.findByName(anyString())).thenReturn(Optional.empty());
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(invocation -> {
            PermissionEntity p = invocation.getArgument(0);
            return p;
        });

        when(roleRepository.findByName(anyString())).thenReturn(Optional.empty());
        when(roleRepository.save(any(RoleEntity.class))).thenAnswer(invocation -> {
            RoleEntity r = invocation.getArgument(0);
            return r;
        });

        when(userRepository.existsByUsername("admin")).thenReturn(false);
        when(passwordEncoder.encode("admin123")).thenReturn("encodedPassword");

        when(redisTemplate.hasKey("filter:emails")).thenReturn(false);
        UserEntity sampleUser = new UserEntity();
        sampleUser.setUsername("testuser");
        sampleUser.setEmail("test@example.com");
        when(userRepository.findAll()).thenReturn(List.of(sampleUser));

        dataSeeder.run();

        verify(userRepository).save(any(UserEntity.class));
        verify(cuckooFilterService).add("filter:emails", "test@example.com");
        verify(cuckooFilterService).add("filter:usernames", "testuser");
    }

    @Test
    void run_WhenAdminExistsAndFilterInitialized_SkipsCreation() throws Exception {
        RoleEntity existingRole = new RoleEntity();
        existingRole.setName("ADMIN");
        when(roleRepository.findByName(eq("ADMIN"))).thenReturn(Optional.of(existingRole));
        when(roleRepository.findByName(eq("USER"))).thenReturn(Optional.of(new RoleEntity()));

        PermissionEntity existingPerm = new PermissionEntity();
        existingPerm.setName("PERM");
        when(permissionRepository.findByName(anyString())).thenReturn(Optional.of(existingPerm));

        when(userRepository.existsByUsername("admin")).thenReturn(true);
        when(redisTemplate.hasKey("filter:emails")).thenReturn(true);

        dataSeeder.run();

        verify(userRepository, never()).save(any(UserEntity.class));
        verify(cuckooFilterService, never()).add(anyString(), anyString());
    }
}
