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

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.io.FilenameUtils;
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
  List<DownloadItem> downloadItems;

  /**
   * Name of file containing Resources to download.
   */
  @Parameter(property = "download.itemsFile")
  private File itemsFile;

  /**
   * Location of the output.
   */
  @Parameter(property = "download.outputDir", defaultValue = "${project.build.directory}", required = true)
  private String outputDirectory;

  /**
   * Whether to unpack the file in case it is an archive (.zip).
   */
  @Parameter(property = "download.unpack", defaultValue = "false")
  private boolean unpack;

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject mavenProject;

  @Parameter(defaultValue = "${session}", readonly = true)
  private MavenSession mavenSession;

  @Component
  private BuildPluginManager pluginManager;

  /* ************************************************************************** */
  @Override
  public void execute()
          throws MojoExecutionException, MojoFailureException {

    // prepare output directory
//    if (!outputDirectory.exists()) {
//      outputDirectory.mkdirs();
//    }

    if (downloadItems == null) {
      downloadItems = new ArrayList<>();
    }

    if (itemsFile != null) {
      addResourcesFromFile(itemsFile, downloadItems);
    }

    for (DownloadItem resource : downloadItems) {
      String uri = resource.getUri();
      Objects.requireNonNull(uri, "downloadItem has no uri: " + resource.toString());
      
      String sha256 = resource.getSha256();
      getLog().info("Downloading " + uri + (sha256 != null ? " (sha256: " + sha256 + ")" : ""));

      String destDir = resource.getTargetDir() != null ?
              resource.getTargetDir() : outputDirectory;
      
      executeMojo(
              plugin(DOWNLOAD_PLUGIN[0], DOWNLOAD_PLUGIN[1], DOWNLOAD_PLUGIN[2]),
              goal("wget"),
              configuration(
                      element(name("uri"), uri),
                      element(name("outputFileName"), resource.getTargetName()),
                      element(name("outputDirectory"), destDir),
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

  private void addResourcesFromFile(final File resourcesFile, final List<DownloadItem> resourcesList)
          throws MojoExecutionException {
    try {
      JAXBContext context = JAXBContext.newInstance(DownloadItems.class);
      DownloadItems drList = (DownloadItems) context.createUnmarshaller()
              .unmarshal(resourcesFile);
      if (drList != null && drList.getDownloadItems()!= null) {
        for (DownloadItem res : drList.getDownloadItems()) {
          resourcesList.add(res);
        }
      } else {
        getLog().warn("no resources found in " + resourcesFile);
      }
    } catch (JAXBException ex) {
      throw new MojoExecutionException("unable to parse resourcesFile " + resourcesFile, ex);
    }
  }

  @XmlRootElement(name = "downloadItems")
  @XmlAccessorType(XmlAccessType.FIELD)
  public static class DownloadItems {

    @XmlElement(name = "downloadItem")
    private List<DownloadItem> downloadItems = null;

    public List<DownloadItem> getDownloadItems() {
      return downloadItems;
    }

    public void setDownloadItems(List<DownloadItem> item) {
      this.downloadItems = item;
    }
  }

  @XmlRootElement(name = "downloadItem")
  @XmlAccessorType(XmlAccessType.FIELD)
  @XmlType(propOrder = {"uri", "targetName", "targetDir", "sha256"})
  public static class DownloadItem {

    URI uri;
    String targetName;
    String targetDir;
    String sha256;

    public DownloadItem() {
    }

    public String getUri() {
      return uri != null ? uri.toString() : null;
    }

    public final void setUri(String uriStr) throws URISyntaxException {
      this.uri = new URI(uriStr);
    }

    public String getTargetDir() {
      return targetDir;
    }

    public void setTargetDir(String targetDir) {
      this.targetDir = targetDir;
    }

    public String getTargetName() {
      return targetName != null || uri == null
              ? targetName
              : FilenameUtils.getName(uri.getPath());
    }

    public final void setTargetName(String targetName) {
      this.targetName = targetName;
    }

    public String getSha256() {
      return sha256;
    }

    public final void setSha256(String sha256) {
      this.sha256 = sha256;
    }
  }
}