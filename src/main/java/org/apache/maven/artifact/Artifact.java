package org.apache.maven.artifact;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.maven.artifact.metadata.ArtifactMetadata;

import java.io.File;
import java.util.List;

/**
 * Description of an artifact.
 */
public interface Artifact
{
    // TODO: into scope handler
    String SCOPE_COMPILE = "compile";

    String SCOPE_TEST = "test";

    String SCOPE_RUNTIME = "runtime";

    String getGroupId();

    String getArtifactId();

    String getVersion();

    String getScope();

    String getType();

    String getClassifier();

    // only providing this since classifier is *very* optional...
    boolean hasClassifier();

    // ----------------------------------------------------------------------

    void setPath( String path );

    String getPath();

    File getFile();

    boolean exists();

    // ----------------------------------------------------------------------

    File getChecksumFile();

    // ----------------------------------------------------------------------

    String getId();

    String getConflictId();

    void addMetadata( ArtifactMetadata metadata );

    List getMetadataList();
}