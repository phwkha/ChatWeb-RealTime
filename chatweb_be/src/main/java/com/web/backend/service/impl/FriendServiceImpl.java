package com.web.backend.service.impl;

import com.web.backend.common.FriendshipStatus;
import com.web.backend.common.UserStatus;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.common.NotificationsType;
import com.web.backend.controller.response.PageResponse;
import com.web.backend.controller.response.UserSummaryResponse;
import com.web.backend.kafka.payload.FriendPayload;
import com.web.backend.exception.custom.AccessForbiddenException;
import com.web.backend.exception.custom.InvalidDataException;
import com.web.backend.exception.custom.ResourceConflictException;
import com.web.backend.exception.custom.ResourceNotFoundException;
import com.web.backend.mapper.UserMapper;
import com.web.backend.model.postgres.FriendshipEntity;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.repository.FriendshipRepository;
import com.web.backend.repository.UserRepository;
import com.web.backend.service.FriendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "FRIEND-SERVICE")
public class FriendServiceImpl implements FriendService {

        private final UserRepository userRepository;

        private final FriendshipRepository friendshipRepository;

        private final RedisTemplate<String, Object> redisTemplate;

        private final ApplicationEventPublisher eventPublisher;

        private final UserMapper userMapper;

        private static final String RELATION_KEY_PREFIX = "relation:";
        private static final String RELATION_NONE = "NONE";
        private static final String RELATION_ACCEPTED = "ACCEPTED";
        private static final String RELATION_PENDING_PREFIX = "PENDING:";
        private static final String RELATION_BLOCKED_PREFIX = "BLOCKED:";

        private static final Duration TTL_ACCEPTED = Duration.ofDays(7);
        private static final Duration TTL_PENDING = Duration.ofDays(1);
        private static final Duration TTL_BLOCKED = Duration.ofDays(7);
        private static final Duration TTL_NONE = Duration.ofHours(1);

        private static final String DESC_STRING = "desc";

        private static final String CREATEAT_STRING = "createAt";

        private static final String ERROR_FRIEND_SELF_ADD_STRING = "error.friend.self_add";
        private static final String ERROR_FRIEND_SEND_DELETED_STRING = "error.friend.send_deleted";
        private static final String ERROR_FRIEND_SEND_LOCKED_STRING = "error.friend.send_locked";
        private static final String ERROR_FRIEND_BLOCKED_CANNOT_SEND_STRING = "error.friend.blocked_cannot_send";
        private static final String ERROR_FRIEND_INVITE_EXISTS_STRING = "error.friend.invite_exists";
        private static final String ERROR_FRIEND_ACCEPT_DELETED_STRING = "error.friend.accept_deleted";
        private static final String ERROR_FRIEND_ACCEPT_LOCKED_STRING = "error.friend.accept_locked";
        private static final String ERROR_FRIEND_INVITE_NOT_FOUND_STRING = "error.friend.invite_not_found";
        private static final String ERROR_FRIEND_ALREADY_FRIENDS_STRING = "error.friend.already_friends";
        private static final String ERROR_FRIEND_RELATION_NOT_FOUND_STRING = "error.friend.relation_not_found";
        private static final String ERROR_USER_NOT_FOUND_STRING = "error.user.not_found";

        @Override
        @Transactional
        public void sendFriendRequest(String requesterUsername, String addresseeUsername) {
                if (requesterUsername.equals(addresseeUsername))
                        throw new InvalidDataException(Translator.tolocale(ERROR_FRIEND_SELF_ADD_STRING));

                UserEntity requester = getUser(requesterUsername);
                UserEntity addressee = getUser(addresseeUsername);

                if (addressee.getUserStatus() == UserStatus.INACTIVE) {
                        throw new AccessForbiddenException(Translator.tolocale(ERROR_FRIEND_SEND_DELETED_STRING));
                }
                if (addressee.getUserStatus() == UserStatus.LOCKED) {
                        throw new AccessForbiddenException(Translator.tolocale(ERROR_FRIEND_SEND_LOCKED_STRING));
                }

                Optional<FriendshipEntity> existingRelation = friendshipRepository.findByUsers(requester, addressee);

                if (existingRelation.isPresent()) {
                        FriendshipEntity f = existingRelation.get();
                        if (f.getStatus() == FriendshipStatus.BLOCKED) {
                                throw new AccessForbiddenException(
                                                Translator.tolocale(ERROR_FRIEND_BLOCKED_CANNOT_SEND_STRING));
                        }
                        if (f.getStatus() == FriendshipStatus.ACCEPTED || f.getStatus() == FriendshipStatus.PENDING) {
                                throw new ResourceConflictException(
                                                Translator.tolocale(ERROR_FRIEND_INVITE_EXISTS_STRING));
                        }
                }

                FriendshipEntity friendship = new FriendshipEntity();
                friendship.setRequester(requester);
                friendship.setAddressee(addressee);
                friendship.setStatus(FriendshipStatus.PENDING);
                try {
                        friendshipRepository.save(friendship);
                } catch (DataIntegrityViolationException ex) {
                        log.warn("Concurrent friend request detected between '{}' and '{}'", requesterUsername,
                                        addresseeUsername);
                        throw new ResourceConflictException(Translator.tolocale(ERROR_FRIEND_INVITE_EXISTS_STRING));
                }

                redisTemplate.opsForValue().set(
                                buildRelationKey(requesterUsername, addresseeUsername),
                                RELATION_PENDING_PREFIX + requesterUsername,
                                TTL_PENDING);

                eventPublisher.publishEvent(FriendPayload.builder()
                                .senderUsername(requesterUsername)
                                .recipientUsername(addresseeUsername)
                                .senderType(NotificationsType.REQUEST_SENT_SUCCESS)
                                .recipientType(NotificationsType.FRIEND_REQUEST)
                                .senderDisplayName(requester.getFirstName() != null ? requester.getFirstName()
                                                : requester.getUsername())
                                .recipientDisplayName(addresseeUsername)
                                .build());
        }

        @Override
        @Transactional
        public void acceptFriendRequest(String acceptorUsername, String requesterUsername) {
                UserEntity acceptor = getUser(acceptorUsername);
                UserEntity requester = getUser(requesterUsername);

                if (requester.getUserStatus() == UserStatus.INACTIVE) {
                        throw new AccessForbiddenException(Translator.tolocale(ERROR_FRIEND_ACCEPT_DELETED_STRING));
                }
                if (requester.getUserStatus() == UserStatus.LOCKED) {
                        throw new AccessForbiddenException(Translator.tolocale(ERROR_FRIEND_ACCEPT_LOCKED_STRING));
                }

                FriendshipEntity friendship = friendshipRepository.findByUsers(acceptor, requester)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                Translator.tolocale(ERROR_FRIEND_INVITE_NOT_FOUND_STRING)));

                if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
                        throw new ResourceConflictException(Translator.tolocale(ERROR_FRIEND_ALREADY_FRIENDS_STRING));
                }

                friendship.setStatus(FriendshipStatus.ACCEPTED);
                friendshipRepository.save(friendship);

                redisTemplate.opsForValue().set(
                                buildRelationKey(acceptorUsername, requesterUsername),
                                RELATION_ACCEPTED,
                                TTL_ACCEPTED);

                eventPublisher.publishEvent(FriendPayload.builder()
                                .senderUsername(acceptorUsername)
                                .recipientUsername(requesterUsername)
                                .senderType(NotificationsType.YOU_ACCEPTED)
                                .recipientType(NotificationsType.FRIEND_ACCEPTED)
                                .senderDisplayName(acceptor.getFirstName() != null ? acceptor.getFirstName()
                                                : acceptor.getUsername())
                                .recipientDisplayName(requesterUsername)
                                .build());
        }

        @Override
        @Transactional(readOnly = true)
        public PageResponse<UserSummaryResponse> getSentRequests(String currentUsername, int page, int size,
                        String sortDir) {
                UserEntity currentUser = getUser(currentUsername);

                Pageable pageable = PageRequest.of(page, size,
                                Sort.by((sortDir.equalsIgnoreCase(DESC_STRING)) ? Sort.Direction.DESC
                                                : Sort.Direction.ASC,
                                                CREATEAT_STRING));

                Page<FriendshipEntity> pageResult = friendshipRepository.findByRequesterAndStatus(currentUser,
                                FriendshipStatus.PENDING, pageable);

                List<UserSummaryResponse> content = pageResult.getContent().stream()
                                .map(f -> userMapper.toUserSummaryResponse(f.getAddressee()))
                                .toList();

                return buildPageResponse(pageResult, content);
        }

        @Override
        @Transactional(readOnly = true)
        public PageResponse<UserSummaryResponse> getPendingRequests(String currentUsername, int page, int size,
                        String sortDir) {
                UserEntity currentUser = getUser(currentUsername);
                Pageable pageable = PageRequest.of(page, size,
                                Sort.by((sortDir.equalsIgnoreCase(DESC_STRING)) ? Sort.Direction.DESC
                                                : Sort.Direction.ASC,
                                                CREATEAT_STRING));

                Page<FriendshipEntity> pageResult = friendshipRepository.findByAddresseeAndStatus(currentUser,
                                FriendshipStatus.PENDING, pageable);

                List<UserSummaryResponse> content = pageResult.getContent().stream()
                                .map(f -> userMapper.toUserSummaryResponse(f.getRequester()))
                                .toList();

                return buildPageResponse(pageResult, content);
        }

        @Override
        @Transactional(readOnly = true)
        public PageResponse<UserSummaryResponse> getFriendsList(String currentUsername, int page, int size,
                        String sortDir) {
                UserEntity currentUser = getUser(currentUsername);
                Pageable pageable = PageRequest.of(page, size,
                                Sort.by((sortDir.equalsIgnoreCase(DESC_STRING)) ? Sort.Direction.DESC
                                                : Sort.Direction.ASC,
                                                CREATEAT_STRING));

                Page<FriendshipEntity> pageResult = friendshipRepository.findAllAcceptedFriendships(currentUser,
                                pageable);

                List<UserSummaryResponse> content = pageResult.getContent().stream()
                                .map(f -> {
                                        UserEntity friend = f.getRequester().getUsername().equals(currentUsername)
                                                        ? f.getAddressee()
                                                        : f.getRequester();
                                        return userMapper.toUserSummaryResponse(friend);
                                })
                                .toList();

                return buildPageResponse(pageResult, content);
        }

        @Override
        @Transactional
        public void deleteFriendship(String currentUsername, String targetUsername) {
                UserEntity user1 = getUser(currentUsername);
                UserEntity user2 = getUser(targetUsername);

                FriendshipEntity friendship = friendshipRepository.findByUsers(user1, user2)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                Translator.tolocale(ERROR_FRIEND_RELATION_NOT_FOUND_STRING)));

                boolean isAccepted = friendship.getStatus() == FriendshipStatus.ACCEPTED;
                boolean isRequester = friendship.getRequester().getUsername().equals(currentUsername);

                friendshipRepository.delete(friendship);

                redisTemplate.opsForValue().set(
                                buildRelationKey(currentUsername, targetUsername),
                                RELATION_NONE,
                                TTL_NONE);

                if (isAccepted) {
                        eventPublisher.publishEvent(FriendPayload.builder()
                                        .senderUsername(currentUsername)
                                        .recipientUsername(targetUsername)
                                        .recipientType(NotificationsType.UNFRIENDED)
                                        .senderDisplayName(currentUsername)
                                        .build());

                } else {
                        if (isRequester) {
                                eventPublisher.publishEvent(FriendPayload.builder()
                                                .senderUsername(currentUsername)
                                                .recipientUsername(targetUsername)
                                                .recipientType(NotificationsType.REQUEST_CANCELLED)
                                                .senderDisplayName(currentUsername)
                                                .build());

                        } else {
                                eventPublisher.publishEvent(FriendPayload.builder()
                                                .senderUsername(currentUsername)
                                                .recipientUsername(targetUsername)
                                                .recipientType(NotificationsType.REQUEST_REJECTED)
                                                .senderDisplayName(currentUsername)
                                                .build());
                        }
                }
        }

        @Override
        @Transactional
        public void blockUser(String blockerUsername, String targetUsername) {
                UserEntity blocker = getUser(blockerUsername);
                UserEntity target = getUser(targetUsername);

                FriendshipEntity friendship = friendshipRepository.findByUsers(blocker, target)
                                .orElse(new FriendshipEntity());

                friendship.setRequester(blocker);
                friendship.setAddressee(target);
                friendship.setStatus(FriendshipStatus.BLOCKED);

                friendshipRepository.save(friendship);

                redisTemplate.opsForValue().set(
                                buildRelationKey(blockerUsername, targetUsername),
                                RELATION_BLOCKED_PREFIX + blockerUsername,
                                TTL_BLOCKED);

                log.info("User '{}' blocked '{}'", blockerUsername, targetUsername);
        }

        @Override
        public boolean isFriend(@NonNull String user1, @NonNull String user2) {
                String key = buildRelationKey(user1, user2);
                Object cached = redisTemplate.opsForValue().get(key);
                if (cached != null) {
                        return RELATION_ACCEPTED.equals(cached.toString());
                }

                Optional<FriendshipEntity> relationOpt = friendshipRepository.findByUsernames(user1, user2);
                if (relationOpt.isPresent()) {
                        FriendshipEntity relation = relationOpt.get();
                        if (relation.getStatus() == FriendshipStatus.ACCEPTED) {
                                redisTemplate.opsForValue().set(key, RELATION_ACCEPTED, TTL_ACCEPTED);
                                return true;
                        } else if (relation.getStatus() == FriendshipStatus.PENDING) {
                                redisTemplate.opsForValue().set(key,
                                                RELATION_PENDING_PREFIX + relation.getRequester().getUsername(),
                                                TTL_PENDING);
                                return false;
                        } else if (relation.getStatus() == FriendshipStatus.BLOCKED) {
                                redisTemplate.opsForValue().set(key,
                                                RELATION_BLOCKED_PREFIX + relation.getRequester().getUsername(),
                                                TTL_BLOCKED);
                                return false;
                        }
                }

                redisTemplate.opsForValue().set(key, RELATION_NONE, TTL_NONE);
                return false;
        }

        private String buildRelationKey(@NonNull String user1, @NonNull String user2) {
                return RELATION_KEY_PREFIX + (user1.compareTo(user2) <= 0 ? user1 + ":" + user2 : user2 + ":" + user1);
        }

        private UserEntity getUser(String username) {
                return userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                Translator.tolocale(ERROR_USER_NOT_FOUND_STRING)));
        }

        private <T> PageResponse<T> buildPageResponse(Page<?> pageResult, List<T> content) {
                return PageResponse.<T>builder()
                                .content(content)
                                .pageNo(pageResult.getNumber())
                                .pageSize(pageResult.getSize())
                                .totalElements(pageResult.getTotalElements())
                                .totalPages(pageResult.getTotalPages())
                                .last(pageResult.isLast())
                                .build();
        }
}