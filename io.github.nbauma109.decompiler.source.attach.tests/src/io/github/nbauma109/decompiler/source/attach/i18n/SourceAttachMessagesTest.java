package io.github.nbauma109.decompiler.source.attach.i18n;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SourceAttachMessagesTest {

    @Test
    public void getStringReturnsKnownValueForAttachSourceActionName() {
        assertEquals("Attach Library Source", Messages.getString("AttachSourceAction.Action.Name")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void getStringReturnsKnownValueForAttachSourceHandlerJobName() {
        assertEquals("Attaching source to library...", //$NON-NLS-1$
                Messages.getString("AttachSourceHandler.Job.Name")); //$NON-NLS-1$
    }

    @Test
    public void getStringReturnsKnownValueForJavaSourceAttacherHandlerJobName() {
        assertEquals("Attaching source to library...", //$NON-NLS-1$
                Messages.getString("JavaSourceAttacherHandler.Job.Name")); //$NON-NLS-1$
    }

    @Test
    public void getStringWrapsMissingKeyWithExclamationMarks() {
        assertEquals("!no.such.key!", Messages.getString("no.such.key")); //$NON-NLS-1$ //$NON-NLS-2$
    }
}
