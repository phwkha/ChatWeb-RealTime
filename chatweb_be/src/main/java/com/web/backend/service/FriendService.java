package com.web.backend.service;

import org.springframework.lang.NonNull;

import com.web.backend.controller.response.PageResponse;
import com.web.backend.controller.response.UserSummaryResponse;
import com.web.backend.model.postgres.UserEntity;

public interface FriendService {

    void sendFriendRequest(UserEntity requester, String addresseeUsername);

    void acceptFriendRequest(UserEntity acceptor, String requesterUsername);

    PageResponse<UserSummaryResponse> getPendingRequests(UserEntity currentUser, int page, int size, String sortDir);

    PageResponse<UserSummaryResponse> getSentRequests(UserEntity currentUser, int page, int size, String sortDir);

    PageResponse<UserSummaryResponse> getFriendsList(UserEntity currentUser, int page, int size, String sortDir);

    void deleteFriendship(UserEntity currentUser, String targetUsername);

    void blockUser(UserEntity blocker, String targetUsername);

    void unblockUser(UserEntity blocker, String targetUsername);

    PageResponse<UserSummaryResponse> getBlockedList(UserEntity currentUser, int page, int size, String sortDir);

    boolean isFriend(@NonNull String user1, @NonNull String user2);
}
