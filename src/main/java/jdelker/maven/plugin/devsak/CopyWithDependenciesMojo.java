/*
 * CopyWithDependencies
 *
 * Copyright (c) 2022-2023 Joerg Delker
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import jdelker.maven.plugin.devsak.util.DependencyUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.artifact.filter.resolve.AndFilter;
import org.apache.maven.shared.artifact.filter.resolve.PatternExclusionsFilter;
import org.apache.maven.shared.artifact.filter.resolve.PatternInclusionsFilter;
import org.apache.maven.shared.artifact.filter.resolve.TransformableFilter;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResult;
import org.apache.maven.shared.transfer.dependencies.DefaultDependableCoordinate;
import org.apache.maven.shared.transfer.dependencies.DependableCoordinate;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolver;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolverException;
import org.codehaus.plexus.util.FileUtils;

/**
 * Goal that copies an artifact, including its dependencies from the repository
 * to a defined location.
 *
 * @author delker
 * @since 1.0
 */
@Mojo(name = "copy-with-dependencies", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, requiresProject = true, threadSafe = true)
public class CopyWithDependenciesMojo extends AbstractMojo {

  public static final String TRACKING_FILENAME = "copy-with-dependencies.tracking";

  /**
   * Collection of ArtifactItems to work on.
   *
   * See <a href="./usage.html">Usage</a> for details.
   */
  @Parameter
  private List<ArtifactItem> artifactItems;

  /**
   * Track installed artifacts, so they are not copied on any subsequent build.
   */
  @Parameter(defaultValue = "true")
  private boolean artifactTracking;

  /**
   * Directory to store marker filesF
   */
  @Parameter(defaultValue = "${project.build.directory}/copied-artifacts", required = true)
  private File outputDirectory;

  /**
   * Directory to store marker files
   */
  @Parameter(defaultValue = "${project.build.directory}/.markers", required = true)
  private File markersDirectory;

  /**
   * The Maven session
   */
  @Parameter(defaultValue = "${session}", readonly = true, required = true)
  protected MavenSession session;

  @Component
  private DependencyResolver dependencyResolver;

  private final Set<String> copiedArtifacts = new HashSet<>();

  /**
   * Main entry into mojo.This method gets the ArtifactItems and iterates
   * through each one passing it to copyArtifact.
   *
   * @throws MojoExecutionException with a message if an error occurs.
   * @throws org.apache.maven.plugin.MojoFailureException when no artifactItems
   * are present
   * @see ArtifactItem
   */
  @Override
  public void execute()
          throws MojoExecutionException, MojoFailureException {

    if (artifactItems == null || artifactItems.isEmpty()) {
      throw new MojoFailureException("Either artifact or artifactItems is required ");
    }

    readTrackingFile();
    for (ArtifactItem artifactItem : artifactItems) {
      String itemId = artifactItem.toString();
      if (!artifactTracking || !copiedArtifacts.contains(itemId)) {
        getLog().info("Processing " + itemId);
        copyArtifactWithDependencies(artifactItem);
      } else {
        getLog().info("Skipping already processed " + itemId);
      }
      copiedArtifacts.add(itemId);
    }
    writeTrackingFile();
  }

  /**
   * Resolves the artifact and all its dependencies from the repository and
   * copies it to the specified location.
   *
   * @param artifactItem containing the information about the Artifact
   * @throws MojoExecutionException with a message if an error occurs.
   */
  protected void copyArtifactWithDependencies(ArtifactItem artifactItem)
          throws MojoExecutionException {

    // determine output directory
    File outputDir = artifactItem.getOutputDirectory();
    if (outputDir == null) {
      outputDir = outputDirectory;
    }

    // handle filters
    TransformableFilter filter = getFilter(artifactItem);

    try {
      DefaultDependableCoordinate coordinate = new DefaultDependableCoordinate();
      coordinate.setGroupId(artifactItem.getGroupId());
      coordinate.setArtifactId(artifactItem.getArtifactId());
      coordinate.setVersion(artifactItem.getVersion());
      coordinate.setType(artifactItem.getType());

      Iterable<ArtifactResult> arList
              = dependencyResolver.resolveDependencies(getProjectBuildingRequest(), coordinate, filter);
      if (arList != null) {
        for (ArtifactResult ar : arList) {
          Artifact a = ar.getArtifact();
          File destFile = new File(outputDir, DependencyUtil.getFormattedFileName(a, false));
          copyFile(a.getFile(), destFile);
        }
      }
    } catch (DependencyResolverException ex) {
      throw new MojoExecutionException("failed to copy dependencies", ex);
    }
  }

  /**
   * Does the actual copy of the file and logging.
   *
   * @param artifact represents the file to copy.
   * @param destFile file name of destination file.
   * @throws MojoExecutionException with a message if an error occurs.
   */
  protected void copyFile(File artifact, File destFile)
          throws MojoExecutionException {
    try {
      getLog().info("Copying " + artifact.getName() + " to " + destFile);
      FileUtils.copyFile(artifact, destFile);
    } catch (IOException e) {
      throw new MojoExecutionException("Error copying artifact from " + artifact + " to " + destFile, e);
    }
  }

  /**
   * Generate a new ProjectBuildingRequest populated from the current session
   * and the current project remote repositories, used to resolve artifacts.
   *
   * @return ProjectBuildingRequest
   */
  public ProjectBuildingRequest getProjectBuildingRequest() {
    ProjectBuildingRequest pbr
            = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());

    return pbr;
  }

  private void readTrackingFile() throws MojoFailureException {
    File trackingFile = new File(markersDirectory, TRACKING_FILENAME);
    try {
      if (artifactTracking && trackingFile.exists()) {
        copiedArtifacts.addAll(Files.readAllLines(trackingFile.toPath()));
      }
    } catch (IOException ex) {
      throw new MojoFailureException("unable to read tracking file: " + trackingFile, ex);
    }
  }

  private void writeTrackingFile() throws MojoFailureException {
    File trackingFile = new File(markersDirectory, TRACKING_FILENAME);
    try {
      if (artifactTracking && !copiedArtifacts.isEmpty()) {
        if (!markersDirectory.exists()) {
          markersDirectory.mkdirs();
        }
        Files.write(trackingFile.toPath(), copiedArtifacts, StandardOpenOption.CREATE);
      }
    } catch (IOException ex) {
      throw new MojoFailureException("unable to write tracking file: " + trackingFile, ex);
    }
  }

  private TransformableFilter getFilter(ArtifactItem artifactItem) {
    List<TransformableFilter> filterList = new ArrayList<>();

    String excludes = artifactItem.getExcludes();
    if (StringUtils.isNotEmpty(excludes)) {
      filterList.add(new PatternExclusionsFilter(Arrays.asList(excludes.split(","))));
    }
    String includes = artifactItem.getIncludes();
    if (StringUtils.isNotEmpty(includes)) {
      filterList.add(new PatternInclusionsFilter(Arrays.asList(includes.split(","))));
    }

    return filterList.isEmpty() ? null : new AndFilter(filterList);
  }

  /**
   * POJO for an artifact item.
   *
   * @author jdelker
   */
  public static class ArtifactItem
          implements DependableCoordinate {

    /**
     * Group Id of Artifact
     *
     * @parameter
     * @required
     */
    private String groupId;

    /**
     * Name of Artifact
     *
     * @parameter
     * @required
     */
    private String artifactId;

    /**
     * Version of Artifact
     *
     * @parameter
     */
    private String version = null;

    /**
     * Type of Artifact (War,Jar,etc)
     *
     * @parameter
     * @required
     */
    private String type = "jar";

    /**
     * Classifier for Artifact (tests,sources,etc)
     *
     * @parameter
     */
    private String classifier;

    /**
     * Location to use for this Artifact. Overrides default location.
     *
     * @parameter
     */
    private File outputDirectory;

    /**
     * Provides ability to change destination file name
     *
     * @parameter
     */
    private String destFileName;

    /**
     * A comma separated list of artifacts patterns to include.
     */
    private String includes;

    /**
     * A comma separated list of artifacts patterns to exclude.
     */
    private String excludes;

    /**
     * Default constructor.
     */
    public ArtifactItem() {
      // default constructor
    }

    private String filterEmptyString(String in) {
      if ("".equals(in)) {
        return null;
      }
      return in;
    }

    /**
     * @return Returns the artifactId.
     */
    @Override
    public String getArtifactId() {
      return artifactId;
    }

    /**
     * @param theArtifact The artifactId to set.
     */
    public void setArtifactId(String theArtifact) {
      this.artifactId = filterEmptyString(theArtifact);
    }

    /**
     * @return Returns the groupId.
     */
    @Override
    public String getGroupId() {
      return groupId;
    }

    /**
     * @param groupId The groupId to set.
     */
    public void setGroupId(String groupId) {
      this.groupId = filterEmptyString(groupId);
    }

    /**
     * @return Returns the type.
     */
    @Override
    public String getType() {
      return type;
    }

    /**
     * @param type The type to set.
     */
    public void setType(String type) {
      this.type = filterEmptyString(type);
    }

    /**
     * @return Returns the version.
     */
    @Override
    public String getVersion() {
      return version;
    }

    /**
     * @param version The version to set.
     */
    public void setVersion(String version) {
      this.version = filterEmptyString(version);
    }

    /**
     * @return Returns the base version.
     */
    public String getBaseVersion() {
      return ArtifactUtils.toSnapshotVersion(version);
    }

    /**
     * @return Classifier.
     */
    @Override
    public String getClassifier() {
      return classifier;
    }

    /**
     * @param classifier Classifier.
     */
    public void setClassifier(String classifier) {
      this.classifier = filterEmptyString(classifier);
    }

    @Override
    public String toString() {
      if (this.classifier == null) {
        return groupId + ":" + artifactId + ":" + Objects.toString(version, "?") + ":" + type;
      } else {
        return groupId + ":" + artifactId + ":" + classifier + ":" + Objects.toString(version, "?") + ":"
                + type;
      }
    }

    /**
     * @return Returns the location.
     */
    public File getOutputDirectory() {
      return outputDirectory;
    }

    /**
     * @param outputDirectory The outputDirectory to set.
     */
    public void setOutputDirectory(File outputDirectory) {
      this.outputDirectory = outputDirectory;
    }

    /**
     * @return Returns the location.
     */
    public String getDestFileName() {
      return destFileName;
    }

    /**
     * @param destFileName The destFileName to set.
     */
    public void setDestFileName(String destFileName) {
      this.destFileName = filterEmptyString(destFileName);
    }

    /**
     * @return Returns a comma separated list of excluded items
     */
    public String getExcludes() {
      return DependencyUtil.cleanToBeTokenizedString(this.excludes);
    }

    /**
     * @param excludes A comma separated list of items to exclude i.e.
     * <code>**\/*.xml, **\/*.properties</code>
     */
    public void setExcludes(String excludes) {
      this.excludes = excludes;
    }

    /**
     * @return Returns a comma separated list of included items
     */
    public String getIncludes() {
      return DependencyUtil.cleanToBeTokenizedString(this.includes);
    }

    /**
     * @param includes A comma separated list of items to include i.e.
     * <code>**\/*.xml, **\/*.properties</code>
     */
    public void setIncludes(String includes) {
      this.includes = includes;
    }
  }

}
