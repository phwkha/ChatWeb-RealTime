package com.web.backend.service;

import com.web.backend.controller.request.*;
import com.web.backend.controller.response.*;
import com.web.backend.controller.response.AddressResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import com.web.backend.model.postgres.UserEntity;

public interface UserService {

    void setUserOnlineStatus(String username, boolean isOnline);

    boolean userExists(String username);

    UserResponse getMe(UserEntity user);

    UserDetailResponse getProfileUser(UserEntity user);

    UserDetailResponse updateUser(String username, UpdateUserRequest request);

    String updateAvatar(UserEntity user, MultipartFile avatarFile);

    void initiateEmailChange(UserEntity user, String newEmail, String currentPassword);

    void initiatePhoneChange(UserEntity user, String newPhone, String currentPassword);

    AddressResponse addAddress(UserEntity user, AddressRequest request);

    AddressResponse updateAddress(String username, Long addressId, AddressRequest request);

    void deleteAddress(String username, Long addressId);

    List<AddressResponse> getAllAddresses(String username);

    AddressResponse getAddressById(String username, Long addressId);

    void deleteUser(String username);

    void changePassword(UserEntity user, String currentPassword, String newPassword);

    void verifyPhoneChange(String username, String otp);

    void verifyEmailChange(UserEntity user, String otp);

    void resendPhoneChangeOtp(UserEntity user);

    void resendEmailChangeOtp(String username);
}
