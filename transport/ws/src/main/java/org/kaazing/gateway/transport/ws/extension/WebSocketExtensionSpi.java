/*
 * Copyright 2014, Kaazing Corporation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kaazing.gateway.transport.ws.extension;

import java.util.List;

import org.apache.mina.core.filterchain.IoFilter;

/**
 * {@link WebSocketExtensionSpi} is part of <i>Service Provider Interface</i> <em>(SPI)</em> for extension developers.
 * When an enabled extension is successfully negotiated, an instance of this class is created using the corresponding
 * {@link WebSocketExtensionFactorySpi} that is registered through META-INF/services. This class can supply
 * a filter that can view and manipulate WebSocket traffic.
 */
public abstract class WebSocketExtensionSpi {

    /**
     * This method is called when the extension is successfully negotiated.
     * @returns The extension header (token and optional parameters) that should be included in the WebSocket handshake
     *          response for this extension
     */
    public abstract ExtensionHeader getExtensionHeader();

    /**
     * This method allows extensions to provide a filter that will be added to the filter chain after the WebSocket codec 
     * filter and so can be used to see and modify WebSocket frames going to and from the client. Filters are added
     * in the order that the extensions were specified in the HTTP request, so the first extension is closest to the network
     * (i.e. gets to see and possibly modify frames read from the client before other extensions, and  gets the final say for
     * Frames being written to the client).
     * @param extension    Details of the negotiated extension and parameters
     * @return A filter which is to be added to the filter chain, or null if none is to be added
     */
    public IoFilter getFilter() {
        return null;
    };
    
}