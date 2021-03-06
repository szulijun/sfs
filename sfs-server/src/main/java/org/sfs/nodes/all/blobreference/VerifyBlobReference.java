/*
 * Copyright 2016 The Simple File Server Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sfs.nodes.all.blobreference;

import com.google.common.base.Optional;
import io.vertx.core.logging.Logger;
import org.sfs.Server;
import org.sfs.VertxContext;
import org.sfs.nodes.ClusterInfo;
import org.sfs.nodes.XNode;
import org.sfs.rx.Defer;
import org.sfs.vo.Segment;
import org.sfs.vo.TransientBlobReference;
import rx.Observable;
import rx.functions.Func1;

import java.util.Arrays;

import static com.google.common.base.Optional.absent;
import static io.vertx.core.logging.LoggerFactory.getLogger;
import static java.lang.Boolean.FALSE;
import static org.sfs.rx.Defer.just;
import static org.sfs.util.MessageDigestFactory.SHA512;

public class VerifyBlobReference implements Func1<TransientBlobReference, Observable<Boolean>> {

    private static final Logger LOGGER = getLogger(VerifyBlobReference.class);
    private final VertxContext<Server> vertxContext;

    public VerifyBlobReference(VertxContext<Server> vertxContext) {
        this.vertxContext = vertxContext;
    }

    @Override
    public Observable<Boolean> call(TransientBlobReference transientBlobReference) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("begin verify blob reference object=" + transientBlobReference.getSegment().getParent().getParent().getId() + ", version=" + transientBlobReference.getSegment().getParent().getId() + ", segment=" + transientBlobReference.getSegment().getId() + ", volume=" + transientBlobReference.getVolumeId() + ", position=" + transientBlobReference.getPosition());
        }
        ClusterInfo clusterInfo = vertxContext.verticle().getClusterInfo();
        Segment<? extends Segment> segment = transientBlobReference.getSegment();
        Optional<byte[]> writeSha512 = segment.getWriteSha512();
        Optional<Long> writeLength = segment.getWriteLength();
        if (!writeSha512.isPresent() && writeLength.isPresent()) {
            return just(false);
        }
        return just(transientBlobReference)
                .filter(transientBlobReference1 -> transientBlobReference1.getVolumeId().isPresent() && transientBlobReference1.getPosition().isPresent())
                .flatMap(transientBlobReference1 -> {
                    String volumeId = transientBlobReference.getVolumeId().get();
                    long position = transientBlobReference.getPosition().get();
                    Optional<XNode> oXNode = clusterInfo.getNodeForVolume(vertxContext, volumeId);
                    if (!oXNode.isPresent()) {
                        LOGGER.warn("No nodes contain volume " + volumeId);
                        return Defer.just(false);
                    } else {
                        XNode xNode = oXNode.get();
                        return xNode.checksum(volumeId, position, absent(), absent(), SHA512)
                                .map(digestBlobOptional -> {
                                    if (digestBlobOptional != null) {
                                        if (digestBlobOptional.isPresent()) {
                                            byte[] expectedSha512 = digestBlobOptional.get().getDigest(SHA512).get();
                                            Long expectedLength = digestBlobOptional.get().getLength();
                                            Optional<byte[]> oExistingSha512 = transientBlobReference1.getReadSha512();
                                            Optional<Long> oExistingLength = transientBlobReference1.getReadLength();
                                            boolean sha512Match = oExistingSha512.isPresent() ? Arrays.equals(expectedSha512, oExistingSha512.get()) : FALSE;
                                            boolean lengthMatch = oExistingLength.isPresent() ? oExistingLength.get().equals(expectedLength) : FALSE;
                                            return sha512Match && lengthMatch
                                                    && Arrays.equals(writeSha512.get(), expectedSha512)
                                                    && writeLength.get().equals(expectedLength);
                                        }
                                    }
                                    return false;
                                });
                    }
                })
                .onErrorResumeNext(throwable -> {
                    LOGGER.error("verify fail blob reference object=" + transientBlobReference.getSegment().getParent().getParent().getId() + ", version=" + transientBlobReference.getSegment().getParent().getId() + ", segment=" + transientBlobReference.getSegment().getId() + ", volume=" + transientBlobReference.getVolumeId() + ", position=" + transientBlobReference.getPosition(), throwable);
                    return Defer.just(false);
                })
                .singleOrDefault(false)
                .map(verified -> {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("end verify blob reference object=" + transientBlobReference.getSegment().getParent().getParent().getId() + ", version=" + transientBlobReference.getSegment().getParent().getId() + ", segment=" + transientBlobReference.getSegment().getId() + ", volume=" + transientBlobReference.getVolumeId() + ", position=" + transientBlobReference.getPosition() + ", verified=" + verified);
                    }
                    return verified;
                });
    }
}