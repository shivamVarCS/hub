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

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Contains all files in a package.
 */
public class Package implements Iterable<File> {
  private final String name;
  private final String version;
  private final PackageMeta meta;
  private final File spec;
  private final File specSignature;
  @Nullable
  private final File archive;
  @Nullable
  private final File archiveSignature;
  @Nullable
  private final File license;
  @Nullable
  private final File icon;
  private final List<File> files;

  public Package(String name, String version, PackageMeta meta, File archive, File archiveSignature,
                 File spec, File specSignature, File license, File icon) {
    this.name = name;
    this.version = version;
    this.meta = meta;
    this.archive = archive;
    this.archiveSignature = archiveSignature;
    this.spec = spec;
    this.specSignature = specSignature;
    this.license = license;
    this.icon = icon;
    files = new ArrayList<>();
    files.add(spec);
    if (specSignature != null) {
      files.add(specSignature);
    }
    if (archive != null) {
      files.add(archive);
    }
    if (archiveSignature != null) {
      files.add(archiveSignature);
    }
    if (license != null) {
      files.add(license);
    }
    if (icon != null) {
      files.add(icon);
    }
  }

  public String getName() {
    return name;
  }

  public String getVersion() {
    return version;
  }

  public PackageMeta getMeta() {
    return meta;
  }

  public File getArchive() {
    return archive;
  }

  public File getArchiveSignature() {
    return archiveSignature;
  }

  public File getSpec() {
    return spec;
  }

  public File getSpecSignature() {
    return specSignature;
  }

  @Nullable
  public File getLicense() {
    return license;
  }

  @Nullable
  public File getIcon() {
    return icon;
  }

  public static Builder builder(String name, String version) {
    return new Builder(name, version);
  }

  @Override
  public Iterator<File> iterator() {
    return files.iterator();
  }

  /**
   * Builder to create a Package.
   */
  public static class Builder {
    private static final Gson GSON = new Gson();
    private final String name;
    private final String version;
    private PackageMeta meta;
    private File archive;
    private File archiveSignature;
    private File spec;
    private File specSignature;
    private File license;
    private File icon;

    public Builder(String name, String version) {
      this.name = name;
      this.version = version;
    }

    public Builder setArchive(File archive) {
      this.archive = archive;
      return this;
    }

    public Builder setArchiveSignature(File archiveSignature) {
      this.archiveSignature = archiveSignature;
      return this;
    }

    public Builder setSpec(File spec) {
      this.spec = spec;
      try (Reader reader = new FileReader(spec)) {
        PackageSpec specObj = GSON.fromJson(reader, PackageSpec.class);
        specObj.validate();
        meta = PackageMeta.fromSpec(name, version, specObj);
      } catch (Exception e) {
        throw new IllegalArgumentException("Unable to parse spec file " + spec, e);
      }
      return this;
    }

    public Builder setSpecSignature(File specSignature) {
      this.specSignature = specSignature;
      return this;
    }

    public Builder setLicense(File license) {
      this.license = license;
      return this;
    }

    public Builder setIcon(File icon) {
      this.icon = icon;
      return this;
    }

    public Package build() {
      return new Package(name, version, meta, archive, archiveSignature, spec, specSignature, license, icon);
    }
  }
}
