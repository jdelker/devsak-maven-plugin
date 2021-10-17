package jdelker.maven.plugin.devsak;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jdelker.maven.plugin.devsak.util.ArtifactItem;
import jdelker.maven.plugin.devsak.util.DependencyUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.Artifact;
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
import org.apache.maven.shared.artifact.filter.resolve.PatternExclusionsFilter;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResult;
import org.apache.maven.shared.transfer.dependencies.DefaultDependableCoordinate;
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
   * Collection of ArtifactItems to work on. (ArtifactItem contains groupId,
   * artifactId, version, type, classifier, outputDirectory, destFileName,
   * overWrite and encoding.) See <a href="./usage.html">Usage</a> for details.
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
  @Parameter(defaultValue = "${project.build.directory}/dependencies", required = true)
  private File outputDirectory;

  @Parameter
  private String includeScope;

  @Parameter
  private String excludeScope;

  /**
   * Directory to store marker filesF
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
   * @throws org.apache.maven.plugin.MojoFailureException
   * @see ArtifactItem
   * @see #getArtifactItems
   * @see #copyArtifact(ArtifactItem)
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

    // handle exclusions
    String excludes = artifactItem.getExcludes();
    PatternExclusionsFilter pef = null;
    if (StringUtils.isNotEmpty(excludes)) {
      pef = new PatternExclusionsFilter(Arrays.asList(excludes.split(",")));
    }

    try {
      DefaultDependableCoordinate coordinate = new DefaultDependableCoordinate();
      coordinate.setGroupId(artifactItem.getGroupId());
      coordinate.setArtifactId(artifactItem.getArtifactId());
      coordinate.setVersion(artifactItem.getVersion());
      coordinate.setType(artifactItem.getType());

      Iterable<ArtifactResult> arList
              = dependencyResolver.resolveDependencies(getProjectBuildingRequest(), coordinate, pef);
      if (arList != null) {
        for (ArtifactResult ar : arList) {
          Artifact a = ar.getArtifact();
          if (isScopeIncluded(a) && !isScopeExcluded(a)) {
            File destFile = new File(outputDir, DependencyUtil.getFormattedFileName(a, false));
            copyFile(a.getFile(), destFile);
          }
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
      getLog().info("  copying " + artifact.getName() + " to " + destFile);
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
        Files.write(trackingFile.toPath(), copiedArtifacts, StandardOpenOption.CREATE);
      }
    } catch (IOException ex) {
      throw new MojoFailureException("unable to write tracking file: " + trackingFile, ex);
    }
  }

  private boolean isScopeIncluded(Artifact a) {
    boolean isIncluded = false;
    if (StringUtils.isEmpty(includeScope) || includeScope.equalsIgnoreCase(a.getScope())) {
      isIncluded = true;
    }
    return isIncluded;
  }

  private boolean isScopeExcluded(Artifact a) {
    boolean isExcluded = true;
    if (StringUtils.isEmpty(excludeScope) || !excludeScope.equalsIgnoreCase(a.getScope())) {
      isExcluded = false;
    }
    return isExcluded;
  }
}
