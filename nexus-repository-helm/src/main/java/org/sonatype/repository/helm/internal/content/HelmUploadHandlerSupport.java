/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.repository.helm.internal.content;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.rest.UploadDefinitionExtension;
import org.sonatype.nexus.repository.security.ContentPermissionChecker;
import org.sonatype.nexus.repository.security.VariableResolverAdapter;
import org.sonatype.nexus.repository.storage.StorageFacet;
import org.sonatype.nexus.repository.storage.TempBlob;
import org.sonatype.nexus.repository.upload.ComponentUpload;
import org.sonatype.nexus.repository.upload.UploadDefinition;
import org.sonatype.nexus.repository.upload.UploadHandlerSupport;
import org.sonatype.nexus.repository.upload.UploadResponse;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.PartPayload;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.payloads.StreamPayload;
import org.sonatype.nexus.rest.ValidationErrorsException;
import org.sonatype.repository.helm.HelmAttributes;
import org.sonatype.repository.helm.internal.AssetKind;
import org.sonatype.repository.helm.internal.HelmFormat;
import org.sonatype.repository.helm.internal.util.HelmAttributeParser;

import org.apache.commons.lang3.StringUtils;

import static java.util.Collections.emptyMap;
import static org.sonatype.repository.helm.internal.HelmFormat.HASH_ALGORITHMS;
import static org.sonatype.repository.helm.internal.HelmFormat.NAME;

/**
 * Common base for helm upload handlers
 *
 * @since 3.next
 */
public abstract class HelmUploadHandlerSupport
    extends UploadHandlerSupport
{
  protected final HelmAttributeParser helmPackageParser;

  protected final ContentPermissionChecker contentPermissionChecker;

  protected final VariableResolverAdapter variableResolverAdapter;

  protected UploadDefinition definition;

  public HelmUploadHandlerSupport(
      final ContentPermissionChecker contentPermissionChecker,
      final HelmAttributeParser helmPackageParser,
      final VariableResolverAdapter variableResolverAdapter,
      final Set<UploadDefinitionExtension> uploadDefinitionExtensions)
  {
    super(uploadDefinitionExtensions);
    this.contentPermissionChecker = contentPermissionChecker;
    this.variableResolverAdapter = variableResolverAdapter;
    this.helmPackageParser = helmPackageParser;
  }

  @Override
  public UploadResponse handle(final Repository repository, final ComponentUpload upload) throws IOException {
    //Data holders for populating the UploadResponse
    List<PartPayload> pathToPayload = new ArrayList<>();

    upload.getAssetUploads().forEach(asset -> pathToPayload.add(asset.getPayload()));

    Map<String, Content> responseContents = getResponseContents(repository, pathToPayload);

    return new UploadResponse(responseContents.values(), new ArrayList<>(responseContents.keySet()));
  }

  protected abstract Map<String, Content> getResponseContents(
      final Repository repository,
      final List<PartPayload> pathToPayload)
      throws IOException;

  @Override
  public Content handle(
      final Repository repository,
      final File content,
      final String path)
      throws IOException
  {
    ensurePermitted(repository.getName(), HelmFormat.NAME, path, emptyMap());

    Path contentPath = content.toPath();
    return doPut(repository, content, path, contentPath);
  }

  protected Content doPut(final Repository repository, final File content, final String path, final Path contentPath)
      throws IOException
  {
    HelmContentFacet facet = repository.facet(HelmContentFacet.class);
    String fileName = Paths.get(path).getFileName().toString();
    AssetKind assetKind = AssetKind.getAssetKindByFileName(fileName);
    Payload streamPayload = new StreamPayload(() -> new FileInputStream(content), Files.size(contentPath),
        Files.probeContentType(contentPath));
    return facet.putIndex(path, (Content) streamPayload, assetKind);
  }

  protected void processPayload(
      final Repository repository,
      final HelmContentFacet facet,
      final StorageFacet storageFacet, final Map<String, Content> responseContents, final PartPayload payload)
      throws IOException
  {
    String fileName = payload.getName() != null ? payload.getName() : StringUtils.EMPTY;
    AssetKind assetKind = AssetKind.getAssetKindByFileName(fileName);

    if (assetKind != AssetKind.HELM_PROVENANCE && assetKind != AssetKind.HELM_PACKAGE) {
      throw new IllegalArgumentException("Unsupported extension. Extension must be .tgz or .tgz.prov");
    }

    try (TempBlob tempBlob = storageFacet.createTempBlob(payload, HASH_ALGORITHMS)) {
      HelmAttributes attributesFromInputStream = helmPackageParser.getAttributes(assetKind, tempBlob.get());
      String extension = assetKind.getExtension();
      String name = attributesFromInputStream.getName();
      String version = attributesFromInputStream.getVersion();

      if (StringUtils.isBlank(name)) {
        throw new ValidationErrorsException("Metadata is missing the name attribute");
      }

      if (StringUtils.isBlank(version)) {
        throw new ValidationErrorsException("Metadata is missing the version attribute");
      }

      String path = String.format("%s-%s%s", name, version, extension);

      ensurePermitted(repository.getName(), NAME, path, Collections.emptyMap());

      Content content = facet.putIndex(path, (Content) payload, assetKind);

      responseContents.put(path, content);
    }
  }

  @Override
  public UploadDefinition getDefinition() {
    if (definition == null) {
      definition = getDefinition(NAME, false);
    }
    return definition;
  }

  @Override
  public VariableResolverAdapter getVariableResolverAdapter() {
    return variableResolverAdapter;
  }

  @Override
  public ContentPermissionChecker contentPermissionChecker() {
    return contentPermissionChecker;
  }

  @Override
  public boolean supportsExportImport() {
    return true;
  }
}
