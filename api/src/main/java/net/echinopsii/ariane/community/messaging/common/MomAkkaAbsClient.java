/**
 * Messaging - Common Implementation
 * Client abstract implementation
 * Copyright (C) 4/30/16 echinopsii
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

package net.echinopsii.ariane.community.messaging.common;

import akka.actor.ActorSystem;
import net.echinopsii.ariane.community.messaging.api.MomClient;
import net.echinopsii.ariane.community.messaging.api.MomRequestExecutor;
import net.echinopsii.ariane.community.messaging.api.MomServiceFactory;

import java.util.*;

public abstract class MomAkkaAbsClient implements MomClient {

    private ActorSystem       system     = null;
    private String            clientID   = null;

    private MomServiceFactory serviceFactory ;
    private List<MomRequestExecutor> requestExecutors = new ArrayList<MomRequestExecutor>();

    @Override
    public void init(Properties properties) throws Exception {
        Dictionary props = new Properties();
        for(Object key : properties.keySet())
            props.put(key, properties.get(key));
        init(props);
    }

    public ActorSystem getActorSystem() {
        return system;
    }

    public void setActorSystem(ActorSystem sys) {
        this.system = sys;
    }


    @Override
    public String getClientID() {
        return clientID;
    }

    public void setClientID(String id) {
        this.clientID = id;
    }

    @Override
    public MomServiceFactory getServiceFactory() {
        return serviceFactory;
    }

    public void setServiceFactory(MomServiceFactory serviceFactory) {
        this.serviceFactory = serviceFactory;
    }

    public List<MomRequestExecutor> getRequestExecutors() {
        return requestExecutors;
    }
}