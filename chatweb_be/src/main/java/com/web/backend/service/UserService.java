package com.web.backend.service;

import com.web.backend.controller.request.*;
import com.web.backend.controller.response.*;
import com.web.backend.controller.response.AddressResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService {

    void setUserOnlineStatus(String username, boolean isOnline);

    boolean userExists(String username);

    UserResponse getMe(String username);

    UserDetailResponse getProfileUser(String username);

    UserDetailResponse updateUser(String username, UpdateUserRequest request);

    String updateAvatar(String username, MultipartFile avatarFile);

    void initiateEmailChange(String username, String newEmail, String currentPassword);

    void initiatePhoneChange(String username, String newPhone, String currentPassword);

    AddressResponse addAddress(String username, AddressRequest request);

    AddressResponse updateAddress(String username, Long addressId, AddressRequest request);

    void deleteAddress(String username, Long addressId);

    List<AddressResponse> getAllAddresses(String username);

    AddressResponse getAddressById(String username, Long addressId);

    void deleteUser(String username);

    void changePassword(String username, String currentPassword, String newPassword);

    void verifyPhoneChange(String username, String otp);

    void verifyEmailChange(String username, String otp);

    void resendPhoneChangeOtp(String username);

    void resendEmailChangeOtp(String username);
}
