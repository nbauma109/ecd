/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.util;

import org.eclipse.core.runtime.IBundleGroup;
import org.eclipse.core.runtime.IBundleGroupProvider;
import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.p2.metadata.Version;

public class VersionUtil {

	public static Version getFeatureVersion(String featureId) {
		for (IBundleGroupProvider provider : Platform.getBundleGroupProviders()) {
			for (IBundleGroup feature : provider.getBundleGroups()) {
				if (feature.getIdentifier().equals(featureId)) {
                    return Version.create(feature.getVersion());
                }
			}
		}
		return null;
	}

	public static String getDecompilerVersion() {
		Version installVersion = VersionUtil.getFeatureVersion("org.sf.feeling.decompiler"); //$NON-NLS-1$
		if (installVersion != null) {
			return installVersion.toString();
		}
		return null;
	}
}
