/**
 * Ariane Community Messaging
 * Application Message Worker Interface
 *
 * Copyright (C) 8/24/14 echinopsii
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

import java.util.Map;

/**
 * AppMsgWorker interface.
 * <p/>
 * Dedicated to define message treatment logic in the apply() method which will be called by the messaging tooling.
 * Any class implementing AppMsgWorker can be passed through following messaging tooling:
 * <p/>
 * - request tooling
 * @see net.echinopsii.ariane.community.messaging.api.MomRequestExecutor
 * <p/>
 * - service tooling
 * @see net.echinopsii.ariane.community.messaging.api.MomServiceFactory
 * <p/>
 * AppMsgWorker are MoM broker agnostic : the message definition for an AppMsgWorker is a simple Map<String, Object>.
 * @see net.echinopsii.ariane.community.messaging.api.MomMsgTranslator to know more on internal implementation.
 */
public interface AppMsgWorker {
    /**
     * apply business treatment to the message
     * @param message to be applied
     * @return answer msg
     */
    Map<String, Object> apply(Map<String, Object> message);
}