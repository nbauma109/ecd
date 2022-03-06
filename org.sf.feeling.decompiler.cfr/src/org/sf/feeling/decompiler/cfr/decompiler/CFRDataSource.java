package org.sf.feeling.decompiler.cfr.decompiler;

import java.io.IOException;
import java.util.Collection;

import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.sf.feeling.decompiler.util.ClassUtil;

public class CFRDataSource implements ClassFileSource {
	private Loader loader;
	private byte[] data;
	private String name;

	public CFRDataSource(Loader loader, byte[] data, String name) {
		this.loader = loader;
		this.data = data;
		this.name = name;
	}

	@Override
	public void informAnalysisRelativePathDetail(String usePath, String classFilePath) {
	}

	@Override
	public Collection<String> addJar(String jarPath) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getPossiblyRenamedPath(String s) {
		return s;
	}

	@Override
	public Pair<byte[], String> getClassFileContent(String s) throws IOException {
		if (s.equals(name)) {
			return Pair.make(data, name);
		}
		String internalName = ClassUtil.getInternalName(s);
		return Pair.make(loader.load(internalName), internalName);
	}
}