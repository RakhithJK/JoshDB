package org.josh.JoshDB.FileTrie;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public class AtomicResizingLongArray
{

    //64*2/8 = 128/8 =
    private static final int ELEMENT_LENGTH = 8;

    //an array of byte[]s where masterList[i].length == ELEMENT_LENGTH << i
    private volatile byte[][] masterList;
    private static final AtomicReferenceFieldUpdater
    <
        AtomicResizingLongArray,
        byte[][]
    >
    masterListUpdater =
        AtomicReferenceFieldUpdater.newUpdater
        (
            AtomicResizingLongArray.class,
            byte[][].class,
            "masterList"
        );

    /**
     * Mostly for testing purposes, returns "current" deep length
     */
    public int currentLengthEstimate()
    {
        return masterListDeepLength(masterList == null ? 0 : masterList.length);
    }

    private int masterListDeepLength(int shallowLength)
    {
        int deepLength = 0;
        for (int i = 0; i < shallowLength; i++)
        {
            deepLength += ELEMENT_LENGTH << (i - 1);
        }

        return deepLength;
    }

    // I'm positive some fancy masky shifty stuff would be more efficient here,
    // but it's way too early to start optimizing shit like that
    private int shallowLengthRequiredForOffset(int offset)
    {
        int accepted = 1;
        for (int proposed = 1; masterListDeepLength(proposed) < offset; proposed++)
        {
            accepted = proposed;
        }

        return accepted;
    }

    private final AtomicInteger maxRequestedShallowLength;

    /**
     * Ensure that the deep length of masterList is
     * at least enough to satisfy a get at requiredOffset
     */
    void ensureMasterListLongEnough(int requiredOffset)
    {
        int requiredLength = shallowLengthRequiredForOffset(requiredOffset);
        int localMax = maxRequestedShallowLength.get();

        boolean didSucceed = false;
        //set localMax to the deep Array Length we'll have after resizing
        while (localMax < requiredLength)
        {
            didSucceed = maxRequestedShallowLength.compareAndSet(localMax, requiredLength);

            if (didSucceed)
            {
                break;
            }
            else
            {
                localMax = maxRequestedShallowLength.get();
            }
        }

        //spin until the array has been resized by whoever succeeded
        if (!didSucceed)
        {
            //noinspection StatementWithEmptyBody
            while (masterList.length < requiredLength);
            return;
        }

        //spin until masterList has localMax subarrays allocated
        //noinspection StatementWithEmptyBody
        while (masterList.length < localMax);


        //then add our chunks
        // to do that we want to atomically swap out the master
        // list in case any other requests have the same required
        // length
        byte[][] replacement = new byte[requiredLength][];
        byte[][] localMaster;
        do
        {
            localMaster = masterListUpdater.get(this);

            for (int i = 0; i < localMax; i++)
            {
                replacement[i] = localMaster[i];
            }

            for (int i = localMax; i < requiredLength; i++)
            {
                replacement[i] = new byte[ELEMENT_LENGTH << i];

                for (int j = 0; j < replacement[i].length; j += 8)
                {
                    ByteBuffer.wrap(replacement[i], j, 8).putLong(-1);
                }
            }
        }
        while
        (
            !masterListUpdater.compareAndSet(this, localMaster, replacement)
            &&
            localMaster.length < requiredLength
        );
    }

    public AtomicResizingLongArray()
    {
        masterList = new byte[1][];
        masterList[0] = new byte[ELEMENT_LENGTH];
        //invalid get returns -1
        ByteBuffer.wrap(masterList[0]).putLong(-1);
        maxRequestedShallowLength = new AtomicInteger(1);
    }

    public long get(int index)
    {
        return ByteBuffer.wrap(get(index * ELEMENT_LENGTH, ELEMENT_LENGTH)).getLong();
    }

    public void set(int index, long contents)
    {
        set(index * ELEMENT_LENGTH, ByteBuffer.allocate(8).putLong(contents).array());
    }

    public void set(int offset, byte[] contents)
    {
        ensureMasterListLongEnough(offset + contents.length);

        // todo I'm positive this could be made more efficient and simpler with
        // some fancy power of two math but this'll do for now
        int writeBufferPosition = 0;
        int overallOffsetOfCurrentArray = 0;
        for (int i = 0; i < masterList.length; i++)
        {
//            int lengthOfCurrentArray = ELEMENT_LENGTH << i;
            int lengthOfCurrentArray = masterList[i].length;
            int overallEndPositionOfCurrentArray =
                overallOffsetOfCurrentArray
                +
                lengthOfCurrentArray;

            //do we have anything to write in this subarray?
            if (overallEndPositionOfCurrentArray > offset && overallOffsetOfCurrentArray <= offset)
            {
                int startOffset;

                if (overallOffsetOfCurrentArray > offset)
                {
                    startOffset = 0;
                }
                else
                {
                    startOffset = offset - overallOffsetOfCurrentArray;
                }

                int bytesToWrite =
                    Math.min
                    (
                        lengthOfCurrentArray - startOffset,
                        contents.length - writeBufferPosition
                    );

                System.arraycopy
                (
                    contents,
                    writeBufferPosition,
                    masterList[i],
                    startOffset,
                    bytesToWrite
                );

                writeBufferPosition += bytesToWrite;
            }

            //are we done writing?
            if (overallEndPositionOfCurrentArray >= offset + contents.length)
            {
                break;
            }

            overallOffsetOfCurrentArray += lengthOfCurrentArray;
        }
    }

    public byte[] get(int offset, int length)
    {
        byte[] retVal = new byte[length];

        for (int i = 0; i < retVal.length; i += 8)
        {
            ByteBuffer.wrap(retVal, i, 8).putLong(-1);
        }

        int readBufferPosition = 0;
        int overallOffsetOfCurrentArray = 0;
        for (int i = 0; i < masterList.length; i++)
        {
            int lengthOfCurrentArray = ELEMENT_LENGTH << i;
            int overallEndPositionOfCurrentArray =
                overallOffsetOfCurrentArray
                +
                lengthOfCurrentArray
                ;

            //do we have anything to read in this subarray?
            if (overallEndPositionOfCurrentArray > offset)
            {
                int startOffset;

                if (overallOffsetOfCurrentArray > offset)
                {
                    startOffset = 0;
                }
                else
                {
                    startOffset = offset - overallOffsetOfCurrentArray;
                }

                int bytesToRead =
                    Math.min
                    (
                        lengthOfCurrentArray-startOffset,
                        length - readBufferPosition
                    );

                System.arraycopy
                (
                    masterList[i],
                    startOffset,
                    retVal,
                    readBufferPosition,
                    bytesToRead
                );

                readBufferPosition += bytesToRead;
            }

            //are we done reading?
            if (overallEndPositionOfCurrentArray >= offset + length)
            {
                break;
            }

            overallOffsetOfCurrentArray += lengthOfCurrentArray;
        }

        return retVal;
    }
}