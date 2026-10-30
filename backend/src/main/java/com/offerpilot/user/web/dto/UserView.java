package com.offerpilot.user.web.dto;

import com.offerpilot.user.domain.User;

public record UserView(Long id, String nickname, String email, String role) {

    public static UserView from(User user) {
        return new UserView(user.getId(), user.getNickname(), user.getEmail(), user.getRole());
    }
}
