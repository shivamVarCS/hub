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
import com.amazonaws.services.s3.model.S3ObjectSummary;
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
import javax.activation.FileTypeMap;
import javax.activation.MimetypesFileTypeMap;
import javax.annotation.Nullable;

/**
 * Publish packages to S3.
 */
public class S3Publisher implements Publisher {
  private static final Logger LOG = LoggerFactory.getLogger(S3Publisher.class);
  private static final FileTypeMap fileTypeMap = MimetypesFileTypeMap.getDefaultFileTypeMap();
  private static final String version = "v1";
  private final AmazonS3Client client;
  private final String bucket;
  private final boolean forcePush;

  public S3Publisher(File cfgFile, String bucket, boolean forcePush) throws IOException {
    this.bucket = bucket;
    this.forcePush = forcePush;

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
      clientConf.setSocketTimeout(Integer.parseInt(conf.get("socket_timeout")) * 1000);
    }
    this.client = new AmazonS3Client(new BasicAWSCredentials(accessKey, secretKey), clientConf);
  }

  @Override
  public void publishPackage(Package pkg) throws Exception {
    String keyPrefix = String.format("%s/packages/%s/%s/", version, pkg.getName(), pkg.getVersion());

    putFilesIfChanged(keyPrefix, pkg.getIcon());
    putFilesIfChanged(keyPrefix, pkg.getLicense());
    putFilesIfChanged(keyPrefix, pkg.getSpec().getFile(), pkg.getSpec().getSignature());
    if (pkg.getArchive() != null) {
      putFilesIfChanged(keyPrefix, pkg.getArchive().getFile(), pkg.getArchive().getSignature());
    }
    for (SignedFile file : pkg.getFiles()) {
      putFilesIfChanged(keyPrefix, file.getFile(), file.getSignature());
    }
    for (S3ObjectSummary objectSummary : client.listObjects(bucket, keyPrefix).getObjectSummaries()) {
      String objectKey = objectSummary.getKey();
      String name = objectKey.substring(keyPrefix.length());
      if (!pkg.getFileNames().contains(name)) {
        LOG.info("Deleting object {} from s3 since it does not exist in the package anymore.", objectKey);
        client.deleteObject(bucket, objectKey);
      }
    }
  }

  @Override
  public void publishCatalog(File catalog) throws Exception {
    putFilesIfChanged(version + "/", catalog);
  }

  // if the specified file has changed, put it plus all extra files on s3.
  private void putFilesIfChanged(String keyPrefix, @Nullable File file, File... extraFiles) throws IOException {
    if (file != null && shouldPush(keyPrefix, file)) {
      putFile(keyPrefix, file);
      for (File extraFile : extraFiles) {
        if (extraFile != null) {
          putFile(keyPrefix, extraFile);
        }
      }
    }
  }

  // check if the file on s3 has a different md5 or the file length.
  private boolean shouldPush(String keyPrefix, File file) throws IOException {
    if (forcePush) {
      return true;
    }
    String key = keyPrefix + file.getName();
    if (client.doesObjectExist(bucket, key)) {
      ObjectMetadata existingMeta = client.getObjectMetadata(bucket, key);
      long fileLength = file.length();
      String md5Hex = BaseEncoding.base16().encode(Files.hash(file, Hashing.md5()).asBytes());
      if (existingMeta != null &&
        existingMeta.getContentLength() == fileLength &&
        existingMeta.getETag() != null && existingMeta.getETag().equalsIgnoreCase(md5Hex)) {
        LOG.info("{} has not changed, skipping upload to S3.", file);
        return false;
      }
    }
    return true;
  }

  private void putFile(String keyPrefix, File file) throws IOException {
    String ext = Files.getFileExtension(file.getName());
    String contentType;
    switch (ext) {
      case "json":
        contentType = MediaType.JSON_UTF_8.withoutParameters().toString();
        break;
      case "txt":
        contentType = MediaType.PLAIN_TEXT_UTF_8.withoutParameters().toString();
        break;
      case "asc":
        contentType = MediaType.PLAIN_TEXT_UTF_8.withoutParameters().toString();
        break;
      default:
        contentType = fileTypeMap.getContentType(file);
    }
    ObjectMetadata newMeta = new ObjectMetadata();
    newMeta.setContentType(contentType);
    String key = keyPrefix + file.getName();
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
