/*
 * Copyright © 2016 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.marketplace;

import co.cask.marketplace.spec.PackageMeta;
import co.cask.marketplace.spec.PackageSpec;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Tool to create and publish packages for a CDAP Marketplace.
 */
public class Packager {
  private static final Logger LOG = LoggerFactory.getLogger(Packager.class);
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final String ARCHIVE_NAME = "archive.zip";
  private final File baseDir;
  private final File packagesDir;
  private final File catalogFile;

  public Packager(File baseDir) {
    this.baseDir = baseDir;
    this.packagesDir = new File(baseDir, "packages");
    this.catalogFile = new File(baseDir, "packages.json");
  }

  /**
   * Deletes package archive.zip files and the package catalog file.
   * @throws IOException
   */
  public void clean() throws IOException {
    if (catalogFile.exists()) {
      LOG.info("Deleting catalog file " + catalogFile);
      if (!catalogFile.delete()) {
        throw new IOException("Could not delete catalog file " + catalogFile);
      }
    }

    for (File packageDir : listFiles(packagesDir)) {
      if (!packageDir.isDirectory()) {
        continue;
      }
      for (File versionDir : listFiles(packageDir)) {
        if (!versionDir.isDirectory()) {
          continue;
        }
        File archiveFile = new File(versionDir, ARCHIVE_NAME);
        if (archiveFile.exists()) {
          LOG.info("Deleting package archive " + archiveFile);
          if (!archiveFile.delete()) {
            throw new IOException("Could not delete archive file " + archiveFile);
          }
        }
      }
    }
  }

  /**
   * Build package archives and create the package catalog file.
   * @throws IOException
   */
  public void buildArchives() throws IOException {
    List<PackageMeta> packageCatalog = new ArrayList<>();

    for (File packageDir : listFiles(packagesDir)) {
      if (!packageDir.isDirectory()) {
        LOG.warn("Skipping {} since it is not a directory", packageDir);
        continue;
      }

      String packageName = packageDir.getName();
      for (File versionDir : listFiles(packageDir)) {
        if (!versionDir.isDirectory()) {
          LOG.warn("Skipping {} since it is not a directory", versionDir);
          continue;
        }

        String packageVersion = versionDir.getName();
        PackageSpec spec = buildPackageArchive(packageName, packageVersion, versionDir);
        spec.validate();
        packageCatalog.add(PackageMeta.fromSpec(packageName, packageVersion, spec));
        LOG.info("Created archive for package {}-{}", packageName, packageVersion);
      }
    }
    LOG.info("Created {} packages", packageCatalog.size());

    try (PrintWriter printWriter = new PrintWriter(new FileOutputStream(catalogFile))) {
      printWriter.print(GSON.toJson(packageCatalog) + "\n");
    }
    LOG.info("Created package catalog file {}", catalogFile);
  }

  /**
   * Sign all package specs and archives
   */
  public void signPackages() {

  }

  private PackageSpec buildPackageArchive(String name, String version, File packageDir) throws IOException {
    List<File> archiveFiles = new ArrayList<>();
    boolean containsIcon = false;
    PackageSpec spec = null;

    for (File packageFile : listFiles(packageDir)) {
      String fileName = packageFile.getName();

      if (fileName.equals("icon.jpg")) {
        containsIcon = true;
        continue;
      }

      if (fileName.equals("license.txt")) {
        continue;
      }

      if (fileName.equals("spec.json")) {
        try (Reader reader = new FileReader(packageFile)) {
          spec = GSON.fromJson(reader, PackageSpec.class);
        } catch (Exception e) {
          throw new IllegalArgumentException(String.format("Unable to parse spec for %s-%s", name, version), e);
        }
        continue;
      }

      if (fileName.equals(ARCHIVE_NAME)) {
        if (!packageFile.delete()) {
          throw new IOException("Unable to delete existing archive " + packageFile);
        }
        continue;
      }

      archiveFiles.add(packageFile);
    }

    if (spec == null) {
      throw new IllegalArgumentException(String.format("No spec found for package %s-%s.", name, version));
    }

    if (!containsIcon) {
      LOG.warn("Package {}-{} does not contain an icon.", name, version);
    }

    // build the zip from everything but icon, license, and spec
    if (!archiveFiles.isEmpty()) {
      File archiveFile = new File(packageDir, ARCHIVE_NAME);
      LOG.info("Creating archive for package {}-{} from files {}", name, version, archiveFiles);
      try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(archiveFile)))) {
        for (File file : archiveFiles) {
          addFileToArchive(zos, file, "");
        }
        zos.finish();
      }
    }
    return spec;
  }

  private void addFileToArchive(ZipOutputStream zos, File file, String parent) throws IOException {
    if (file.isDirectory()) {
      String path = parent + file.getName() + "/";
      zos.putNextEntry(new ZipEntry(path));
      for (File child : listFiles(file)) {
        addFileToArchive(zos, child, path);
      }
      zos.closeEntry();
    } else {
      zos.putNextEntry(new ZipEntry(parent + file.getName()));
      byte[] buffer = new byte[4096];
      try (FileInputStream inputStream = new FileInputStream(file)) {
        int length;
        while ((length = inputStream.read(buffer)) > 0) {
          zos.write(buffer, 0, length);
        }
      }
      zos.closeEntry();
    }
  }

  private File[] listFiles(File dir) throws IOException {
    File[] files = dir.listFiles();
    if (files == null) {
      throw new IOException("Unable to list files in directory " + dir);
    }
    return files;
  }

  public static void main(String[] args) throws Exception {

    Options options = new Options()
      .addOption(new Option("h", "help", false, "Print this usage message."))
      .addOption(new Option("k", "key", true,
                            "File containing the private key used to sign package specs and archives."))
      .addOption(new Option("d", "dir", true,
                            "Directory containing packages. Defaults to the current working directory."));

    CommandLineParser parser = new BasicParser();
    CommandLine commandLine = parser.parse(options, args);
    String[] commandArgs = commandLine.getArgs();

    // if help is an option
    if (commandLine.hasOption("h") || commandArgs.length == 0) {
      HelpFormatter helpFormatter = new HelpFormatter();
      helpFormatter.printHelp(
        Packager.class.getName() + " <command>",
        "Supported commands are 'clean' and 'build'." +
          "'build' will create package archives and the package.json catalog listing all packages found. " +
          "Expects packages to conform to a specific directory structure. " +
          "Each package should put its contents in the <base>/packages/<package-name>/<package-version> directory. " +
          "In that directory, there must be a spec.json file.\n" +
          "If the package contains a license, it must be named license.txt.\n" +
          "If the package contains an icon, it must be named icon.jpg.\n" +
          "Anything else in the package directory will be zipped up into a file named archive.zip.\n\n" +
          "'clean' will delete any existing archives and the package.json catalog listing.", options, "");
      System.exit(0);
    }

    String command = commandArgs[0];
    if (!command.equalsIgnoreCase("build") && !command.equalsIgnoreCase("clean")) {
      LOG.error("Command must be 'build' or 'clean'.");
      System.exit(1);
    }

    // read and validate options

    // get package directory
    String packageDirectoryStr = commandLine.hasOption("d") ?
      commandLine.getOptionValue("d") : System.getProperty("user.dir");
    File packageDirectory = new File(packageDirectoryStr);
    if (!packageDirectory.exists()) {
      LOG.error("Directory '{}' does not exist.", packageDirectory);
      System.exit(1);
    }
    if (!packageDirectory.isDirectory()) {
      LOG.error("Directory '{}' is not a directory.", packageDirectory);
      System.exit(1);
    }

    // get private key

    Packager packager = new Packager(packageDirectory);

    if (command.equalsIgnoreCase("clean")) {
      packager.clean();
    } else if (command.equalsIgnoreCase("build")) {
      packager.clean();
      packager.buildArchives();
    }
  }
}
