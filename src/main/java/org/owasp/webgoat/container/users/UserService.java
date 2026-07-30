/*
 * SPDX-FileCopyrightText: Copyright © 2017 WebGoat authors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.owasp.webgoat.container.users;

import java.util.List;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import org.flywaydb.core.Flyway;
import org.owasp.webgoat.container.lessons.Initializable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class UserService implements UserDetailsService {

  private final UserRepository userRepository;
  private final UserProgressRepository userTrackerRepository;
  private final JdbcTemplate jdbcTemplate;
  private final Function<String, Flyway> flywayLessons;
  private final List<Initializable> lessonInitializables;

  @Override
  public WebGoatUser loadUserByUsername(String username) throws UsernameNotFoundException {
    WebGoatUser webGoatUser = userRepository.findByUsername(username);
    if (webGoatUser == null) {
      throw new UsernameNotFoundException("User not found");
    } else {
      webGoatUser.createUser();
      lessonInitializables.forEach(l -> l.initialize(webGoatUser));
    }
    return webGoatUser;
  }

  public void addUser(String username, String password) {
    var userAlreadyExists = userRepository.existsByUsername(username);
    var webGoatUser = userRepository.save(new WebGoatUser(username, password));

    if (!userAlreadyExists) {
      userTrackerRepository.save(new UserProgress(username));
      createLessonsForUser(webGoatUser);
    }
  }

  private void createLessonsForUser(WebGoatUser webGoatUser) {
    String schemaName = webGoatUser.getUsername() == null ? "" : webGoatUser.getUsername();
    String createSchemaSql = "CREATE SCHEMA \"" + schemaName + "\" authorization dba";
    jdbcTemplate.execute(createSchemaSql);
    flywayLessons.apply(webGoatUser.getUsername()).migrate();
  }

  public List<WebGoatUser> getAllUsers() {
    return userRepository.findAll();
  }
}
