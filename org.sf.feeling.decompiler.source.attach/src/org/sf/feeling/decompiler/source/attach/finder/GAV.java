/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.source.attach.finder;

import java.util.Objects;

public class GAV {

	private String g;
	private String a;
	private String v;
	private String artifactLink;

	public String getG() {
		return g;
	}

	public void setGroup(String g) {
		this.g = g;
	}

	public String getA() {
		return a;
	}

	public void setArtifact(String a) {
		this.a = a;
	}

	public String getV() {
		return v;
	}

	public void setVersion(String v) {
		this.v = v;
	}

	public String getArtifactLink() {
		return artifactLink;
	}

	public void setArtifactLink(String artifactLink) {
		this.artifactLink = artifactLink;
	}

	@Override
	public int hashCode() {
		return Objects.hash(a, artifactLink, g, v);
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
		return Objects.equals(a, other.a) && Objects.equals(artifactLink, other.artifactLink)
				&& Objects.equals(g, other.g) && Objects.equals(v, other.v);
	}

	@Override
	public String toString() {
		return "GAV [g=" + g + ", a=" + a + ", v=" + v + ", artifactLink=" + artifactLink + "]";
	}

}
