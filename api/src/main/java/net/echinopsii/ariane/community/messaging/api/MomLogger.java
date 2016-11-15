/**
 * Ariane Community Messaging
 * Mom Logger Interface
 *
 * Copyright (C) 11/13/16 echinopsii
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.echinopsii.ariane.community.messaging.api;

import org.slf4j.Logger;
import org.slf4j.spi.LocationAwareLogger;

import java.io.Serializable;
import java.util.Map;

public interface MomLogger extends Serializable, Logger, LocationAwareLogger {
    MomLogger setTraceLevel(boolean isTraceLevelEnabled);

    void traceMessage(String opsName, Map<String, Object> message);
    void debugMessage(String opsName, Map<String, Object> message);
    void infoMessage(String opsName, Map<String, Object> message);
    void warningMessage(String opsName, Map<String, Object> message);
    void errorMessage(String opsName, Map<String, Object> message);
}
