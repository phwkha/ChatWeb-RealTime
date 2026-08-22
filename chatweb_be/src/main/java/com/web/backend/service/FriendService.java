package com.web.backend.service;

import org.springframework.lang.NonNull;

import com.web.backend.controller.response.PageResponse;
import com.web.backend.controller.response.UserSummaryResponse;

public interface FriendService {

    void sendFriendRequest(String requesterUsername, String addresseeUsername);

    void acceptFriendRequest(String acceptorUsername, String requesterUsername);

    PageResponse<UserSummaryResponse> getPendingRequests(String currentUsername, int page, int size, String sortDir);

    PageResponse<UserSummaryResponse> getSentRequests(String currentUsername, int page, int size, String sortDir);

    PageResponse<UserSummaryResponse> getFriendsList(String currentUsername, int page, int size, String sortDir);

    void deleteFriendship(String currentUsername, String targetUsername);

    void blockUser(String blockerUsername, String targetUsername);

    void unblockUser(String blockerUsername, String targetUsername);

    PageResponse<UserSummaryResponse> getBlockedList(String currentUsername, int page, int size, String sortDir);

    boolean isFriend(@NonNull String user1, @NonNull String user2);
}
