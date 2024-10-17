package io.hyperfoil.tools.horreum.svc.user;

import java.util.List;
import java.util.Map;

import io.hyperfoil.tools.horreum.api.services.UserService;

/**
 * Interface for back-end implementations for {@link io.hyperfoil.tools.horreum.svc.UserServiceImpl}
 */
public interface UserBackEnd {

    List<String> getRoles(String username);

    List<UserService.UserData> searchUsers(String query);

    List<UserService.UserData> info(List<String> usernames);

    void createUser(UserService.NewUser user);

    void removeUser(String username);

    List<String> getTeams();

    Map<String, List<String>> teamMembers(String team);

    void updateTeamMembers(String team, Map<String, List<String>> roles);

    List<String> getAllTeams();

    void addTeam(String team);

    void deleteTeam(String team);

    List<UserService.UserData> administrators();

    void updateAdministrators(List<String> newAdmins);

    void setPassword(String username, String password);
}
