package com.saps.auth.layered.data;

import java.util.Optional;

public interface UserRepository {

    Optional<User> findByUsername(String username);

    /** @return false if the username is already taken */
    boolean saveIfAbsent(User user);
}