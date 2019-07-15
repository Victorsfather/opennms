/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2019-2019 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2019 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.graph.rest.impl;

import java.util.List;
import java.util.Objects;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.opennms.netmgt.graph.api.GraphContainer;
import org.opennms.netmgt.graph.api.info.GraphContainerInfo;
import org.opennms.netmgt.graph.api.service.GraphService;
import org.opennms.netmgt.graph.rest.api.GraphRestService;
import org.opennms.netmgt.graph.rest.impl.renderer.JsonGraphRenderer;

public class GraphRestServiceImpl implements GraphRestService {

    private GraphService graphService;

    public GraphRestServiceImpl(GraphService graphService) {
        this.graphService = Objects.requireNonNull(graphService);
    }

    @Override
    public Response listContainerInfo() {
        final List<GraphContainerInfo> graphContainerInfos = graphService.getGraphContainerInfos();
        if (graphContainerInfos.isEmpty()) {
            return Response.noContent().build();
        }
        final String rendered = render(graphContainerInfos);
        return Response.ok(rendered).type(MediaType.APPLICATION_JSON_TYPE).build();
    }

    @Override
    public Response getContainer(String containerId) {
        final GraphContainer container = graphService.getGraphContainer(containerId);
        if (container == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        final String rendered = render(container);
        return Response.ok(rendered).type(MediaType.APPLICATION_JSON_TYPE).build();
    }

    private static String render(List<GraphContainerInfo> infos) {
        return new JsonGraphRenderer().render(infos);
    }

    private static String render(GraphContainer graphContainer) {
        return new JsonGraphRenderer().render(graphContainer);
    }
}