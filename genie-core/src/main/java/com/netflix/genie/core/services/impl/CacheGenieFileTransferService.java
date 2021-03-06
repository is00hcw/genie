/*
 * Copyright 2016 Netflix, Inc.
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *        http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.genie.core.services.impl;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.netflix.genie.common.exceptions.GenieException;
import com.netflix.genie.common.exceptions.GenieServerException;
import com.netflix.genie.core.services.FileTransfer;
import com.netflix.genie.core.services.FileTransferFactory;
import com.netflix.spectator.api.Registry;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotBlank;

import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

/**
 * Caches the downloaded file from the remote location.
 * Created by amajumdar on 7/22/16.
 */
@Slf4j
public class CacheGenieFileTransferService extends GenieFileTransferService {
    //File cache location
    private final String baseCacheLocation;
    //File transfer service to get/put files on a local system
    private final FileTransfer localFileTransfer;
    //File cache
    private final LoadingCache<String, File> fileCache = CacheBuilder.newBuilder()
            .recordStats()
            .build(
                    new CacheLoader<String, File>() {
                        public File load(
                                @NotNull
                                final String path) throws GenieException {
                            return loadFile(path);
                        }
                    });

    /**
     * Constructor.
     *
     * @param fileTransferFactory file transfer implementation factory
     * @param baseCacheLocation file cache location
     * @param localFileTransfer Local file transfer service
     * @param registry spectator registry
     * @throws GenieException If there is any problem
     */
    public CacheGenieFileTransferService(
            @NotNull final FileTransferFactory fileTransferFactory,
            @NotNull final String baseCacheLocation,
            @NotNull final FileTransfer localFileTransfer,
            @NotNull final Registry registry) throws GenieException {
        super(fileTransferFactory);
        this.baseCacheLocation = createDirectories(baseCacheLocation).toString();
        this.localFileTransfer = localFileTransfer;
        registry.gauge("genie.jobs.file.cache.hitRate", fileCache,
                (ToDoubleFunction<LoadingCache<String, File>>) value -> value.stats().hitRate());
        registry.gauge("genie.jobs.file.cache.missRate", fileCache,
                (ToDoubleFunction<LoadingCache<String, File>>) value -> value.stats().missRate());
        registry.gauge("genie.jobs.file.cache.loadExceptionRate", fileCache,
                (ToDoubleFunction<LoadingCache<String, File>>) value -> value.stats().loadExceptionRate());
    }

    /**
     * Get the file needed by Genie for job execution.
     *
     * @param srcRemotePath Path of the file in the remote location to be fetched
     * @param dstLocalPath  Local path where the file needs to be placed
     * @throws GenieException If there is any problem
     */
    public void getFile(
            @NotBlank(message = "Source file path cannot be empty.")
            final String srcRemotePath,
            @NotBlank(message = "Destination local path cannot be empty")
            final String dstLocalPath
    ) throws GenieException {
        log.debug("Called with src path {} and destination path {}", srcRemotePath, dstLocalPath);
        File cachedFile = fileCache.getIfPresent(srcRemotePath);
        try {
            if (cachedFile == null) {
                cachedFile = fileCache.get(srcRemotePath);
            } else {
                cachedFile = fileCache.get(srcRemotePath);
                // Before using the cached file check if the real file has been modified after we have cached
                final long lastModifiedTime = getFileTransfer(srcRemotePath).getLastModifiedTime(srcRemotePath);
                if (lastModifiedTime > cachedFile.lastModified()) {
                    synchronized (this) {
                        // Check the modification time again because threads that were waiting for a file might have
                        // been refreshed by a previous thread.
                        if (lastModifiedTime > cachedFile.lastModified()) {
                            fileCache.invalidate(srcRemotePath);
                            deleteFile(cachedFile);
                            cachedFile = fileCache.get(srcRemotePath);
                        }
                    }
                }
            }
        } catch (Exception e) {
            final String message = String.format("Failed getting the file %s", srcRemotePath);
            log.error(message);
            throw new GenieServerException(message, e);
        }
        localFileTransfer.getFile(cachedFile.getPath(), dstLocalPath);
    }

    protected void deleteFile(final File file) throws IOException {
        Files.deleteIfExists(file.toPath());
    }

    protected Path createDirectories(final String path) throws GenieException {
        Path result = null;
        try {
            final File pathFile = new File(new URI(path).getPath());
            result = pathFile.toPath();
            if (!Files.exists(result)) {
                Files.createDirectories(result);
            }
        } catch (Exception e) {
            throw new GenieServerException("Failed creating the cache location " + path, e);
        }
        return result;
    }

    /**
     * Loads the file given the path and stores it under the cache location with file name as UUID string created using
     * the path.
     * @param path Path of the file to be loaded
     * @return loaded file
     * @throws GenieException Exception if the file does not load
     */
    protected File loadFile(final String path) throws GenieException {
        final byte[] pathBytes = path.getBytes(Charset.forName("UTF-8"));
        final String pathUUID = UUID.nameUUIDFromBytes(pathBytes).toString();
        final String cacheFilePath = String.format("%s/%s", baseCacheLocation, pathUUID);
        final File cacheFile = new File(cacheFilePath);
        if (!cacheFile.exists()) {
            getFileTransfer(path).getFile(path, cacheFilePath);
        }
        return cacheFile;
    }
}
