package com.jenkov.nioserver;

/**
 * A shared buffer which can contain many messages inside. A message gets a section of the buffer to use. If the
 * message outgrows the section in size, the message requests a larger section and the message is copied to that
 * larger section. The smaller section is then freed again.
 *
 *
 * Created by jjenkov on 18-10-2015.
 */
public class MessageBuffer {

    public static int KB = 1024;
    public static int MB = 1024 * KB;

    private static final int CAPACITY_SMALL  =   4  * KB;
    private static final int CAPACITY_MEDIUM = 128  * KB;
    private static final int CAPACITY_LARGE  = 1024 * KB;

    //package scope (default) - so they can be accessed from unit tests.
    byte[]  smallMessageBuffer  = new byte[1024 *   4 * KB];   //1024 x   4KB messages =  4MB.
    byte[]  mediumMessageBuffer = new byte[128  * 128 * KB];   // 128 x 128KB messages = 16MB.
    byte[]  largeMessageBuffer  = new byte[16   *   1 * MB];   //  16 *   1MB messages = 16MB.

    QueueIntFlip smallMessageBufferFreeBlocks  = new QueueIntFlip(1024); // 1024 free sections
    QueueIntFlip mediumMessageBufferFreeBlocks = new QueueIntFlip(128);  // 128  free sections
    QueueIntFlip largeMessageBufferFreeBlocks  = new QueueIntFlip(16);   // 16   free sections

    //todo make all message buffer capacities and block sizes configurable
    //todo calculate free block queue sizes based on capacity and block size of buffers.

    public MessageBuffer() {
        //add all free sections to all free section queues.
        for(int i=0; i<smallMessageBuffer.length; i+= CAPACITY_SMALL){
            // each section has size 4* KB
            this.smallMessageBufferFreeBlocks.put(i);
        }
        for(int i=0; i<mediumMessageBuffer.length; i+= CAPACITY_MEDIUM){
            this.mediumMessageBufferFreeBlocks.put(i);
        }
        for(int i=0; i<largeMessageBuffer.length; i+= CAPACITY_LARGE){
            this.largeMessageBufferFreeBlocks.put(i);
        }
    }

    /**
     * create one new Message with shared array
     * @return
     */
    public Message getMessage() {
        int nextFreeSmallBlock = this.smallMessageBufferFreeBlocks.take();

        if(nextFreeSmallBlock == -1) return null;

        Message message = new Message(this);       //todo get from Message pool - caps memory usage.
        message.sharedArray = this.smallMessageBuffer;  // share the same messageBuffer with different message instance
        message.capacity    = CAPACITY_SMALL;
        message.offset      = nextFreeSmallBlock;  // 0, when get the first message, the 2th start index is CAPACITY_SMALL
        message.length      = 0;

        return message;
    }

    public boolean expandMessage(Message message){
        if(message.capacity == CAPACITY_SMALL){
            return moveMessage(message, this.smallMessageBufferFreeBlocks, this.mediumMessageBufferFreeBlocks, this.mediumMessageBuffer, CAPACITY_MEDIUM);
        } else if(message.capacity == CAPACITY_MEDIUM){
            return moveMessage(message, this.mediumMessageBufferFreeBlocks, this.largeMessageBufferFreeBlocks, this.largeMessageBuffer, CAPACITY_LARGE);
        } else {
            return false;
        }
    }

    private boolean moveMessage(Message message, QueueIntFlip srcBlockQueue, QueueIntFlip destBlockQueue, byte[] dest, int newCapacity) {
        //TODO: why use the queue to store the index ?
        int nextFreeBlock = destBlockQueue.take();
        if(nextFreeBlock == -1) return false;

        System.arraycopy(message.sharedArray, message.offset, dest, nextFreeBlock, message.length);
        // The space can be used when the array index is available in queue
        // Because it must take the started index first from the queue before using that section of the array
        srcBlockQueue.put(message.offset); //free smaller block after copy

        message.sharedArray = dest;
        message.offset      = nextFreeBlock;
        message.capacity    = newCapacity;
        return true;
    }





}
