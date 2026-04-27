package io.papermc.paper.configuration;

import io.papermc.paper.configuration.type.Duration;
import java.util.List;

/**
 * Teji-Paper Configuration
 * Added by vortexx.0983@gmail.com
 */
public class TejiPaperConfiguration extends ConfigurationPart {
    public Physics physics = new Physics();
    public ChunkSending chunkSending = new ChunkSending();
    public Network network = new Network();

    public class Physics extends ConfigurationPart {
        public boolean asyncPhysicsEnabled = false;
        public int physicsThreadPoolSize = 4;
        public List<String> additionalAsyncBlocks = List.of();
    }

    public class ChunkSending extends ConfigurationPart {
        public boolean asyncEnabled = false;
        public int serializationThreadPoolSize = 4;
        public boolean lazyScheduling = true;
    }

    public class Network extends ConfigurationPart {
        public boolean asyncCompressionEnabled = false;
        public int compressionThreads = 4;
        public boolean useLibdeflate = true;
        public boolean packetBatchingEnabled = false;
        public int batchingMaxDelayMs = 1;
        public int batchingMaxPackets = 64;
    }
}