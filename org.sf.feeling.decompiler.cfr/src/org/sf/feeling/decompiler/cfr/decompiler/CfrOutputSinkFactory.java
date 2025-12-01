package org.sf.feeling.decompiler.cfr.decompiler;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.api.SinkReturns.LineNumberMapping;

public final class CfrOutputSinkFactory implements OutputSinkFactory {
    private final StringBuilder sb;
    private final Map<Integer, Integer> lineMapping;

    public CfrOutputSinkFactory(StringBuilder sb, Map<Integer, Integer> lineMapping) {
        this.sb = sb;
        this.lineMapping = lineMapping;
    }

    @Override
    public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> collection) {
        return Arrays.asList(SinkClass.STRING, SinkClass.DECOMPILED, SinkClass.DECOMPILED_MULTIVER,
                SinkClass.EXCEPTION_MESSAGE, SinkClass.LINE_NUMBER_MAPPING);
    }

    @Override
    public <T> Sink<T> getSink(final SinkType sinkType, final SinkClass sinkClass) {
        return sinkable -> {
            if (sinkType == SinkType.PROGRESS) {
                return;
            }
            if (sinkType == SinkType.LINENUMBER) {
                LineNumberMapping mapping = (LineNumberMapping) sinkable;
                Map<Integer, Integer> classFileMappings = mapping.getClassFileMappings();
                Map<Integer, Integer> mappings = mapping.getMappings();
                if (classFileMappings != null && mappings != null) {
                    for (Entry<Integer, Integer> entry : mappings.entrySet()) {
                        Integer srcLineNumber = classFileMappings.get(entry.getKey());
                        lineMapping.put(entry.getValue(), srcLineNumber);
                    }
                }
                return;
            }
            sb.append(sinkable);
        };
    }
}