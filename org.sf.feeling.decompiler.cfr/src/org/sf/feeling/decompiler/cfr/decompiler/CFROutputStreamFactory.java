package org.sf.feeling.decompiler.cfr.decompiler;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.benf.cfr.reader.api.OutputSinkFactory;

public class CFROutputStreamFactory implements OutputSinkFactory {
	private String generatedSource;

	@Override
	public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> collection) {
		return Collections.singletonList(SinkClass.STRING);
	}

	@Override
	public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
		return a -> generatedSource = (String) a;
	}

	public String getGeneratedSource() {
		return generatedSource;
	}
}