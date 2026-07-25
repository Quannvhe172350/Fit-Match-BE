package com.fitmatch.service;

/** UC-002: xác minh số điện thoại bằng OTP 6 chữ số gửi qua SMS. */
public interface PhoneVerificationService {

    /** Phát OTP mới cho SĐT hiện tại của user (vô hiệu hóa OTP cũ). */
    void requestOtp(String username);

    /** Xác thực OTP; sai quá 5 lần thì OTP bị khóa, phải xin mã mới. */
    void verifyOtp(String username, String code);
}
