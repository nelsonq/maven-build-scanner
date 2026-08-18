package com.intuit.maven.extensions.build.scanner.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.intuit.maven.extensions.build.scanner.model.Project;
import com.intuit.maven.extensions.build.scanner.model.ProjectSummary;
import com.intuit.maven.extensions.build.scanner.model.SessionProfile;
import com.intuit.maven.extensions.build.scanner.model.SessionSummary;
import java.io.File;
import java.util.LinkedList;
import java.util.List;
import lombok.Getter;
import lombok.SneakyThrows;

public class DiskDataStorage {

  /** Set to store scans somewhere other than {@code ${user.home}/.mvn/scans}. */
  public static final String DIRECTORY_PROPERTY = "maven.build.scanner.dir";

  /** Honoured when {@link #DIRECTORY_PROPERTY} is not set - for CI, where -D is awkward. */
  public static final String DIRECTORY_ENV = "MAVEN_BUILD_SCANNER_DIR";

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

  @Getter private final String directory;
  private final String sessionSummaries, projectSummaries, sessionProfiles;

  public DiskDataStorage() {
    this(defaultDirectory());
  }

  public DiskDataStorage(String directory) {
    this.directory = directory;
    this.sessionSummaries = directory + File.separator + "session-summaries";
    this.projectSummaries = directory + File.separator + "project-summaries";
    this.sessionProfiles = directory + File.separator + "session-profiles";
  }

  /** Where scans are stored unless a directory is named explicitly. */
  public static String defaultDirectory() {
    String directory = System.getProperty(DIRECTORY_PROPERTY, System.getenv(DIRECTORY_ENV));
    return directory != null
        ? directory
        : System.getProperty("user.home") + File.separator + ".mvn" + File.separator + "scans";
  }

  void open() {
    new File(sessionProfiles).mkdirs();
    new File(projectSummaries).mkdirs();
    new File(sessionSummaries).mkdirs();
  }

  @SneakyThrows
  void updateProjectSummary(Project project, SessionProfile sessionProfile) {
    String path =
        projectSummaries
            + File.separator
            + project.getGroupId()
            + "-"
            + project.getArtifactId()
            + ".json";
    MAPPER.writeValue(
        new File(path),
        new ProjectSummary(
            project.getGroupId(), project.getArtifactId(), getSessionSummary(sessionProfile)));
  }

  private SessionSummary getSessionSummary(SessionProfile sessionProfile) {
    return new SessionSummary(
        sessionProfile.getId(),
        sessionProfile.getProject(),
        sessionProfile.getStartTime(),
        sessionProfile.getDuration(),
        sessionProfile.getBranch(),
        sessionProfile.getUsername(),
        sessionProfile.getGoals(),
        sessionProfile.getHostname(),
        sessionProfile.getStatus());
  }

  @SneakyThrows
  void updateSessionSummary(SessionProfile sessionProfile) {
    String path = sessionSummaries + File.separator + sessionProfile.getId() + ".json";
    MAPPER.writeValue(new File(path), getSessionSummary(sessionProfile));
  }

  @SneakyThrows
  void updateSessionProfile(SessionProfile sessionProfile) {
    String path = sessionProfiles + File.separator + sessionProfile.getId() + ".json";
    MAPPER.writeValue(new File(path), sessionProfile);
  }

  private static File[] listFiles(String directory) {
    File[] files = new File(directory).listFiles();
    return files == null ? new File[0] : files;
  }

  @SneakyThrows
  public List<ProjectSummary> listProjectSummaries() {
    List<ProjectSummary> items = new LinkedList<>();
    for (File file : listFiles(projectSummaries)) {
      items.add(MAPPER.readValue(file, ProjectSummary.class));
    }
    return items;
  }

  @SneakyThrows
  public List<SessionSummary> listSessionSummaries(String groupId, String artifactId) {
    List<SessionSummary> items = new LinkedList<>();
    for (File file : listFiles(sessionSummaries)) {
      SessionSummary item = MAPPER.readValue(file, SessionSummary.class);
      if (item.getProject().getGroupId().equals(groupId)
          && item.getProject().getArtifactId().equals(artifactId)) {
        items.add(item);
      }
    }
    return items;
  }

  @SneakyThrows
  public SessionProfile getSessionProfile(String id) {
    return MAPPER.readValue(
        new File(sessionProfiles + File.separator + id + ".json"), SessionProfile.class);
  }
}
