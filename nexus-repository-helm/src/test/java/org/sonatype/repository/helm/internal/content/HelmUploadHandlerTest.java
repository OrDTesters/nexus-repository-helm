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

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.sonatype.nexus.repository.rest.UploadDefinitionExtension;
import org.sonatype.nexus.repository.security.ContentPermissionChecker;
import org.sonatype.nexus.repository.security.VariableResolverAdapter;
import org.sonatype.nexus.repository.upload.AssetUpload;
import org.sonatype.nexus.repository.upload.ComponentUpload;
import org.sonatype.nexus.repository.upload.UploadHandler;
import org.sonatype.nexus.repository.upload.UploadResponse;
import org.sonatype.nexus.repository.view.PartPayload;
import org.sonatype.repository.helm.internal.HelmUploadHandlerTestSupport;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HelmUploadHandlerTest
    extends HelmUploadHandlerTestSupport
{
  @Mock
  HelmContentFacet helmFacet;

  @Override
  protected UploadHandler newHelmUploadHandler(
      final ContentPermissionChecker contentPermissionChecker,
      final VariableResolverAdapter variableResolverAdapter,
      final Set<UploadDefinitionExtension> uploadDefinitionExtensions)
  {
    return new HelmUploadHandler(contentPermissionChecker, variableResolverAdapter, uploadDefinitionExtensions);
  }

  @Before
  public void setup() {
    when(repository.facet(HelmContentFacet.class)).thenReturn(helmFacet);
  }

  @Test
  public void testHandle() throws IOException {
    ComponentUpload component = new ComponentUpload();

    component.getFields().put("directory", "org/apache/maven");

    AssetUpload asset = new AssetUpload();
    asset.getFields().put("filename", "foo.jar");
    asset.setPayload(jarPayload);
    component.getAssetUploads().add(asset);

    asset = new AssetUpload();
    asset.getFields().put("filename", "bar.jar");
    asset.setPayload(sourcesPayload);
    component.getAssetUploads().add(asset);

    when(content.getAttributes()).thenReturn(attributesMap);
    when(helmFacet.put(any(), any())).thenReturn(content);
    UploadResponse uploadResponse = underTest.handle(repository, component);
    assertThat(uploadResponse.getAssetPaths(), contains("/org/apache/maven/foo.jar", "/org/apache/maven/bar.jar"));

    ArgumentCaptor<String> pathCapture = ArgumentCaptor.forClass(String.class);
    verify(helmFacet, times(2)).put(pathCapture.capture(), any(PartPayload.class));

    List<String> paths = pathCapture.getAllValues();

    assertThat(paths, hasSize(2));

    String path = paths.get(0);
    assertNotNull(path);
    assertThat(path, is("/org/apache/maven/foo.jar"));

    path = paths.get(1);
    assertNotNull(path);
    assertThat(path, is("/org/apache/maven/bar.jar"));
  }

  @Override
  protected void testNormalizePath(final String directory, final String file, final String expectedPath)
      throws IOException
  {
    reset(helmFacet);
    ComponentUpload component = new ComponentUpload();

    component.getFields().put("directory", directory);

    AssetUpload asset = new AssetUpload();
    asset.getFields().put("filename", file);
    asset.setPayload(jarPayload);
    component.getAssetUploads().add(asset);

    when(content.getAttributes()).thenReturn(attributesMap);
    when(helmFacet.put(any(), any())).thenReturn(content);
    underTest.handle(repository, component);

    ArgumentCaptor<String> pathCapture = ArgumentCaptor.forClass(String.class);
    verify(helmFacet).put(pathCapture.capture(), any(PartPayload.class));

    String path = pathCapture.getValue();
    assertNotNull(path);
    assertThat(path, is(expectedPath));
  }

  @Override
  protected String path(final String path) {
    return "/" + path;
  }
}
