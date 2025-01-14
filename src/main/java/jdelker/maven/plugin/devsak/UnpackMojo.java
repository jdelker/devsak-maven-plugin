/*
 * UnpackMojo
 *
 * Copyright (c) 2021 Joerg Delker
 *
 * This source code is subject to terms and conditions of the Apache License, Version 2.0.
 * A copy of the license can be found in the License.html file at the root of this distribution.
 * By using this source code in any fashion, you are agreeing to be bound by the terms of the
 * Apache License, Version 2.0.
 *
 * You must not remove this notice, or any other, from this software.
 */
package jdelker.maven.plugin.devsak;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.components.io.fileselectors.IncludeExcludeFileSelector;

/**
 * Goal for unpacking files
 */
@Mojo(name = "unpack", requiresProject = false, defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class UnpackMojo extends AbstractMojo {

  /**
   * The list of class or jar files.
   */
  @Parameter(required = true)
  private FileSet fileSet;

  /**
   * Location of the output.
   */
  @Parameter(defaultValue = "${project.build.directory}/unpacked", alias = "outputDir", required = true)
  private File outputDirectory;

  /**
   * The list of class files to process.
   */
  @Parameter
  private String[] includes;
  @Parameter
  private String[] excludes;

  /**
   * To look up Archiver/UnArchiver implementations
   */
  @Component
  private ArchiverManager archiverManager;

  /* ************************************************************************** */
  @Override
  public void execute()
          throws MojoExecutionException, MojoFailureException {

    // prepare output directory
    if (!outputDirectory.exists()) {
      outputDirectory.mkdirs();
    }

    List<File> files = getFiles(fileSet);
    for (File f : files) {
      getLog().info("Unpacking " + f.getName() + " to " + outputDirectory);
      unpack(f);
    }
  }

  /**
   * @param file {@link File}
   * @throws MojoFailureException in case of an error.
   */
  protected void unpack(File file) throws MojoFailureException {
    File filePath = new File(fileSet.getDirectory(), file.getPath());

    try {
      UnArchiver unArchiver = archiverManager.getUnArchiver(file);
      unArchiver.setIgnorePermissions(true);

      unArchiver.setSourceFile(filePath);

      unArchiver.setDestDirectory(outputDirectory);

      if (!ArrayUtils.isEmpty(includes) || !ArrayUtils.isEmpty(excludes)) {
        IncludeExcludeFileSelector[] selectors
                = new IncludeExcludeFileSelector[]{new IncludeExcludeFileSelector()};

        if (ArrayUtils.isNotEmpty(excludes)) {
          selectors[0].setExcludes(excludes);
        }

        if (ArrayUtils.isNotEmpty(includes)) {
          selectors[0].setIncludes(includes);
        }

        unArchiver.setFileSelectors(selectors);
      }
      
      unArchiver.extract();
      
    } catch (Exception ex) {
      throw new MojoFailureException("Unpack failed", ex);
    }
  }

  private List<File> getFiles(FileSet fileSet)
          throws MojoFailureException {

    List<File> filesToProcess = new ArrayList<>();

    FileSetManager fileSetManager = new FileSetManager(getLog(), true);
    String[] files = fileSetManager.getIncludedFiles(fileSet);

    if (files == null || files.length == 0) {
      getLog().info("No files found.");
    } else {
      for (String fileName : files) {
        File f = new File(fileName);
        filesToProcess.add(f);
      }
    }

    return filesToProcess;
  }

}
