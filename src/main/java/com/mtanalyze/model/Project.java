/*
 * Copyright 2026 Centerscout GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mtanalyze.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The central domain object.  Holds all loaded SWIFT messages for the current working session.
 */
public class Project {

    private final List<SwiftMessage> messages = new ArrayList<>();

    public List<SwiftMessage> messages()           { return Collections.unmodifiableList(messages); }
    public void addMessage(SwiftMessage m)         { messages.add(m); }
    public void removeMessage(int index)           { messages.remove(index); }
    public void clear()                            { messages.clear(); }
}