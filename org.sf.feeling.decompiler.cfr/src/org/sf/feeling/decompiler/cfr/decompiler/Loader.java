package org.sf.feeling.decompiler.cfr.decompiler;

import java.io.IOException;

public interface Loader {
	boolean canLoad(String internalName);

	byte[] load(String internalName) throws IOException;
}
