package io.hyperfoil.tools.horreum.svc.user;

import io.hyperfoil.tools.horreum.api.internal.services.UserService;

import java.util.List;
import java.util.Map;

public interface UserBackEnd {

    List<UserService.UserData> searchUsers(String query);
    List<UserService.UserData> info(List<String> usernames);

    void createUser(UserService.NewUser user);
    List<String> getTeams();

    Map<String, List<String>> teamMembers(String team);
    void updateTeamMembers(String team, Map<String, List<String>> roles);
    List<String> getAllTeams();
    void addTeam(String team);

    void deleteTeam(String team);
    List<UserService.UserData> administrators();
    void updateAdministrators(List<String> newAdmins);
}
