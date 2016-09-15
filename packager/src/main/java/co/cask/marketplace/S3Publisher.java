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

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.common.io.Files;
import com.google.common.net.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Publish packages to S3.
 */
public class S3Publisher implements Publisher {
  private static final Logger LOG = LoggerFactory.getLogger(S3Publisher.class);
  private final AmazonS3Client client;
  private final String bucket;

  public S3Publisher(File cfgFile, String bucket) throws IOException {
    Map<String, String> conf = parseConfig(cfgFile);
    String accessKey = conf.get("access_key");
    String secretKey = conf.get("secret_key");
    if (accessKey == null) {
      throw new IllegalArgumentException("Could not find access_key in S3 config file " + cfgFile);
    }
    if (secretKey == null) {
      throw new IllegalArgumentException("Could not find access_key in S3 config file " + cfgFile);
    }

    ClientConfiguration clientConf = new ClientConfiguration();
    clientConf = "true".equalsIgnoreCase(conf.get("use_https")) ?
      clientConf.withProtocol(Protocol.HTTPS) : clientConf.withProtocol(Protocol.HTTP);
    if (conf.containsKey("socket_timeout")) {
      clientConf.setSocketTimeout(Integer.parseInt(conf.get("socket_timeout")));
    }
    this.client = new AmazonS3Client(new BasicAWSCredentials(accessKey, secretKey), clientConf);
    this.bucket = bucket;
  }

  @Override
  public void publishPackage(Package pkg) throws Exception {
    String keyBase = String.format("packages/%s/%s/", pkg.getName(), pkg.getVersion());
    for (File file : pkg) {
      putFileIfChanged(keyBase + file.getName(), file);
    }
  }

  @Override
  public void publishCatalog(File catalog) throws Exception {
    putFileIfChanged(catalog.getName(), catalog);
  }

  // puts the file on s3 if the md5 is different, or the file length is different.
  private void putFileIfChanged(String key, File file) throws IOException {
    ObjectMetadata existingMeta = client.getObjectMetadata(bucket, key);
    long fileLength = file.length();
    String md5Hex = BaseEncoding.base16().encode(Files.hash(file, Hashing.md5()).asBytes());
    if (existingMeta != null &&
      existingMeta.getContentLength() == fileLength &&
      existingMeta.getETag() != null && existingMeta.getETag().equalsIgnoreCase(md5Hex)) {
      LOG.info("{} hasn't changed, skipping upload to s3.", key);
      return;
    }

    String ext = Files.getFileExtension(file.getName());
    String contentType;
    switch (ext) {
      case "json":
        contentType = MediaType.JSON_UTF_8.toString();
        break;
      case "txt":
        contentType = MediaType.PLAIN_TEXT_UTF_8.toString();
        break;
      default:
        contentType = MediaType.OCTET_STREAM.toString();
    }
    ObjectMetadata newMeta = new ObjectMetadata();
    newMeta.setContentType(contentType);
    PutObjectRequest request = new PutObjectRequest(bucket, key, file)
      .withCannedAcl(CannedAccessControlList.PublicRead)
      .withMetadata(newMeta);
    client.putObject(request);
    LOG.info("put file {} into s3 bucket {}, key {}", file, bucket, key);
  }

  private static Map<String, String> parseConfig(File cfgFile) throws IOException {
    Map<String, String> config = new HashMap<>();
    try (BufferedReader br = new BufferedReader(new FileReader(cfgFile))) {
      String line;
      while ((line = br.readLine()) != null) {
        String[] parts = line.split("\\s*=\\s*");
        if (parts.length != 2) {
          continue;
        }
        config.put(parts[0], parts[1]);
      }
    }
    return config;
  }
}
