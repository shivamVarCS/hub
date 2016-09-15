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

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A package specification.
 */
public class PackageSpec implements Validatable {
  private final String specVersion;
  private final String description;
  private final String label;
  private final String author;
  private final String org;
  private final String cdapVersion;
  private final long created;
  private final Set<String> categories;
  private final List<ActionSpec> actions;

  public PackageSpec(String specVersion, String description, String label, String author, String org,
                     String cdapVersion, long created, Set<String> categories, List<ActionSpec> actions) {
    this.specVersion = specVersion;
    this.description = description;
    this.label = label;
    this.author = author;
    this.org = org;
    this.cdapVersion = cdapVersion;
    this.created = created;
    this.categories = categories;
    this.actions = actions;
  }

  public String getSpecVersion() {
    return specVersion;
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

  public String getCdapVersion() {
    return cdapVersion;
  }

  public long getCreated() {
    return created;
  }

  public Set<String> getCategories() {
    return categories;
  }

  public List<ActionSpec> getActions() {
    return actions;
  }

  @Override
  public void validate() {
    for (ActionSpec actionSpec : actions) {
      actionSpec.validate();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    PackageSpec that = (PackageSpec) o;

    return Objects.equals(specVersion, that.specVersion) &&
      Objects.equals(description, that.description) &&
      Objects.equals(label, that.label) &&
      Objects.equals(author, that.author) &&
      Objects.equals(org, that.org) &&
      Objects.equals(cdapVersion, that.cdapVersion) &&
      Objects.equals(created, that.created) &&
      Objects.equals(categories, that.categories) &&
      Objects.equals(actions, that.actions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(specVersion, description, label, author, org, cdapVersion, created, categories, actions);
  }

  @Override
  public String toString() {
    return "PackageSpec{" +
      "specVersion='" + specVersion + '\'' +
      ", description='" + description + '\'' +
      ", label='" + label + '\'' +
      ", author='" + author + '\'' +
      ", org='" + org + '\'' +
      ", cdapVersion='" + cdapVersion + '\'' +
      ", created=" + created +
      ", categories=" + categories +
      ", actions=" + actions +
      '}';
  }
}
