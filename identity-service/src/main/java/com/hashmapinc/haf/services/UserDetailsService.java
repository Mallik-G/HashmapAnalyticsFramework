package com.hashmapinc.haf.services;

import com.hashmapinc.haf.models.User;
import com.hashmapinc.haf.models.UserCredentials;
import com.hashmapinc.haf.models.UserInformation;
import com.hashmapinc.haf.page.PaginatedRequest;
import com.hashmapinc.haf.page.TextPageData;
import com.hashmapinc.haf.requests.ActivateUserRequest;

import java.util.Collection;
import java.util.UUID;

public interface UserDetailsService {

    UserInformation loadUserByUsername(String s, String clientId);

    User save(User user);

    User findById(UUID id);

    Collection<User> findAllByClientId(String clientId);

    void deleteById(String userId);

    UserCredentials findCredentialsByUserId(UUID userId);

    UserCredentials findCredentialsByActivationToken(String activationToken);

    UserCredentials activateUserCredentials(ActivateUserRequest activateUserRequest);

    UserCredentials saveUserCredentials(UserCredentials credentials);

    UserCredentials requestPasswordReset(String email, String clientId);

    UserCredentials findUserCredentialsByResetToken(String resetToken);

    void deleteUserCredentialsById(UUID id);

    TextPageData<User> findPaginatedUsersByCriteria(PaginatedRequest request);

}
