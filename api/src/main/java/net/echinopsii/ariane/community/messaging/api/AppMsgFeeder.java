/**
 * Ariane Community Messaging
 * Application Message Feeder Interface
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
 * AppMsgFeeder interface.
 * <p/>
 * Dedicated to generate message to feed (in the apply method)
 */
public interface AppMsgFeeder {

    String MSG_FEED_NOW = "FEED_NOW";

    /**
     * @return the next message to feed
     */
    Map<String, Object> apply();

    /**
     * @return the interval between each message feed
     */
    int getInterval();
}