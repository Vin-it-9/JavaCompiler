package org.compiler;

import jakarta.enterprise.context.ApplicationScoped;

import java.lang.ref.SoftReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class CacheManager {
    
    private static final int MAX_CACHE_ENTRIES = 100;
    
    private final Map<String, SoftReference<byte[]>> bytecodeCache = 
        new LinkedHashMap<>(MAX_CACHE_ENTRIES + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, SoftReference<byte[]>> eldest) {
                return size() > MAX_CACHE_ENTRIES;
            }
        };

    public synchronized Optional<byte[]> get(String codeHash) {
        SoftReference<byte[]> ref = bytecodeCache.get(codeHash);
        if (ref != null) {
            byte[] bytecode = ref.get();
            if (bytecode != null) {
                return Optional.of(bytecode);
            }
            bytecodeCache.remove(codeHash);
        }
        return Optional.empty();
    }

    public synchronized void put(String codeHash, byte[] bytecode) {
        bytecodeCache.put(codeHash, new SoftReference<>(bytecode));
    }

    public synchronized int size() {
        return bytecodeCache.size();
    }

    public synchronized void clear() {
        bytecodeCache.clear();
    }
}
