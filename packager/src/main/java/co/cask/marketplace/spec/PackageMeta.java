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

package co.cask.marketplace.spec;

import java.util.Objects;
import java.util.Set;

/**
 * Metadata about a package. The Packager will create a file containing a list of these, one for each package
 * it created.
 */
public class PackageMeta {
  private final String name;
  private final String version;
  private final String description;
  private final String label;
  private final String author;
  private final String org;
  private final Set<String> categories;
  private final String cdapVersion;

  public static PackageMeta fromSpec(String name, String version, PackageSpec spec) {
    return new PackageMeta(name, version, spec.getDescription(), spec.getLabel(), spec.getAuthor(), spec.getOrg(),
                           spec.getCategories(), spec.getCdapVersion());
  }

  public PackageMeta(String name, String version, String description, String label, String author, String org,
                     Set<String> categories, String cdapVersion) {
    this.name = name;
    this.version = version;
    this.description = description;
    this.label = label;
    this.author = author;
    this.org = org;
    this.categories = categories;
    this.cdapVersion = cdapVersion;
  }

  public String getName() {
    return name;
  }

  public String getVersion() {
    return version;
  }

  public String getDescription() {
    return description;
  }

  public String getLabel() {
    return label;
  }

  public String getAuthor() {
    return author;
  }

  public String getOrg() {
    return org;
  }

  public Set<String> getCategories() {
    return categories;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    PackageMeta that = (PackageMeta) o;
    return Objects.equals(name, that.name) &&
      Objects.equals(version, that.version) &&
      Objects.equals(description, that.description) &&
      Objects.equals(label, that.label) &&
      Objects.equals(author, that.author) &&
      Objects.equals(org, that.org) &&
      Objects.equals(categories, that.categories) &&
      Objects.equals(cdapVersion, that.cdapVersion);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, version, description, label, author, org, categories, cdapVersion);
  }

  @Override
  public String toString() {
    return "PackageMeta{" +
      "name='" + name + '\'' +
      ", version='" + version + '\'' +
      ", description='" + description + '\'' +
      ", label='" + label + '\'' +
      ", author='" + author + '\'' +
      ", org='" + org + '\'' +
      ", categories=" + categories +
      ", cdapVersion='" + cdapVersion + '\'' +
      '}';
  }
}
