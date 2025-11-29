package org.sf.feeling.decompiler.jd.decompiler;

import static org.junit.Assert.assertThrows;

import java.io.File;

import org.junit.Test;

public class JDCoreSourceMapperTest {

	@Test
	public void testDecompile() {
		JDCoreSourceMapper sourceMapper = new JDCoreSourceMapper();
		File file = new File("target/test/Test.class");
		assertThrows(UnsupportedOperationException.class, () -> sourceMapper.decompile("JD", file));
	}
}
