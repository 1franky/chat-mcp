package com.aidatachat.domain.model;

import java.util.List;

public record UserPage(List<UserAccount> users, long totalElements, int page, int size) {

    public UserPage {
        users = List.copyOf(users);
    }
}
