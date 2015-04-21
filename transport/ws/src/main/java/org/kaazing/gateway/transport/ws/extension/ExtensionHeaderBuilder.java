/**
 * Copyright (c) 2007-2014 Kaazing Corporation. All rights reserved.
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.kaazing.gateway.transport.ws.extension;

import static org.kaazing.gateway.transport.ws.WsMessage.Kind.TEXT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.mina.core.filterchain.IoFilterChain;
import org.kaazing.gateway.resource.address.ResourceAddress;
import org.kaazing.gateway.resource.address.ws.WsResourceAddress;
import org.kaazing.gateway.transport.ws.WsMessage;
import org.kaazing.gateway.transport.ws.WsMessage.Kind;
import org.kaazing.gateway.transport.ws.bridge.extensions.WsExtensions;
import org.kaazing.gateway.transport.ws.bridge.extensions.idletimeout.IdleTimeoutExtension;
import org.kaazing.gateway.transport.ws.bridge.extensions.pingpong.PingPongExtension;
import org.kaazing.mina.core.buffer.IoBufferEx;
import org.kaazing.mina.core.session.IoSessionEx;

/**
 * This class assists in parsing a WebSocket xtensions HTTP header which has the following syntax (a comma-separated list
 * Of extensions with optional parameters):
 * <pre>
 *     Sec-WebSocket-Extensions = extension-list
 *       extension-list = 1#extension
 *        extension = extension-token *( ";" extension-param )
 *        extension-token = registered-token
 *        registered-token = token
 *        extension-param = token [ "=" (token | quoted-string) ]
 *            ;When using the quoted-string syntax variant, the value
 *            ;after quoted-string unescaping MUST conform to the
 *            ;'token' ABNF.
 * </pre>
 */
public class ExtensionHeaderBuilder implements ExtensionHeader {
    
    private String extensionToken;
    private Map<String, ExtensionParameter> parametersByName = new LinkedHashMap<>();

    /**
     * @param extension  One of the comma-separated list of extensions from a WebSocket extensions HTTP header
     */
    public ExtensionHeaderBuilder(String extension) {
        if (extension == null) {
            throw new NullPointerException("extensionToken");
        }

        // Look for any parameters in the given token
        int idx = extension.indexOf(';');
        if (idx == -1) {
            this.extensionToken = extension;

        } else {
            String[] elts = extension.split(";");
            this.extensionToken = elts[0].trim();

            for (int i = 1; i < elts.length; i++) {
                String key = null;
                String value = null;

                idx = elts[i].indexOf('=');
                if (idx == -1) {
                    key = elts[i].trim();

                } else {
                    key = elts[i].substring(0, idx).trim();
                    value = elts[i].substring(idx+1).trim();
                }
                
                appendParameter(key, value);
            }
        }
    }

    public ExtensionHeaderBuilder(ExtensionHeader extension) {
        this.extensionToken = extension.getExtensionToken();
        List<ExtensionParameter> parameters = extension.getParameters();
        for ( ExtensionParameter p: parameters) {
            this.parametersByName.put(p.getName(), p);
        }
    }

    public String getExtensionToken() {
        return extensionToken;
    }

    public List<ExtensionParameter> getParameters() {
        return Collections.unmodifiableList(new ArrayList<>(parametersByName.values()));
    }

    @Override
    public boolean hasParameters() {
        return !parametersByName.isEmpty();
    }

    @Override
    public WsExtensionValidation checkValidity() {
        // By default, all extensions are valid
        return new WsExtensionValidation();
    }

    public ExtensionHeader toExtensionHeader() {
        return this;
    }

    public ExtensionHeaderBuilder setExtensionToken(String token) {
        this.extensionToken = token;
        return this;
    }

    public ExtensionHeaderBuilder append(ExtensionParameter parameter) {
        if ( !parametersByName.containsKey(parameter.getName()) ) {
            parametersByName.put(parameter.getName(), parameter);
        }
        return this;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder(extensionToken);
        for (ExtensionParameter wsExtensionParameter: parametersByName.values()) {
            b.append(';').append(' ').append(wsExtensionParameter);
        }
        return b.toString();
    }

    // Default equality is by extension token, ignoring parameters.

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof ExtensionHeaderBuilder)) return false;

        ExtensionHeaderBuilder that = (ExtensionHeaderBuilder) o;

        return !(extensionToken != null ? !extensionToken.equals(that.extensionToken) : that.extensionToken != null);

    }

    @Override
    public int hashCode() {
        return extensionToken != null ? extensionToken.hashCode() : 0;
    }

    public void appendParameter(String parameterContents) {
       append(new ExtensionParameterBuilder(parameterContents));
    }

    public void appendParameter(String parameterName, String parameterValue) {
        append(new ExtensionParameterBuilder(parameterName, parameterValue));
    }

    public static ExtensionHeader create(ResourceAddress address, ExtensionHeader extension) {
        ExtensionHeader ext;
    
        if (extension.getExtensionToken().equals(WsExtensions.IDLE_TIMEOUT)) {
            IdleTimeoutExtension idleTimeoutExt = new IdleTimeoutExtension(extension,
                    address.getOption(WsResourceAddress.INACTIVITY_TIMEOUT));
            ext = idleTimeoutExt;
    
        } else if (extension.getExtensionToken().equals(WsExtensions.PING_PONG)) {
            PingPongExtension typedExtension = new PingPongExtension(extension);
            ext = typedExtension;
        
        } else {
            ext = extension;
        }
    
        return ext;
    }

    @Override
    public boolean canDecode(EndpointKind endpointKind, Kind messageKind) {
        return false;
    }

    @Override
    public boolean canEncode(EndpointKind endpointKind, Kind messageKind) {
        return false;
    }

    @Override
    public WsMessage decode(IoBufferEx payload) {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] encode(WsMessage message) {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] getControlBytes() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Kind getEncodedKind(WsMessage message) {
        return TEXT;
    }
    
    @Override
    public String getOrdering() {
        return getExtensionToken();
    }

    @Override
    public void handleMessage(IoSessionEx session, WsMessage message) {
    }

    @Override
    public void removeBridgeFilters(IoFilterChain filterChain) {
    }

    @Override
    public void updateBridgeFilters(IoFilterChain filterChain) {
    }
    
}