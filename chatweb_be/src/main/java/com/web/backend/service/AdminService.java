package com.web.backend.service;

import com.web.backend.controller.request.AddressRequest;
import com.web.backend.controller.request.AdminCreateUserRequest;
import com.web.backend.controller.request.AdminSearchUserRequest;
import com.web.backend.controller.request.AdminUpdateUserRequest;
import com.web.backend.controller.response.*;

import java.util.List;

public interface AdminService {

    PageResponse<UserSummaryResponse> getOnlineUsers(int pageNo, int pageSize);

    PageResponse<UserResponse> searchUsersForAdmin(AdminSearchUserRequest request, int pageNo, int pageSize,
            String... sorts);

    UserDetailResponse getUserByUsername(String username);

    UserResponse adminCreateUser(AdminCreateUserRequest request);

    UserResponse lockUser(String username);

    UserResponse unlockUser(String username);

    void deleteAvatar(String username);

    UserResponse adminUpdateUser(String username, AdminUpdateUserRequest request);

    void adminDeleteUser(String targetUsername, String requesterUsername);

    List<AddressResponse> adminGetAllAddresses(String targetUsername);

    AddressResponse adminGetAddressById(String targetUsername, Long addressId);

    AddressResponse adminUpdateAddress(String targetUsername, Long addressId, AddressRequest request);

    void adminDeleteAddress(String targetUsername, Long addressId);
}
