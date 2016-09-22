package co.cask.marketplace;

import java.util.Objects;

/**
 * Package name and version.
 */
public class PackageId {
  private final String name;
  private final String version;

  public PackageId(String name, String version) {
    this.name = name;
    this.version = version;
  }

  public String getName() {
    return name;
  }

  public String getVersion() {
    return version;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    PackageId that = (PackageId) o;

    return Objects.equals(name, that.name) && Objects.equals(version, that.version);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, version);
  }

  @Override
  public String toString() {
    return "PackageId{" +
      "name='" + name + '\'' +
      ", version='" + version + '\'' +
      '}';
  }
}
