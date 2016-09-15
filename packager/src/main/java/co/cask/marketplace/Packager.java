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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
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
  private final File packagesDir;
  private final File catalogFile;

  public Packager(File baseDir) {
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
  public List<Package> buildPackages() throws IOException {
    List<Package> packages = new ArrayList<>();
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
        Package pkg = buildPackage(packageName, packageVersion, versionDir);
        packageCatalog.add(pkg.getMeta());
        packages.add(pkg);
        LOG.info("Created package {}-{}", packageName, packageVersion);
      }
    }
    LOG.info("Created {} packages", packageCatalog.size());

    try (PrintWriter printWriter = new PrintWriter(new FileOutputStream(catalogFile))) {
      printWriter.print(GSON.toJson(packageCatalog) + "\n");
    }
    LOG.info("Created package catalog file {}", catalogFile);
    return packages;
  }

  public File getCatalog() {
    return catalogFile;
  }

  private Package buildPackage(String name, String version, File packageDir) throws IOException {
    List<File> archiveFiles = new ArrayList<>();
    boolean containsSpec = false;

    Package.Builder builder = Package.builder(name, version);

    for (File packageFile : listFiles(packageDir)) {
      String fileName = packageFile.getName();

      if (fileName.equals("icon.jpg")) {
        builder.setIcon(packageFile);
        continue;
      }

      if (fileName.equals("license.txt")) {
        builder.setLicense(packageFile);
        continue;
      }

      if (fileName.equals("spec.json")) {
        containsSpec = true;
        builder.setSpec(packageFile);
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

    if (!containsSpec) {
      throw new IllegalArgumentException(String.format("No spec found for package %s-%s.", name, version));
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
      builder.setArchive(archiveFile);
    }

    return builder.build();
  }

  private void addFileToArchive(ZipOutputStream zos, File file, String parent) throws IOException {
    if (file.isDirectory()) {
      String path = parent + file.getName() + "/";
      ZipEntry zipEntry = createDeterministicZipEntry(file, path);
      zos.putNextEntry(zipEntry);
      for (File child : listFiles(file)) {
        addFileToArchive(zos, child, path);
      }
      zos.closeEntry();
    } else {
      ZipEntry zipEntry = createDeterministicZipEntry(file, parent + file.getName());
      zos.putNextEntry(zipEntry);
      byte[] buffer = new byte[1024 * 1024];
      try (FileInputStream inputStream = new FileInputStream(file)) {
        int length;
        while ((length = inputStream.read(buffer)) > 0) {
          zos.write(buffer, 0, length);
        }
      }
      zos.closeEntry();
    }
  }

  // set the time on the zip entry so that zips created with the same data have the same bytes and md5
  private ZipEntry createDeterministicZipEntry(File file, String zipPath) throws IOException {
    ZipEntry zipEntry = new ZipEntry(zipPath);
    zipEntry.setTime(file.lastModified());
    return zipEntry;
  }

  private File[] listFiles(File dir) throws IOException {
    File[] files = dir.listFiles();
    if (files == null) {
      throw new IOException("Unable to list files in directory " + dir);
    }
    return files;
  }

}
