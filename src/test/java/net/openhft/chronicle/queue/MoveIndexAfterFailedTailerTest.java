package net.openhft.chronicle.queue;

import net.openhft.chronicle.core.OS;
import net.openhft.chronicle.wire.ReadMarshallable;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static java.lang.System.currentTimeMillis;
import static net.openhft.chronicle.queue.RollCycles.HOURLY;

public class MoveIndexAfterFailedTailerTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(MoveIndexAfterFailedTailerTest.class);

    @Test
    public void test() throws IOException {
        final ChronicleQueueBuilder myBuilder = ChronicleQueueBuilder.single(OS.TARGET + "/chronicle-" + System.nanoTime())
                .timeProvider(System::currentTimeMillis)
                .rollCycle(HOURLY);

        int messages = 10;
        try (final ChronicleQueue myWrite = myBuilder.build()) {
            write(myWrite, messages);
            System.out.println(myWrite.dump());
        }

        try (final ChronicleQueue myRead = myBuilder.build()) {
            read(myRead, messages);
        }
    }

    private void read(ChronicleQueue aChronicle, int expected) throws IOException {
        final ExcerptTailer myTailer = aChronicle.createTailer();
        final int myLast = HOURLY.toCycle(myTailer.toEnd().index());
        final int myFirst = HOURLY.toCycle(myTailer.toStart().index());
        int myCycle = myFirst - 1;
        long myIndex = HOURLY.toIndex(myCycle, 0);
        int count = 0;
        while (myCycle <= myLast) {
//            System.out.println(Long.toHexString(myIndex));
            if (myTailer.moveToIndex(myIndex)) {
                while (myTailer.readDocument(read())) {
                    count++;
                }
            }
            myIndex = HOURLY.toIndex(++myCycle, 0);
        }
        Assert.assertEquals(expected, count);
    }

    private ReadMarshallable read() {
        return aMarshallable -> {
            final byte[] myBytes = aMarshallable.read().bytes();
            if (myBytes != null) {
                LOGGER.info("Reading: {}", new String(myBytes, StandardCharsets.UTF_8));
            }
        };
    }

    private void write(ChronicleQueue aChronicle, int messages) throws IOException {
        final ExcerptAppender myAppender = aChronicle.acquireAppender();
        for (int myCount = 0; myCount < messages; myCount++) {
            myAppender.writeDocument(aMarshallable -> aMarshallable.write().bytes(Long.toString(currentTimeMillis()).getBytes(StandardCharsets.UTF_8)));
//            System.out.println(Long.toHexString(myAppender.lastIndexAppended()));
        }
    }
}