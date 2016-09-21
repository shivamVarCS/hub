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
import org.bouncycastle.openpgp.PGPException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.annotation.Nullable;

/**
 * Tool to create and publish packages for a CDAP Marketplace.
 */
public class Packager {
  private static final Logger LOG = LoggerFactory.getLogger(Packager.class);
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final String ARCHIVE_NAME = "archive.zip";
  private static final Comparator<File> FILE_COMPARATOR = new FileComparator();
  private final File packagesDir;
  private final File catalogFile;
  private final Signer signer;
  private final boolean createZip;

  static {
    // zip stores the modified date in its header, which uses the default timezone.
    // always use UTC to ensure that even zips on machines with different timezones create the exact same zip.
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  }

  public Packager(File baseDir, @Nullable Signer signer) throws InvalidKeyException {
    this(baseDir, signer, false);
  }

  public Packager(File baseDir, @Nullable Signer signer, boolean createZip) {
    this.packagesDir = new File(baseDir, "packages");
    this.catalogFile = new File(baseDir, "packages.json");
    this.signer = signer;
    this.createZip = createZip;
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

    for (File packageDir : sortedListFiles(packagesDir)) {
      if (!packageDir.isDirectory()) {
        continue;
      }

      for (File versionDir : sortedListFiles(packageDir)) {
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

        for (File packagefile : sortedListFiles(versionDir)) {
          if (packagefile.getName().endsWith(".asc")) {
            LOG.info("Deleting signature file {}", packagefile);
            if (!packagefile.delete()) {
              throw new IOException("Could not delete signature file " + packagefile);
            }
          }
        }
      }
    }
  }

  /**
   * Build package archives and create the package catalog file.
   * @throws IOException
   */
  public List<Package> buildPackages() throws IOException, SignatureException, NoSuchAlgorithmException,
    NoSuchProviderException, PGPException {
    List<Package> packages = new ArrayList<>();
    List<PackageMeta> packageCatalog = new ArrayList<>();

    for (File packageDir : sortedListFiles(packagesDir)) {
      if (!packageDir.isDirectory()) {
        LOG.warn("Skipping {} since it is not a directory", packageDir);
        continue;
      }

      String packageName = packageDir.getName();
      for (File versionDir : sortedListFiles(packageDir)) {
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

  private Package buildPackage(String name, String version, File packageDir)
    throws IOException, NoSuchAlgorithmException, SignatureException, NoSuchProviderException, PGPException {

    List<File> archiveFiles = new ArrayList<>();
    boolean containsSpec = false;

    Package.Builder builder = Package.builder(name, version);

    for (File packageFile : sortedListFiles(packageDir)) {
      String fileName = packageFile.getName();

      if (fileName.equals("icon.png")) {
        builder.setIcon(packageFile);
        continue;
      }

      if (fileName.equals("license.txt")) {
        builder.setLicense(packageFile);
        continue;
      }

      if (fileName.equals("spec.json")) {
        containsSpec = true;
        builder.setSpec(new SignedFile(packageFile, signer == null ? null : signer.signFile(packageFile)));
        continue;
      }

      if (fileName.equals(ARCHIVE_NAME)) {
        if (!packageFile.delete()) {
          throw new IOException("Unable to delete existing archive " + packageFile);
        }
        continue;
      }


      builder.addFile(new SignedFile(packageFile, signer == null ? null : signer.signFile(packageFile)));
      archiveFiles.add(packageFile);
    }

    if (!containsSpec) {
      throw new IllegalArgumentException(String.format("No spec found for package %s-%s.", name, version));
    }

    // build the zip from everything but icon, license, and spec
    if (createZip && !archiveFiles.isEmpty()) {
      File archiveFile = new File(packageDir, ARCHIVE_NAME);
      LOG.info("Creating archive for package {}-{} from files {}", name, version, archiveFiles);
      try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(archiveFile)))) {
        for (File file : archiveFiles) {
          addFileToArchive(zos, file, "", builder.getMeta().getCreated() * 1000);
        }
        zos.finish();
      }

      builder.setArchive(new SignedFile(archiveFile, signer == null ? null : signer.signFile(archiveFile)));
    }

    return builder.build();
  }

  private void addFileToArchive(ZipOutputStream zos, File file, String parent, long time) throws IOException {
    if (file.isDirectory()) {
      String path = parent + file.getName() + "/";
      ZipEntry zipEntry = new ZipEntry(path);
      // set time to ensure bytes (and md5) are same for the zip
      zipEntry.setTime(time);
      zos.putNextEntry(zipEntry);
      for (File child : sortedListFiles(file)) {
        addFileToArchive(zos, child, path, time);
      }
      zos.closeEntry();
    } else {
      ZipEntry zipEntry = new ZipEntry(parent + file.getName());
      // set time to ensure bytes (and md5) are same for the zip
      zipEntry.setTime(time);
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

  // returns files in sorted order. We do this to ensure that zips created are always the same bytes.
  private File[] sortedListFiles(File dir) throws IOException {
    File[] files = dir.listFiles();
    if (files == null) {
      throw new IOException("Unable to list files in directory " + dir);
    }
    Arrays.sort(files, FILE_COMPARATOR);
    return files;
  }

  private static class FileComparator implements Comparator<File> {

    @Override
    public int compare(File o1, File o2) {
      return o1.getName().compareTo(o2.getName());
    }
  }
}
