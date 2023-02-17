/*
 * DownloadMojo
 *
 * Copyright (c) 2022 Joerg Delker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jdelker.maven.plugin.devsak;

import java.io.File;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

/**
 * Maven goal for bulk fetching files.
 *
 * @author delker
 */
@Mojo(name = "download", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class DownloadMojo extends AbstractMojo {

  final static String[] DOWNLOAD_PLUGIN = new String[]{
    "com.googlecode.maven-download-plugin",
    "download-maven-plugin",
    "1.6.7"
  };
  
  /**
   * List of URIs to fetch.
   */
  @Parameter
  DownloadResource[] resources;

  /**
   * Location of the output.
   */
  @Parameter(defaultValue = "${project.build.directory}/unpacked", alias = "outputDir", required = true)
  private File outputDirectory;

  /**
   * Whether to unpack the file in case it is an archive (.zip).
   */
  @Parameter(property = "unpack", defaultValue = "false")
  private boolean unpack;

  @Parameter( defaultValue = "${project}", readonly = true )
  private MavenProject mavenProject;

  @Parameter( defaultValue = "${session}", readonly = true )
  private MavenSession mavenSession;

  @Component
  private BuildPluginManager pluginManager;

  /* ************************************************************************** */
  @Override
  public void execute()
          throws MojoExecutionException, MojoFailureException {

    // prepare output directory
    if (!outputDirectory.exists()) {
      outputDirectory.mkdirs();
    }

    for (DownloadResource resource : resources) {
      String uri = resource.getUri();
      String sha256 = resource.getSha256();
      getLog().info("Downloading " + uri + (sha256 != null ? " (sha256: " + sha256 + ")" : ""));

      executeMojo(
              plugin(DOWNLOAD_PLUGIN[0],DOWNLOAD_PLUGIN[1],DOWNLOAD_PLUGIN[2]),
              goal("wget"),
              configuration(
                      element(name("uri"), uri),
                      element(name("outputDirectory"), outputDirectory.getPath()),
                      element(name("unpack"), String.valueOf(unpack)),
                      element(name("sha256"), sha256)
              ),
              executionEnvironment(
                      mavenProject,
                      mavenSession,
                      pluginManager
              )
      );
    }
  }

  public static class DownloadResource {

    String uri;
    String sha256;

    public DownloadResource() {
    }

    public DownloadResource(String uri, String sha256) {
      this.uri = uri;
      this.sha256 = sha256;
    }

    public String getUri() {
      return uri;
    }

    public void setUri(String uri) {
      this.uri = uri;
    }

    public String getSha256() {
      return sha256;
    }

    public void setSha256(String sha256) {
      this.sha256 = sha256;
    }
  }
}
