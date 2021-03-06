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

package org.sfs.nodes.data;

import com.google.common.base.Optional;
import io.vertx.core.http.HttpServerResponse;
import org.sfs.SfsRequest;
import org.sfs.filesystem.volume.HeaderBlob;
import org.sfs.rx.Holder2;
import rx.functions.Func1;

import static java.lang.String.valueOf;
import static org.sfs.util.SfsHttpHeaders.X_CONTENT_LENGTH;
import static org.sfs.util.SfsHttpHeaders.X_CONTENT_POSITION;
import static org.sfs.util.SfsHttpHeaders.X_CONTENT_VOLUME;
import static org.sfs.util.SfsHttpHeaders.X_CONTENT_VOLUME_PRIMARY;
import static org.sfs.util.SfsHttpHeaders.X_CONTENT_VOLUME_REPLICA;

public class WriteHeaderBlobAsHttpResponseHeaders<A extends HeaderBlob> implements Func1<Holder2<SfsRequest, Optional<A>>, Holder2<SfsRequest, Optional<A>>> {


    @Override
    public Holder2<SfsRequest, Optional<A>> call(Holder2<SfsRequest, Optional<A>> input) {
        HttpServerResponse httpServerResponse = input.value0().response();
        Optional<A> oBlob = input.value1();
        if (oBlob.isPresent()) {
            A blob = oBlob.get();
            long length = blob.getLength();
            long position = blob.getPosition();
            String volume = blob.getVolume();

            httpServerResponse
                    .putHeader(X_CONTENT_LENGTH, valueOf(length))
                    .putHeader(X_CONTENT_VOLUME, valueOf(volume))
                    .putHeader(X_CONTENT_POSITION, valueOf(position));
        }

        return input;
    }
}
