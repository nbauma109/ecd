/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.finder;

import java.util.Objects;

public class GAV {

    private String groupId;
    private String artifactId;
    private String version;
    private String artifactLink;

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String a) {
        this.artifactId = a;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getArtifactLink() {
        return artifactLink;
    }

    public void setArtifactLink(String artifactLink) {
        this.artifactLink = artifactLink;
    }

    public boolean isValid() {
        return groupId != null && artifactId != null && version != null;
    }

    @Override
    public int hashCode() {
        return Objects.hash(artifactId, artifactLink, groupId, version);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        GAV other = (GAV) obj;
        return Objects.equals(artifactId, other.artifactId) && Objects.equals(artifactLink, other.artifactLink)
                && Objects.equals(groupId, other.groupId) && Objects.equals(version, other.version);
    }

    @Override
    public String toString() {
        return "GAV [groupId=" + groupId + ", artifactId=" + artifactId + ", version=" + version + ", artifactLink="
                + artifactLink + "]";
    }

}
