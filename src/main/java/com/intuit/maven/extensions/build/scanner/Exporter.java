package com.intuit.maven.extensions.build.scanner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.intuit.maven.extensions.build.scanner.infra.DiskDataStorage;
import com.intuit.maven.extensions.build.scanner.model.ProjectSummary;
import com.intuit.maven.extensions.build.scanner.model.SessionProfile;
import com.intuit.maven.extensions.build.scanner.model.SessionSummary;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

/**
 * Renders scans into a single self-contained HTML file, so that a report can be read without
 * running {@link Server} - e.g. when the build ran on a remote node and only the report is copied
 * back.
 *
 * <p>The page is the same one the server serves; the data the page would fetch from {@code
 * /api/v1/...} is embedded in it, and {@code $.getJSON} is replaced by a lookup into that data.
 *
 * <p>Usage: {@code java -cp <jar> com.intuit...Exporter [--dir <scans>] [report.html]
 * [sessionId...]} - with no session ids every scan is included, so the report is navigable exactly
 * like the server's. Scans are read from {@code --dir} if given, otherwise from {@link
 * DiskDataStorage#defaultDirectory()}.
 */
public class Exporter {

  /** The first inline script in the page - everything up to it is markup and CDN script tags. */
  private static final String ANCHOR = "<script type=\"text/javascript\">";

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

  public static void main(String[] args) throws IOException {
    List<String> arguments = new ArrayList<>(Arrays.asList(args));

    String directory = DiskDataStorage.defaultDirectory();
    if (!arguments.isEmpty()
        && (arguments.get(0).equals("--dir") || arguments.get(0).equals("-d"))) {
      if (arguments.size() < 2) {
        System.err.println("usage: Exporter [--dir <scans>] [report.html] [sessionId...]");
        System.exit(1);
      }
      arguments.remove(0);
      directory = arguments.remove(0);
    }

    File output = new File(arguments.isEmpty() ? "report.html" : arguments.remove(0));
    Set<String> sessionIds = new HashSet<>(arguments);

    DiskDataStorage diskDataStorage = new DiskDataStorage(directory);
    Map<String, Object> data = new LinkedHashMap<>();
    List<ProjectSummary> projectSummaries = new ArrayList<>();
    int sessions = 0;

    for (ProjectSummary projectSummary : diskDataStorage.listProjectSummaries()) {
      List<SessionSummary> sessionSummaries = new ArrayList<>();

      for (SessionSummary sessionSummary :
          diskDataStorage.listSessionSummaries(
              projectSummary.getGroupId(), projectSummary.getArtifactId())) {

        if (!sessionIds.isEmpty() && !sessionIds.contains(sessionSummary.getId())) {
          continue;
        }

        SessionProfile sessionProfile;
        try {
          sessionProfile = diskDataStorage.getSessionProfile(sessionSummary.getId());
        } catch (Exception e) {
          System.err.println("skipping session " + sessionSummary.getId() + ": " + e);
          continue;
        }

        data.put("/api/v1/session-profiles/" + sessionSummary.getId(), sessionProfile);
        sessionSummaries.add(sessionSummary);
        sessions++;
      }

      if (sessionSummaries.isEmpty()) {
        continue;
      }

      data.put(
          "/api/v1/session-summaries/"
              + projectSummary.getGroupId()
              + ":"
              + projectSummary.getArtifactId(),
          sessionSummaries);
      projectSummaries.add(projectSummary);
    }

    data.put("/api/v1/project-summaries", projectSummaries);

    if (sessions == 0) {
      System.err.println(
          "no scans found in "
              + diskDataStorage.getDirectory()
              + (sessionIds.isEmpty() ? "" : " matching " + sessionIds));
    }

    FileUtils.write(output, render(data), StandardCharsets.UTF_8);

    System.out.println(
        "read "
            + diskDataStorage.getDirectory()
            + ", wrote "
            + output.getAbsolutePath()
            + " ("
            + projectSummaries.size()
            + " project(s), "
            + sessions
            + " session(s))");
  }

  private static String render(Map<String, Object> data) throws IOException {
    String html;
    try (InputStream in = Exporter.class.getClassLoader().getResourceAsStream("index.html")) {
      if (in == null) {
        throw new IllegalStateException("index.html is not on the classpath");
      }
      html = IOUtils.toString(in, StandardCharsets.UTF_8);
    }

    int at = html.indexOf(ANCHOR);
    if (at < 0) {
      throw new IllegalStateException(
          "index.html no longer contains " + ANCHOR + ", so the exporter needs updating");
    }

    String script =
        "<script>\n"
            + "    // Injected by "
            + Exporter.class.getName()
            + ": the page's data, and a $.getJSON that reads it rather than the server.\n"
            + "    var scans = "
            + escape(MAPPER.writeValueAsString(data))
            + ";\n"
            + "    $.getJSON = function (url, callback) {\n"
            + "        var path = decodeURIComponent(String(url).split(\"?\")[0]);\n"
            + "        if (!(path in scans)) {\n"
            + "            console.error(\"not exported into this report: \" + path);\n"
            + "            return;\n"
            + "        }\n"
            + "        callback(scans[path]);\n"
            + "    };\n"
            + "</script>\n\n";

    return (html.substring(0, at) + script + html.substring(at))
        // the page's own links are server-absolute, which does not survive file://
        .replace("href=\"/\"", "href=\"?\"")
        .replace("href=\"/?", "href=\"?")
        .replace("href='/?", "href='?");
  }

  /**
   * Makes JSON safe to sit inside a script element: no {@code </script>} can be formed, and no
   * character that JavaScript - unlike JSON - treats as a line terminator survives.
   */
  private static String escape(String json) {
    return json.replace("<", "\\u003c")
        .replace(">", "\\u003e")
        .replace("&", "\\u0026")
        .replace(String.valueOf((char) 0x2028), "\\u2028")
        .replace(String.valueOf((char) 0x2029), "\\u2029");
  }
}
